# Production Incident Response Playbook — Enterprise Finance Tracker

## 🚨 1. Incident Severity Definitions

| Severity | Definition | Threshold / Impact | SLA Response |
|---|---|---|---|
| **P0 (Critical Outage)** | App crash loop, login impossible, database corruption, critical data loss. | Crash rate > 1.09%, ANR rate > 0.47%, or >10,000 users affected. | Immediate (<15 mins) |
| **P1 (High Degradation)** | Broken transaction creation, market ticker outage, token refresh loops. | Core feature degraded for >10% of active sessions. | < 1 hour |
| **P2 (Medium Defect)** | Visual glitch, non-critical deep link broken, filter chip styling bug. | Non-blocking, workaround exists. | Next regular sprint |

---

## 🛠️ 2. P0 Incident Triage Runbook (Step-by-Step)

```
1. TRIAGE & ASSESS (0 - 15 Mins)
   • Check Google Play Console Android Vitals & Firebase Crashlytics.
   • Identify affected app versions, device models, and Android OS versions.
   • Determine if root cause is Backend API (HTTP 500) vs Mobile Client (NPE / Room Migration Crash).

2. MITIGATE IMPACT (15 - 30 Mins)
   • If caused by a feature rollout: Disable via Remote Config feature flag kill-switch.
   • If caused by backend API change: Roll back backend deployment or restore previous payload schema.
   • If caused by client bug: Halt Google Play staged rollout immediately.

3. HOTFIX & DEPLOY (30 - 90 Mins)
   • Branch from release tag: git checkout -b hotfix/1.0.1 v1.0.0
   • Apply minimal surgical patch.
   • Run full test suite: ./gradlew test
   • Request Expedited Review in Google Play Console.
   • Release to 10% -> 25% -> 50% -> 100% staged rollout.

4. POST-MORTEM & ROOT CAUSE ANALYSIS (Within 48 Hours)
   • Write 5-Whys root cause analysis.
   • Add automated regression test to CI suite.
```

---

## 🔬 3. Memory Leak & ANR Investigation Protocols

### ANR (Application Not Responding) Protocol
1. Pull Google Play Console ANR stack trace or pull `/data/anr/traces.txt` via ADB:
   ```bash
   adb pull /data/anr/traces.txt
   ```
2. Inspect the **"main"** thread state. Look for:
   - `BLOCKED` on synchronization lock or Coroutines `runBlocking`.
   - `TIMED_WAIT` on network call or database query executing on Main thread.
3. Fix: Offload disk/network operations to `dispatchers.io` and enable `StrictModeInitializer` in debug builds.

### Out of Memory (OOM) / Memory Leak Protocol
1. Reproduce in Android Studio Profiler using **Dump Java Heap**.
2. Filter by `com.enterprise.financetracker`.
3. Check `MainActivity` retained count. If > 1, follow the **Shortest Path to GC Root** to find leaked static references or uncancelled Coroutine Jobs.
