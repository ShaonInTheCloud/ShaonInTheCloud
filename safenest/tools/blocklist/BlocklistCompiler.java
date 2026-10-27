import com.safenest.app.CompactDomainSet;
import com.safenest.app.DomainRules;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Compiles reviewed hostname sources into a SafeNest compact blocklist asset.
 *
 * Usage:
 *   BlocklistCompiler --out <asset.snbl> --manifest <manifest.json> --exclude <exclusions.txt>
 *                     [--minus <other.snbl>] [--priority-out <names.txt>] <source>...
 *
 * Sources: .txt = one hostname per line ('#' comments allowed); .tsv = hostname in the
 * first column, rows whose second column starts with '?' (unconfirmed) are skipped.
 * Every name is normalized with the app's own DomainRules, so the asset matches exactly
 * what the phone looks up. Output is deterministic for identical inputs.
 */
public final class BlocklistCompiler {
    public static void main(String[] args) throws IOException {
        Path out = null, manifest = null, exclude = null, minus = null, priorityOut = null;
        List<Path> sources = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--out": out = Paths.get(args[++i]); break;
                case "--manifest": manifest = Paths.get(args[++i]); break;
                case "--exclude": exclude = Paths.get(args[++i]); break;
                case "--minus": minus = Paths.get(args[++i]); break;
                case "--priority-out": priorityOut = Paths.get(args[++i]); break;
                default: sources.add(Paths.get(args[i]));
            }
        }
        if (out == null || manifest == null || exclude == null || sources.isEmpty()) {
            System.err.println("usage: --out X --manifest Y --exclude Z [--minus W] [--priority-out P] sources...");
            System.exit(2);
        }
        Set<String> excluded = new TreeSet<>();
        for (String raw : readNames(exclude, false)) {
            String host = DomainRules.normalizeHostname(raw);
            if (host == null) throw new IllegalArgumentException("Invalid exclusion: " + raw);
            excluded.add(host);
        }

        Set<String> all = new LinkedHashSet<>();
        Map<String, int[]> perSource = new LinkedHashMap<>();   // {accepted, invalid, excluded}
        Map<String, String> sourceHashes = new LinkedHashMap<>();
        Set<String> removedByExclusion = new TreeSet<>();
        for (Path source : sources) {
            int accepted = 0, invalid = 0, dropped = 0;
            for (String raw : readNames(source, source.toString().endsWith(".tsv"))) {
                String host = DomainRules.normalizeHostname(raw);
                if (host == null) { invalid++; continue; }
                if (excluded.contains(host)) { dropped++; removedByExclusion.add(host); continue; }
                all.add(host);
                accepted++;
            }
            perSource.put(source.getFileName().toString(), new int[]{accepted, invalid, dropped});
            sourceHashes.put(source.getFileName().toString(), CompactDomainSet.hexSha256(Files.readAllBytes(source)));
        }

        CompactDomainSet set = CompactDomainSet.fromHostnames(all);
        int beforeMinus = set.size();
        if (minus != null) {
            try (var in = Files.newInputStream(minus)) { set = set.minus(CompactDomainSet.read(in)); }
        }
        byte[] bytes = set.toBytes();
        Files.createDirectories(out.toAbsolutePath().getParent());
        try (OutputStream o = Files.newOutputStream(out)) { o.write(bytes); }

        if (priorityOut != null) {
            Files.createDirectories(priorityOut.toAbsolutePath().getParent());
            StringBuilder sb = new StringBuilder("# Bangladesh-researched gambling hosts given priority in Chrome's limited managed-policy list.\n");
            for (String host : new TreeSet<>(all)) sb.append(host).append('\n');
            Files.write(priorityOut, sb.toString().getBytes(StandardCharsets.US_ASCII));
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"format\": \"SNBL v").append(CompactDomainSet.VERSION).append("\",\n");
        json.append("  \"asset\": \"").append(out.getFileName()).append("\",\n");
        json.append("  \"asset_sha256\": \"").append(CompactDomainSet.hexSha256(bytes)).append("\",\n");
        json.append("  \"unique_hostnames\": ").append(all.size()).append(",\n");
        json.append("  \"fingerprints_before_minus\": ").append(beforeMinus).append(",\n");
        json.append("  \"fingerprints\": ").append(set.size()).append(",\n");
        json.append("  \"minus\": ").append(minus == null ? "null" : "\"" + minus.getFileName() + "\"").append(",\n");
        json.append("  \"excluded_hosts_removed\": [");
        int k = 0;
        for (String h : removedByExclusion) json.append(k++ == 0 ? "" : ", ").append('"').append(h).append('"');
        json.append("],\n  \"sources\": [\n");
        k = 0;
        for (Map.Entry<String, int[]> e : perSource.entrySet()) {
            int[] c = e.getValue();
            json.append(k++ == 0 ? "" : ",\n").append("    {\"file\": \"").append(e.getKey())
                .append("\", \"sha256\": \"").append(sourceHashes.get(e.getKey()))
                .append("\", \"accepted\": ").append(c[0]).append(", \"invalid\": ").append(c[1])
                .append(", \"excluded\": ").append(c[2]).append('}');
        }
        json.append("\n  ]\n}\n");
        Files.write(manifest, json.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(out.getFileName() + ": " + set.size() + " fingerprints (" + all.size() + " unique names, "
            + removedByExclusion.size() + " excluded)");
    }

    private static List<String> readNames(Path file, boolean tsv) throws IOException {
        List<String> names = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (tsv) {
                    String[] cols = trimmed.split("\t");
                    if (cols.length > 1 && cols[1].startsWith("?")) continue;
                    names.add(cols[0].trim());
                } else {
                    names.add(trimmed);
                }
            }
        }
        return names;
    }
}
