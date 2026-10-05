package com.safenest.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Inspects only answer records reachable from the requested name. This is a DNS
 * alias policy aid, not DNSSEC validation or a TLS/HTTPS content inspector.
 *
 * CNAME follows RFC 1035; DNAME suffix substitution follows RFC 6672. SVCB/HTTPS
 * follows RFC 9460: AliasMode overrides ServiceMode, targets are uncompressed,
 * root has mode-specific semantics, and service bindings affect only their own
 * query type. Authority/additional records never introduce policy targets.
 */
public final class DnsAliasInspector {
    private static final int MAX_DEPTH = 64;
    private static final int MAX_STATES = 256;
    private static final Result INVALID = new Result(false, Collections.emptyList());

    private DnsAliasInspector() {}

    public static final class Result {
        public final boolean valid;
        public final List<String> targets;
        private Result(boolean valid, List<String> targets) {
            this.valid = valid;
            this.targets = Collections.unmodifiableList(new ArrayList<>(targets));
        }
    }

    /** Invalid/looping/over-budget reachable chains are distinguishable from no aliases. */
    public static Result inspect(DnsPacketCodec.Query query, byte[] reply) {
        if (query == null || !DnsPacketCodec.matchesResponse(query, reply)) return INVALID;
        // A truncated message is incomplete: let the caller retry it over upstream TCP.
        if (DnsPacketCodec.isTruncated(reply)) return INVALID;
        Name question = name(reply, 12, reply.length, false);
        if (question == null) return INVALID;
        Map<String, List<Record>> answers = new HashMap<>();
        int at = question.end + 4;
        for (int i = 0; i < u16(reply, 6); i++) {
            Name owner = name(reply, at, reply.length, true);
            if (owner == null || owner.end + 10 > reply.length) return INVALID;
            int start = owner.end + 10;
            int end = start + u16(reply, owner.end + 8);
            if (end > reply.length) return INVALID;
            Record rr = new Record(owner.text, u16(reply, owner.end),
                    u16(reply, owner.end + 2), start, end);
            // Other classes cannot redirect this query.
            if (rr.dnsClass == query.dnsClass) {
                answers.computeIfAbsent(owner.text, ignored -> new ArrayList<>()).add(rr);
            }
            at = end;
        }
        Walker walk = new Walker(reply, answers, query.type, query.dnsClass);
        if (!walk.visit(query.hostname, query.type == 64 || query.type == 65, 0)) return INVALID;
        return new Result(true, new ArrayList<>(walk.targets));
    }

    private static final class Walker {
        final byte[] data;
        final Map<String, List<Record>> answers;
        final int queryType;
        final int dnsClass;
        final LinkedHashSet<String> targets = new LinkedHashSet<>();
        final Set<String> active = new HashSet<>();
        final Set<String> done = new HashSet<>();
        int states;

        Walker(byte[] data, Map<String, List<Record>> answers, int queryType, int dnsClass) {
            this.data = data;
            this.answers = answers;
            this.queryType = queryType;
            this.dnsClass = dnsClass;
        }

        boolean visit(String host, boolean bindings, int depth) {
            String state = (bindings ? "s:" : "a:") + host;
            if (active.contains(state)) return false;
            if (done.contains(state)) return true;
            if (depth > MAX_DEPTH || ++states > MAX_STATES) return false;
            active.add(state);
            List<Record> exact = answers.getOrDefault(host, Collections.emptyList());
            String canonical = null;
            boolean hasCanonical = false;
            for (Record rr : exact) {
                if (rr.type != 5) continue;
                Name target = name(data, rr.start, rr.end, true);
                if (target == null || target.end != rr.end) return false;
                if (hasCanonical && !canonical.equals(target.text)) return false;
                canonical = target.text;
                hasCanonical = true;
            }
            // An exact CNAME is the effective path, including one synthesized by DNAME.
            if (hasCanonical) {
                if (!follow(canonical, bindings, depth)) return false;
            } else {
                Record dname = null;
                String suffix = host;
                while (!suffix.isEmpty()) {
                    int dot = suffix.indexOf('.');
                    suffix = dot < 0 ? "" : suffix.substring(dot + 1);
                    for (Record rr : answers.getOrDefault(suffix, Collections.emptyList())) {
                        if (rr.type != 39) continue;
                        // DNAME is a singleton, and the closest suffix wins.
                        if (dname != null && !sameTarget(dname, rr, false)) return false;
                        dname = rr;
                    }
                    if (dname != null) break;
                }
                if (dname != null) {
                    Name target = name(data, dname.start, dname.end, false);
                    if (target == null || target.end != dname.end) return false;
                    int prefixEnd = dname.owner.isEmpty() ? host.length() : host.length() - dname.owner.length() - 1;
                    String prefix = host.substring(0, prefixEnd);
                    String replacement = target.text.isEmpty() ? prefix : prefix + "." + target.text;
                    // ASCII presentation length + label/root overhead must fit DNS's 255 octets.
                    if (replacement.length() > 253 || !follow(replacement, bindings, depth)) return false;
                } else if (bindings && dnsClass == 1) {
                    List<Binding> services = new ArrayList<>();
                    boolean aliasMode = false;
                    for (Record rr : exact) {
                        if (rr.type != queryType) continue;
                        Binding b = binding(rr);
                        if (b == null) return false;
                        services.add(b);
                        if (b.priority == 0) aliasMode = true;
                    }
                    for (Binding b : services) {
                        if (aliasMode && b.priority != 0) continue;
                        if (b.priority == 0) {
                            // AliasMode root explicitly means that this service is unavailable.
                            if (!b.target.isEmpty() && !follow(b.target, true, depth)) return false;
                        } else {
                            // ServiceMode root refers to this owner. Endpoint A/AAAA lookups
                            // can use CNAME/DNAME, but do not follow more service bindings.
                            String target = b.target.isEmpty() ? host : b.target;
                            if (!follow(target, false, depth)) return false;
                        }
                    }
                }
            }
            active.remove(state);
            done.add(state);
            return true;
        }

        boolean follow(String target, boolean bindings, int depth) {
            if (!target.isEmpty()) targets.add(target);
            return visit(target, bindings, depth + 1);
        }

        boolean sameTarget(Record a, Record b, boolean compressed) {
            Name left = name(data, a.start, a.end, compressed);
            Name right = name(data, b.start, b.end, compressed);
            return left != null && right != null && left.end == a.end && right.end == b.end
                    && left.text.equals(right.text);
        }

        Binding binding(Record rr) {
            if (rr.end - rr.start < 3) return null;
            int priority = u16(data, rr.start);
            Name target = name(data, rr.start + 2, rr.end, false);
            if (target == null) return null;
            // Validate parameter framing/order. Known-value interpretation (ECH, ALPN,
            // mandatory capabilities) belongs to clients; no parameter bytes become names.
            int pos = target.end, previousKey = -1;
            while (pos < rr.end) {
                if (pos + 4 > rr.end) return null;
                int key = u16(data, pos), length = u16(data, pos + 2);
                if (key <= previousKey || pos + 4 + length > rr.end) return null;
                previousKey = key;
                pos += 4 + length;
            }
            return new Binding(priority, target.text);
        }
    }

    private static final class Record {
        final String owner;
        final int type, dnsClass, start, end;
        Record(String owner, int type, int dnsClass, int start, int end) {
            this.owner = owner; this.type = type; this.dnsClass = dnsClass;
            this.start = start; this.end = end;
        }
    }

    private static final class Binding {
        final int priority;
        final String target;
        Binding(int priority, String target) { this.priority = priority; this.target = target; }
    }

    private static final class Name {
        final String text;
        final int end;
        Name(String text, int end) { this.text = text; this.end = end; }
    }

    /** Keep bytes consumed in the field bounded even when a CNAME pointer leaves RDATA. */
    private static Name name(byte[] data, int start, int limit, boolean pointers) {
        int pos = start, end = -1, steps = 0, wireLength = 1;
        StringBuilder value = new StringBuilder();
        while (pos < (end < 0 ? limit : data.length) && ++steps <= 128) {
            int pointerAt = pos;
            int size = data[pos++] & 255;
            if ((size & 0xc0) == 0xc0) {
                if (!pointers || pos >= (end < 0 ? limit : data.length)) return null;
                int target = ((size & 63) << 8) | (data[pos++] & 255);
                // Backward-only pointers rule out cycles and pointers into later records.
                if (target < 12 || target >= pointerAt) return null;
                if (end < 0) end = pos;
                pos = target;
            } else if (size == 0) {
                return new Name(value.toString().toLowerCase(Locale.ROOT), end < 0 ? pos : end);
            } else {
                if (size > 63 || pos + size > (end < 0 ? limit : data.length)
                        || (wireLength += size + 1) > 255) return null;
                if (value.length() > 0) value.append('.');
                for (int i = 0; i < size; i++) {
                    int c = data[pos + i] & 255;
                    // On-wire international hostnames use ACE (xn--), not raw UTF-8.
                    if (c <= 32 || c >= 127 || c == '.' || c == '\\') return null;
                    value.append((char) c);
                }
                pos += size;
            }
        }
        return null;
    }

    private static int u16(byte[] data, int at) {
        return ((data[at] & 255) << 8) | (data[at + 1] & 255);
    }
}
