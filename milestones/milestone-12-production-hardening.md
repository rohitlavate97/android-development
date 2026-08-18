# Milestone 12: Production Hardening

## Title, Goal & Phase Alignment
**Goal:** Prepare the application for public release by catching memory leaks, ensuring optimal performance, and setting up release automation.
**Phase:** Production Hardening

## Architecture & Component Blueprint
- **StrictMode:** Main-thread IO checks.
- **LeakCanary:** Automatic memory leak detection.
- **Baseline Profiles:** AOT compilation rules for faster startup and smoother scrolling.
- **Security:** `FLAG_SECURE` for preventing screenshots on sensitive screens (e.g., bank sync).
- **Fastlane:** `Fastfile` for automated Play Store deployments.
- **Remote Config:** Feature flags and kill switches.

## Step-by-Step Implementation Instructions
1. Initialize `StrictMode` in the custom `Application` class (debug builds only).
2. Integrate LeakCanary dependency for `debugImplementation`.
3. Apply `FLAG_SECURE` to the Window for sensitive ViewModels/Screens.
4. Generate Baseline Profiles using Macrobenchmark and configure the Baseline Profile Gradle plugin.
5. Setup a `Fastfile` defining beta and release lanes.
6. Implement a Remote Config fetch to act as a forced update / kill switch trigger.

## Code Snippets & Signatures
```kotlin
// StrictMode setup
if (BuildConfig.DEBUG) {
    StrictMode.setThreadPolicy(
        StrictMode.ThreadPolicy.Builder()
            .detectDiskReads()
            .detectDiskWrites()
            .detectNetwork()
            .penaltyLog()
            .penaltyDeath()
            .build()
    )
}

// Window Security
window.setFlags(
    WindowManager.LayoutParams.FLAG_SECURE,
    WindowManager.LayoutParams.FLAG_SECURE
)

// Fastlane Snippet
lane :beta do
  gradle(task: "bundleRelease")
  upload_to_play_store(track: "beta")
end
```

## Deliberate Bugs to Catch & Debug
- Leaving LeakCanary in the `releaseImplementation` causing massive overhead for users.
- Accidentally running disk IO operations on the Main thread, immediately caught and crashed by StrictMode.
- Applying `FLAG_SECURE` app-wide instead of specifically on sensitive screens.

## Unit Testing Requirements (Given-When-Then)
- **Given** a deprecated API version, **When** Remote Config kill switch is evaluated, **Then** app navigates to an update screen.
- **Given** Macrobenchmark execution, **When** capturing startup metrics, **Then** Baseline Profiles provide a >15% improvement in Time-To-Initial-Display.

## Acceptance Criteria Checklist
- [ ] StrictMode enabled for Debug builds.
- [ ] LeakCanary active and zero known memory leaks.
- [ ] Baseline Profiles generated and bundled with release APK/AAB.
- [ ] Window security prevents screenshots on credential/financial screens.
- [ ] Fastlane configured for automated Google Play deployment.
- [ ] Kill switches integrated for emergency deprecation.
