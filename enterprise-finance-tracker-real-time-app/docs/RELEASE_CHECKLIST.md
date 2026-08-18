# Production Release Checklist & Pre-Flight Audit

## 📋 1. Pre-Flight Code Quality & Security Audit

- [ ] **Target SDK 35 & 64-bit Compliance**: `compileSdk = 35`, `targetSdk = 35`, 64-bit native libs validated.
- [ ] **StrictMode Clean**: Zero ThreadPolicy or VmPolicy violations during exploratory testing.
- [ ] **ProGuard / R8 Verification**:
  - Run `./gradlew assembleRelease` and verify no missing classes or warnings.
  - Verify Kotlinx `@Serializable` models and Room `@Entity` classes are preserved.
  - Verify `mapping.txt` is generated and uploaded to Google Play Console for de-obfuscation.
  - Verify `Log.d` and `Log.v` are completely stripped from release DEX.
- [ ] **LeakCanary / Memory Audit**: Zero Activity memory leaks after 10 configuration changes (rotations).
- [ ] **Deep Link Verification**: `https://financetracker.enterprise.com/transactions/*` deep link verified via ADB:
  ```bash
  adb shell am start -a android.intent.action.VIEW -d "https://financetracker.enterprise.com/transactions/tx_100" com.enterprise.financetracker
  ```
- [ ] **Baseline Profiles**: Pre-compiled Baseline Profile assets bundled inside release AAB.

---

## 🚀 2. Release & Staged Rollout Strategy

1. **Internal Testing (Track 1)**: QA & Dogfooding (24 hours).
2. **Closed Alpha (Track 2)**: 100 enterprise test users.
3. **Staged Production Rollout (Track 3)**:
   - Day 1: 5% rollout (Monitor crash rate and ANR threshold in Play Console).
   - Day 2: 15% rollout.
   - Day 3: 50% rollout.
   - Day 4: 100% full release.
