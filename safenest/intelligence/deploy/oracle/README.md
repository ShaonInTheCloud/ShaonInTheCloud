# SafeNest smart blocklist checker on Oracle Cloud (Always Free)

**What it does.** Every day it visits each gambling domain in Supabase (`domain_health`). It records
whether the domain still resolves and responds, and whether the page still looks like gambling
(English and Bangla signals: casino, betting, slots, deposit, bKash, Nagad, bonus…). It follows
redirects, and a redirect to a new gambling domain adds that mirror automatically. A domain that
has been dead for 30 days moves to the archive, which is not blocked but is still checked weekly;
if the domain comes back, it returns to the active list. A domain that no longer looks like gambling
after three checks in a row goes to `review` for the team. The list therefore stays clean instead of
growing forever.

**Where things live.** The checker runs on the Oracle server. Every domain, state and result lives in
Supabase. Nothing is stored only on Oracle, so losing the VM loses nothing.

## 1. Create the server (about 10 minutes)
1. Sign up at cloud.oracle.com and choose your **home region** carefully, because Always Free resources stay there.
2. Compute → Instances → **Create instance**.
   - Image: **Ubuntu 24.04**. Shape: **Ampere A1 (VM.Standard.A1.Flex)**, 2 OCPU and 12 GB, or 4/24 (all Always Free).
   - Add your SSH public key. Leave the networking defaults; no inbound ports beyond SSH are needed.
3. Note the public IP, then `ssh ubuntu@<ip>`.

## 2. Install the checker
```sh
curl -fsSL https://raw.githubusercontent.com/ShaonInTheCloud/ShaonInTheCloud/safenest-global-card-crypto/safenest/intelligence/deploy/oracle/setup.sh | bash
sudo nano /etc/safenest-health.env        # paste SUPABASE_SERVICE_ROLE_KEY (Supabase → Project Settings → API keys)
sudo systemctl start safenest-health-seed.service      # one-time: imports the 541k names
sudo systemctl start safenest-health.service           # first check now (or wait for 02:00 UTC)
journalctl -u safenest-health -f                       # watch progress
```
The service role key can read and write your whole database. It lives only in `/etc/safenest-health.env`
(root-only, mode 600) and never goes in Git.

## 3. Before the first run: create the table in Supabase
Apply `supabase/migrations/20261018090000_domain_health.sql`, using the "SafeNest deploy backend" workflow
with `apply`, or `npx supabase db push`.

## Daily output
- Supabase: `select * from domain_health_summary();` gives counts per state.
- `/var/lib/safenest/health-export/blocked.txt`: the live list (active + dormant + review + unchecked).
- `/var/lib/safenest/health-export/review.csv`: domains for the team to look at. To mark one as
  "keep blocking", set `reviewed_by_team = true, state = 'active'`. To drop it, delete the row or set
  `state = 'archived'`.

## Capacity
At `HEALTH_CONCURRENCY=48`, a 541k-domain pass takes roughly 6–10 hours on the free ARM VM. Active and
dormant domains are rechecked daily, review domains every 3 days, and archived/parked domains weekly,
so daily work shrinks as the list settles. Oracle may reclaim Always Free VMs that sit idle for a week
(very low CPU). The daily run keeps this one busy, but upgrading the account to "Pay As You Go"
(still free within limits) removes that risk.

## Not automatic (on purpose)
- New mirrors are added as `unchecked` and are verified on the next run; nothing is trusted from a single page.
- The apps' bundled list and the iPhone filter are rebuilt from `blocked.txt` when you choose to.
  The signed catalogue release still needs the signing key (see `src/cli.mjs publish`).
