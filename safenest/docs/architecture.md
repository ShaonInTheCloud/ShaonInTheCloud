# SafeNest 0.3 architecture

## Protection layers

| Component | Responsibility | Boundary |
| --- | --- | --- |
| SafeNestVpnService | Local DNS route, listed-name rejection, physical-network resolution | Does not relay ordinary web traffic or inspect all encrypted traffic |
| DnsPacketCodec / DomainRules | Validate packets, construct replies, normalize IDN names, match domains and subdomains | Cannot classify every new site or proxy |
| SafeNestAccessibilityService | With consent, send selected apps and detected VPN windows Home; leave Chrome/Firefox open | App-window barrier only; cannot stop every background VPN |
| ManagedProtection | Enrolled device-owner VPN/Private DNS restrictions, Chrome DoH policy, VPN suspension, installation/uninstall restrictions | Separate enrollment; arbitrary HTTPS proxies and unsupported browsers remain gaps |
| ProtectionSetupScreen | Real service/permission state, disclosure, setup and recovery | Running service alone is not proof of protection |

## DNS path and ordinary browsing

Android routes only 10.91.0.53/32 to SafeNest. There is no default route. Ordinary IPv4 and IPv6 traffic stays on the physical network. Android **Block connections without VPN** is incompatible with this design and can stop normal connections. API 29+ status detection exposes this error; managed setup leaves Lockdown off.

The TUN reader validates IPv4 UDP DNS packets addressed to the private resolver. Listed names get NXDOMAIN immediately. API 29+ uses cancellable raw DNS through the physical Network with a queue-inclusive 4.5-second budget. Configured-resolver UDP/TCP fallback is allowed only without active/strict Private DNS. API 28 encrypted Private DNS stops the filter with an explanation. Replies must match the question and transaction ID. Reachable answer aliases are checked before returning original records. Workers/queues are bounded; old sessions cannot write into a new tunnel. See network-changes-0.3.md.

Upstream TCP DNS retry is implemented. **Client-to-local-resolver TCP DNS is not implemented**; large replies requiring it can fail. There is no general TCP/IP relay, arbitrary external DNS interception, direct-IP filtering or TLS decryption. App/browser DoH can bypass the DNS path. Empty HTTPS/SVCB responses do not constitute a general DoH blocker.

## Guard and permissions

The guard is off by default and requires disclosure, explicit opt-in and Android Accessibility activation. It checks foreground package identity only. Since0.3.2 it does not read address bars or send Chrome/Firefox to Home. Selected nonessential blocked apps still trigger Home. Website blocking belongs to the DNS and managed Chrome policy layers. No Accessibility browsing log is stored or uploaded.

System apps, calls, launcher, Settings, permission controls and SafeNest are excluded from app selection. The guard never clicks permission dialogs or prevents revocation. Optional legacy Device Administrator adds the ordinary deactivation step before uninstall; it does not grant device-owner authority. The 0.3.12 customer UI does not expose app-guard pause or emergency restore. A managed deployment still needs an administrator repair path for failures.

## Managed policies and recovery

After separate eligible enrollment and successful browsing checks, managed mode applies:

- SafeNest Always-on with Lockdown off, and VPN configuration restriction.
- Private DNS configuration restriction on API 29+. An existing strict hostname becomes Automatic; Off/Automatic remain unchanged.
- Chrome managed DnsOverHttpsMode=off, not a policy for every browser.
- Chrome URLBlocklist navigation rules, sharing a 1,000-entry capacity with pre-existing rules. Existing allowlist exceptions remain and are reported. Rules refresh after list changes and process startup; status reports submission, not proven browser enforcement.
- Suspension of visible, nonessential VPN-service packages, including newly installed packages.
- Ordinary unknown-source installation restrictions and SafeNest uninstall restriction.

Changed values are journaled before mutation. Reapplying preserves the original recovery values. Release resumes tracked apps before restoring the previous Always-on VPN and retains failed recovery entries for retry. It does not remove device-owner enrollment. This version does not newly restrict debugging or Safe Mode; legacy recovery clears those restrictions from the earlier prototype.

## Rules and data

The editable JSON import uses version 1, a domains array and gambling/adult/custom categories, capped at 2 MiB and 25,000 entries. Signed catalogs use a separate canonical format, pinned P-256 public key, increasing revision and expiry validation. The stored state is atomic; failed updates retain the prior verified list. Catalog rules and local lists are separate and combined for matching. Catalog updates are manually requested; no live classification service ships with the project.

Android uses local preferences for editable rules, check-ins, guard choices and policy recovery, plus an atomic file for verified catalog state. The web companion uses browser local storage and does not enforce phone blocking or provide connected accounts/payments. DNS uses the configured network resolver; providers can see the queries they process. A catalog refresh contacts the configured HTTPS host, without attaching browsing history. No private signing key is needed on the phone.

See verification-0.3.md and device-test-plan.md.
