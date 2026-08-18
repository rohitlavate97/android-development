# PHASE 13 — RELEASE ENGINEERING, SECURITY & THE PRODUCTION PLAYBOOK (Week 18)

**Objective:** Ship production Android applications safely, secure sensitive user data using hardware-backed cryptography, and master Google Play release engineering and incident management.
**Why this phase matters:** Writing clean code is irrelevant if an app is compromised by reverse engineers, leaks user financial data, or gets stuck in a 1-star crash loop with no rollback mechanism. Professional Android engineers own the path from git commit to Play Store rollout, security posture, and production incident response.
**Prerequisites:** All previous Phases (1 through 12).
**Project deliverable:** Release Pipeline & Production Incident Drill — Hardware-backed biometrics, EncryptedSharedPreferences, Fastlane deployment lane, and simulated production kill-switch drill.
**Concepts covered:** 8 total, each with the full 13-step teaching sequence.

---

## 1. Google Play Console & Release Tracks

### 1. What is it
Google Play Console's Release Tracks are deployment channels (Internal, Closed/Alpha, Open/Beta, Production) used to distribute app updates to progressively larger user groups. It includes **Staged Rollouts**, which allow releasing to a percentage of users, and the **In-App Updates API**, which prompts users to update from within the app itself.

### 2. Why does it exist
Deploying a mobile app is **irreversible**. Unlike a web app where you can deploy a hotfix to servers in seconds, a buggy mobile update stays on a user's phone until they explicitly download the next version. Release tracks and staged rollouts de-risk this process by catching crashes before they hit 100% of your user base.

### 3. Mental model
Think of Release Tracks as your **Environment Pipeline** (Dev → UAT → Beta → Prod).
Think of Staged Rollouts as a **Canary Deployment** (1% → 5% → 20% → 100%).
Think of the In-App Updates API as a **Forced Page Refresh**, ensuring users are on the required client version for a new backend API.

### 4. How it works
- **Internal Testing:** Instant rollout to ~100 whitelisted emails. No Google review required.
- **Closed Testing (Alpha):** Wider group of trusted testers.
- **Open Testing (Beta):** Anyone on the Play Store can opt-in.
- **Production:** Live to the world.
- **Staged Rollout:** When pushing to Prod, you halt at 1%, monitor Android Vitals (crash rate, ANR rate) for 24 hours, then bump to 5%, etc. If Vitals spike, you halt the rollout.

### 5. Code (In-App Updates API)
When a critical backend change happens, you need to force users to update. You implement this in your `MainActivity`.

```kotlin
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability

class MainActivity : AppCompatActivity() {
    private val appUpdateManager by lazy { AppUpdateManagerFactory.create(this) }

    // Modern API: register an ActivityResultLauncher instead of passing a raw request code.
    // The old `startUpdateFlowForResult(info, type, activity, requestCode)` + onActivityResult()
    // overload still compiles but is deprecated — don't teach it as current best practice.
    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Log.e("AppUpdate", "Update flow failed or was cancelled: ${result.resultCode}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo

        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                // Check if an IMMEDIATE update is allowed
                && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            ) {
                // Request the update
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build() // Blocks the UI until updated
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If an immediate update is stalled/paused, resume it when app backgrounds/foregrounds
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateResultLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
                )
            }
        }
    }
}
```

> **[Extension] Note:** Older tutorials (and the previous version of this example) show `startUpdateFlowForResult(appUpdateInfo, AppUpdateType.IMMEDIATE, activity, requestCode)` overridden with `onActivityResult()`. That overload is deprecated — the current API takes an `ActivityResultLauncher` (registered via `registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult())`) plus an `AppUpdateOptions` object instead of a raw `AppUpdateType` + request code. The underlying `AppUpdateManager`/Play Core concepts (Immediate vs Flexible, staleness, `appUpdateInfo`) are unchanged — only the plumbing for getting the result back changed.

### 6. Production usage
- **Immediate Update:** Used when a security vulnerability is patched, or a V1 backend endpoint is being deprecated, preventing old clients from working.
- **Flexible Update:** Used for feature additions. It downloads the update in the background while the user continues using the app, then prompts them to restart to apply it.

### 7. Common mistakes
❌ **Wrong:** Publishing directly to 100% Production track on Friday at 5 PM.
✅ **Right:** Publishing to 1% on Monday morning, monitoring Firebase Crashlytics and Play Console Vitals, then rolling out to 100% by Wednesday.

❌ **Wrong:** Relying only on Play Store auto-updates (some users disable this).
✅ **Right:** Implementing `AppUpdateManager` to handle critical version deprecations gracefully.

### 8. Debugging
- To test in-app updates locally, use the **Internal App Sharing** link in Play Console. It allows you to spoof version codes and trigger the update UI without waiting for Google review.
- If a rollout causes crashes, go to **Play Console > Release > Production > Halt Rollout**.

> **[Extension] Halting a *fully* rolled-out release:** Play Console now also lets you halt a release that already reached 100%, not just one still in a staged rollout. Halting doesn't downgrade anyone already running the bad version — Android still can't force a device backward — but it immediately stops new installs/updates of the bad build and makes the **previous fully-rolled-out version** available again to everyone who hasn't updated yet. This only helps if a meaningful slice of users hasn't already pulled the bad update, so it's most valuable when you catch the problem early — staged rollout discipline (Concept 1's 1% → 5% → 20% pattern) is still what limits blast radius, this is a second safety net on top of it, not a replacement for it.

### 9. Testing
Google provides a `FakeAppUpdateManager` for unit/UI testing without triggering the real Play Store.

```kotlin
@Test
fun testImmediateUpdatePrompt() {
    val fakeAppUpdateManager = FakeAppUpdateManager(context)
    fakeAppUpdateManager.setUpdateAvailable(2) // Simulate higher version available
    // Assert that your UI state reacts to this
}
```

### 10. Exercise
Add a mechanism in your Expense Tracker to fetch a `min_required_version` from a remote JSON file (or Firebase Remote Config). If `BuildConfig.VERSION_CODE` is less than `min_required_version`, show a blocking UI that redirects to the Play Store.

### 11. Deliberate failure
Implement the `AppUpdateManager` Flexible update, but forget to call `appUpdateManager.completeUpdate()` when the download finishes.
**Result:** The update downloads in the background but is never installed, leaving the user on the old version indefinitely and wasting their bandwidth.

### 12. Interview questions
- **Q:** We just deployed version 2.0 to 10% of users, and it has a critical crash on startup. How do we roll back?
  - **A:** You *cannot* roll back an Android version in the Play Console. You must **Halt the Rollout** immediately to protect the remaining 90%. Then, create version 2.0.1 with a fix (or reverting the code), upload it, and release it to 100% to overwrite the broken 2.0 for affected users.
- **Q:** Explain the difference between Flexible and Immediate in-app updates. When would you use each?

### 13. Checkpoint
You understand that releasing an app is a phased process, and you possess the mental and programmatic tools to halt disasters and force critical updates.

---

## 2. Android App Bundle (AAB) & Play App Signing

### 1. What is it
An **Android App Bundle (.aab)** is Android's official publishing format. It contains all your app's compiled code and resources, but defers APK generation and signing to Google Play. **Play App Signing** is the mechanism where Google manages your app's true signing key securely.

### 2. Why does it exist
Before AABs, developers uploaded a massive "fat APK" containing image resolutions for all screens (mdpi to xxxhdpi), strings for 50 languages, and native C++ libraries for 4 different CPU architectures (arm64, x86, etc.).
A user with an English ARM64 phone downloading this APK wasted 20-40% of the download size on assets their phone could never physically use. AABs fix this by letting Google generate a tailored, minimized APK for each specific device.

### 3. Mental model
- **APK** = A fully baked cake. Everyone gets the exact same cake.
- **AAB** = The recipe and all raw ingredients. You give it to Google (the baker), and when a user requests the app, Google bakes a specific cupcake tailored exactly to that user's dietary needs (screen density, language, CPU).
- **Play App Signing** = A bank vault (Google's HSM) holding your true identity. You hold a "building pass" (Upload Key) that gets you into the lobby to deliver the AAB.

### 4. How it works
1. You build an `.aab` file from Android Studio.
2. You sign the AAB with your **Upload Key**.
3. You upload the AAB to Play Console.
4. Google verifies the Upload Key, then strips it.
5. When a user downloads the app, Google generates a split APK for their device configuration, signs it with your **App Signing Key** (which Google holds securely), and delivers it to the device.

### 5. Code / Command Line
Instead of `assembleRelease`, you run:
```bash
./gradlew bundleRelease
```

To test locally what Google will generate, you use Google's `bundletool`:
```bash
# Generate APKs from AAB
bundletool build-apks --bundle=app.aab --output=app.apks \
--ks=keystore.jks --ks-pass=pass:password \
--ks-key-alias=MyKey --key-pass=pass:password

# Install the exact split APK to a connected device
bundletool install-apks --apks=app.apks
```

### 6. Production usage
Every modern app uses AAB. Furthermore, AAB enables **Dynamic Feature Modules**—e.g., if you have a "Customer Support Video Chat" feature that uses a 15MB WebRTC library, you can set it to download *only* when the user clicks the "Support" button, rather than packaging it at install time.

### 7. Common mistakes
❌ **Wrong:** Generating a `.apk` for release and trying to upload it to the Play Store (Google blocks this for new apps).
✅ **Right:** Generating `.aab` and enrolling in Play App Signing.

❌ **Wrong:** Relying on `BuildConfig.VERSION_CODE` checks with third-party APIs (like Facebook Login) without realizing Google re-signs the app. The SHA-1 fingerprint of your App Signing Key will differ from your Upload Key!

### 8. Debugging
If third-party SDKs (Google Maps, Firebase Auth, Facebook Login) fail in Production but work in local release builds, 99% of the time it is because you registered the SHA-1 of your *Upload Key* with the service, but failed to register the SHA-1 of the *App Signing Key* (found in Play Console -> Setup -> App Integrity).

### 9. Testing
Use Android Studio's "Run" configuration to deploy an APK from an App Bundle to ensure nothing is stripped improperly during the split process.

### 10. Exercise
Generate an `.aab` for the Expense Tracker. Download `bundletool`. Run the command to extract a device-specific APK for your emulator, and observe the file size difference compared to a standard universal APK.

### 11. Deliberate failure
Try to use a third-party SDK (like Google Sign-In) in a Production track release, but only whitelist the SHA-1 of your debug keystore or upload keystore. Watch the authentication fail.

### 12. Interview questions
- **Q:** Our build machine was wiped, and we lost the `.jks` Keystore file we use to upload to Google Play. Is our app doomed?
  - **A:** Not anymore! Because we use Play App Signing, we only lost our *Upload Key*. We can contact Google Play Developer Support, verify our identity, and provide a new Upload Key. The App Signing Key remains safe with Google, so users will still get seamless updates without having to uninstall the app.
- **Q:** Explain how an AAB reduces download size.

### 13. Checkpoint
You understand the separation between the Upload Key and App Signing Key, and you know how device-specific code splitting works under the hood.

---

## 3. Android Security: Keystore, Encrypted Storage & Biometrics

### 1. What is it
The **Android Keystore System** lets you store cryptographic keys in a container to make it more difficult to extract from the device. Hardware-backed Keystore means the keys never leave the secure hardware (TEE or StrongBox).
`EncryptedSharedPreferences` wraps this to provide secure key-value storage. `BiometricPrompt` provides the UI and crypto-binding for Face/Fingerprint unlock.

> **[Extension] Note:** `EncryptedSharedPreferences` (and the rest of the `androidx.security:security-crypto` library) was marked **deprecated** by Google in 2025 ("Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore"). It still works, is still shipping bug fixes, and you will see it in a large fraction of existing production codebases — which is exactly why it's still taught below — but don't start a *new* project on it. The concepts here (a hardware-backed `MasterKey`, envelope encryption of a keyset, AES-GCM for values) are the right mental model either way; the emerging replacement pattern is **Proto DataStore + Google Tink's `StreamingAead`** (encrypt the whole file with a Tink keyset backed by a Keystore-protected master key, store it via DataStore instead of `SharedPreferences`), which moves I/O off the main thread and avoids `SharedPreferences`' own well-known issues (synchronous reads, `apply()` disk writes on a background thread pool it doesn't let you control).

### 2. Why does it exist
Standard `SharedPreferences` saves data in a plain text XML file at `/data/data/com.yourapp/shared_prefs/`. If a device is rooted, or backed up via adb, malicious actors can read your user's JWT OAuth tokens and hijack their session.
Relying on a simple PIN or Boolean `isLoggedIn` variable can be bypassed via memory editing or reverse engineering.

### 3. Mental model
- Standard Prefs = Leaving your diary on your desk.
- Keystore = A heavy safe bolted to the floor. You can slide documents into it, and push buttons to get it to stamp documents, but you can never pull the actual stamping mechanism (the private key) out of the safe.

### 4. How it works
`EncryptedSharedPreferences` uses a two-key architecture (Tink library under the hood):
1. A **Keyset** encrypts your data (values are encrypted with AES256-GCM, keys with AES256-SIV).
2. A **MasterKey** encrypts the Keyset.
3. The MasterKey is generated and stored securely inside the Android Keystore.

### 5. Code
**1. Encrypted Storage (JWT Token)**
```kotlin
// Build the MasterKey via Android Keystore
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

// Initialize EncryptedSharedPreferences
val sharedPreferences = EncryptedSharedPreferences.create(
    context,
    "secure_prefs",
    masterKey,
    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
)

// Usage is exactly the same as normal SharedPreferences
sharedPreferences.edit().putString("auth_token", "jwt_ey...").apply()
```

**2. Biometric Authentication**
```kotlin
val executor = ContextCompat.getMainExecutor(context)
val biometricPrompt = BiometricPrompt(activity, executor,
    object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            // User authenticated!
            // Result can contain a CryptoObject to decrypt highly sensitive data
        }
    })

val promptInfo = BiometricPrompt.PromptInfo.Builder()
    .setTitle("Log in to Expense Tracker")
    .setSubtitle("Use your fingerprint to access your financial data")
    .setNegativeButtonText("Use PIN")
    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    .build()

biometricPrompt.authenticate(promptInfo)
```

**3. Certificate Pinning (OkHttp)**
Prevents Man-In-The-Middle (MITM) attacks even if the user installs a rogue root certificate.
```kotlin
val certificatePinner = CertificatePinner.Builder()
    .add("api.mytracker.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .build()

val client = OkHttpClient.Builder()
    .certificatePinner(certificatePinner)
    .build()
```

### 6. Production usage
- Banking apps use Keystore to encrypt the local database (via SQLCipher).
- Password managers use Biometric `CryptoObject` bindings. The fingerprint doesn't just return `true`; the hardware physically unlocks a cryptographic key used to decrypt the vault, making software bypasses impossible.

### 7. Common mistakes
❌ **Wrong:** Storing `"isPremiumUser" = true` in standard SharedPreferences. A user can root their phone, edit the XML, and get premium for free.
✅ **Right:** Deriving premium status dynamically from a verified backend JWT, stored in `EncryptedSharedPreferences`.

❌ **Wrong:** Trusting the `onAuthenticationSucceeded` callback blindly.
✅ **Right:** Using a `CryptoObject` with the BiometricPrompt to perform the actual decryption of the auth token. If the auth is bypassed via memory hooking, the decryption will still fail because the hardware didn't release the key.

### 8. Debugging
Keystore issues often manifest as `KeyStoreException` or `BadPaddingException` when the OS upgrades or the user changes their lock screen type, sometimes invalidating keys. Always wrap crypto operations in try-catch and handle fallback (e.g., force the user to log in again).

### 9. Testing
Crypto logic is hard to test on JVM unit tests because Android Keystore requires an emulator/device. Use Robolectric's limited Keystore support, or better, use Android Instrumented tests (`@RunWith(AndroidJUnit4::class)`).

### 10. Exercise
Convert your Expense Tracker's token storage from standard DataStore/SharedPreferences to `EncryptedSharedPreferences`. Implement an OkHttp Interceptor that reads the token from this secure storage.

### 11. Deliberate failure
Hardcode your API token in `strings.xml`. Decompile your own APK using `apktool` and observe how trivially easy it is to find the token in plain text.

### 12. Interview questions
- **Q:** How do you secure data at rest in Android?
  - **A:** By using `EncryptedSharedPreferences` for key-value pairs or SQLCipher for Room databases, backed by a `MasterKey` stored in the Android hardware Keystore.
- **Q:** What is Certificate Pinning, and what is the risk of implementing it?
  - **A:** It forces the app to only trust specific SSL certificates, preventing MITM attacks via compromised root CAs. The risk is that if your server rotates its certificate unexpectedly and the new hash isn't baked into the app, you will completely lock out all users until they download an app update.

### 13. Checkpoint
You can protect data at rest against root-access attacks and secure the network layer against rogue CAs.

---

## 4. Mobile App Vulnerabilities & OWASP Top 10 Mobile

### 1. What is it
The OWASP Mobile Top 10 is a list of the most critical security risks for mobile apps. Key Android-specific vulnerabilities include Insecure Data Storage, Insecure Communication, Tapjacking, and accidental data leakage.

### 2. Why does it exist
Mobile devices exist in hostile environments. They connect to public airport Wi-Fi (interception risks), install third-party apps (overlay/malware risks), and can be stolen or physically tampered with. The application layer must defend itself.

### 3. Mental model
Treat the Android OS as "Zero Trust".
- Don't trust the file system (encrypt it).
- Don't trust the network (pin it).
- Don't trust the screen (prevent overlays/screenshots).
- Don't trust the device integrity (check for root).

### 4. How it works & Implementations
**1. Insecure Communication (Cleartext Traffic):**
Android 9+ blocks cleartext (HTTP) by default. Never set `android:usesCleartextTraffic="true"` in your manifest unless strictly communicating with a local IoT device that lacks SSL.

**2. Tapjacking (Overlay Attacks):**
A malicious app draws a transparent overlay over your app. When the user thinks they are clicking "Play Game", they are actually clicking "Transfer Funds" on your app sitting underneath.
*Fix:* In XML layouts, use `android:filterTouchesWhenObscured="true"`.

**3. Screen Leakage (App Switcher / Screenshots):**
When users background an app, Android takes a screenshot for the Recents (App Switcher) carousel. This can leak account balances.
*Fix:* Add `FLAG_SECURE` to your Activity window.

### 5. Code
**Preventing Screenshots and Recents leakage:**
```kotlin
class SecureActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Prevent screenshots, screen recording, and hide content in Recent Apps
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        
        setContentView(R.layout.activity_secure)
    }
}
```

**Preventing Tapjacking in Jetpack Compose:**
While Compose is generally safer, ensure sensitive buttons don't accept input if another window is drawn on top. (Often handled at the Window level or via `filterTouchesWhenObscured` in legacy Android Views).

### 6. Production usage
- Netflix and DRM video players use `FLAG_SECURE` to prevent piracy via screen recording.
- Banking applications use Play Integrity API (formerly SafetyNet) to verify the device isn't rooted before allowing a login.

### 7. Common mistakes
❌ **Wrong:** Logging network responses in Logcat using `HttpLoggingInterceptor.Level.BODY` in production. Anyone with physical access can plug in a USB cable, open Logcat, and read user passwords/PII.
✅ **Right:** Stripping all logging in release builds using ProGuard/R8, and wrapping Log statements in `if (BuildConfig.DEBUG)`.

❌ **Wrong:** Storing API keys in `gradle.properties` or `strings.xml` and thinking they are safe.
✅ **Right:** Understanding that *any* secret shipped in the client binary can be extracted by reverse engineers. Use short-lived OAuth tokens or backend proxies instead of shipping master API keys.

### 8. Debugging
If a user complains that "The screen is black when I try to cast to my TV", check if you left `FLAG_SECURE` enabled on that screen. `FLAG_SECURE` prevents all screen capturing, including legitimate casting.

### 9. Testing
Use MobSF (Mobile Security Framework), an open-source automated penetration testing tool. You upload your APK, and it flags hardcoded secrets, exported components, and cleartext vulnerabilities.

### 10. Exercise
Add `FLAG_SECURE` to your Expense Tracker's `MainActivity`. Run the app on your emulator and attempt to take a screenshot using the emulator controls. Verify that the system blocks it.

### 11. Deliberate failure
Add a `Log.d("Security", "User password is: $password")` to your login flow. Generate a Release APK. Connect your phone via USB, run `adb logcat | grep Security`, log into the app, and watch the password print to the terminal in plain text.

### 12. Interview questions
- **Q:** How do you securely store a 3rd party API key (like an OpenAI API key) in an Android app so hackers can't extract it?
  - **A:** You **cannot**. Any secret shipped in a client binary can be reverse-engineered (via `strings` command or decompiling the `.so` files). You must move the secret to your own backend server, have the app authenticate with your server, and let your server make the API call to OpenAI.
- **Q:** What is `FLAG_SECURE` and what are its side effects?
  - **A:** It secures the window content preventing screenshots and hiding it in the recents menu. Side effects include breaking screen recording, casting, and taking legitimate screenshots for bug reporting.

### 13. Checkpoint
You understand that the client is public territory, and you know the fundamental OS configurations required to harden the app against local snooping and overlay attacks.


---

## 5. Feature Flags & Remote Configuration

### 1. What is it?
Feature Flags (or Remote Configuration) are remote key-value pairs fetched by the app at runtime to dynamically toggle features, change UI elements, or modify application behavior without requiring an app store update.

### 2. Why does it exist?
Once an Android app is installed on a user's device, you lose control over its code execution. If a newly deployed feature causes crashes or a 3rd party API goes down, you cannot wait hours or days for Google Play to review a hotfix. Feature flags provide an instant "kill switch" to disable broken paths in seconds.

### 3. Mental model
Think of it like a remote-controlled circuit breaker in a house. You (the developer) hold the remote. If an appliance (a new feature) starts smoking, you can hit the remote switch and instantly cut power to that specific outlet without shutting down the whole house or calling an electrician (releasing a new app version).

### 4. How it works
The app queries a remote service (like Firebase Remote Config or LaunchDarkly) on startup or periodically. The service returns a JSON payload of key-value pairs. The app caches these values locally. Before executing a risky code path or showing a new UI, the app checks the cached flag. If `true`, the feature runs; if `false`, it falls back to the old behavior or hides the UI.

### 5. Code
```kotlin
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import kotlinx.coroutines.tasks.await

class FeatureFlagService {
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()

    init {
        val configSettings = FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(3600) // 1 hour cache
            .build()
        remoteConfig.setConfigSettingsAsync(configSettings)
        
        // Offline fallback defaults
        remoteConfig.setDefaultsAsync(mapOf(
            "is_pdf_export_enabled" to false,
            "max_free_transactions" to 50
        ))
    }

    suspend fun fetchAndActivate(): Boolean {
        return try {
            remoteConfig.fetchAndActivate().await()
        } catch (e: Exception) {
            false
        }
    }

    val isPdfExportEnabled: Boolean
        get() = remoteConfig.getBoolean("is_pdf_export_enabled")
}

// Usage in ViewModel
fun onExportClicked() {
    if (featureFlagService.isPdfExportEnabled) {
        startPdfExport()
    } else {
        showError("PDF Export is currently unavailable.")
    }
}
```

### 6. Production usage
- **Kill Switches:** Instantly disabling a faulty payment gateway.
- **Progressive Rollouts:** Enabling a new dashboard layout for 10% of users to monitor crash rates before 100% rollout.
- **A/B Testing:** Showing a green "Subscribe" button to 50% of users and a blue one to the other 50%.
- **Dynamic Limits:** Changing API polling frequency or max allowed cache sizes remotely.

### 7. Common mistakes
- **No offline defaults:** Failing to set default values, leading to crashes or blocked UI if the app starts offline and Remote Config is unreachable.
- **Throttling limits:** Setting the fetch interval to 0 seconds in production. Firebase will throttle the app, causing fetches to fail entirely.
- **Flag bloat:** Leaving dead flags in the codebase for months after a feature is fully rolled out, leading to spaghetti `if/else` checks.

### 8. Debugging
- Lower the minimum fetch interval to 0 *only* during debug builds to see changes instantly.
- Log the result of `fetchAndActivate()` to ensure the network request actually succeeded.
- Use Charles Proxy or Android Studio Network Profiler to verify the payload coming from Firebase.

### 9. Testing
Testing is straightforward since you should always interface feature flags behind an interface or repository.
```kotlin
@Test
fun `when PDF export disabled, show error message`() {
    val mockFlags = mockk<FeatureFlagService>()
    every { mockFlags.isPdfExportEnabled } returns false
    val viewModel = ExpenseViewModel(mockFlags)
    
    viewModel.onExportClicked()
    
    assertEquals("PDF Export is currently unavailable.", viewModel.uiState.value.errorMessage)
}
```

### 10. Exercise
Implement a `RemoteConfigWrapper` that takes an interface of your app's features. Add a new flag `enable_crypto_investments`. Wrap the "Add Investment" screen logic to either show the crypto option or hide it based on this flag. Set the default to `false`.

### 11. Deliberate failure
```kotlin
// BROKEN: Fetches config synchronously on the main thread, risking ANRs, 
// and defaults to true if the network fails.
val isFeatureEnabled = try {
    FirebaseRemoteConfig.getInstance().fetch() // Blocks!
    FirebaseRemoteConfig.getInstance().getBoolean("new_feature")
} catch (e: Exception) {
    true // Fails open! Dangerous for a kill switch.
}
```

### 12. Interview questions
1. *How do you handle a situation where a user opens the app while offline, and you just enabled a critical kill-switch to disable a crashing feature?* (Answer: You can't reach offline users instantly. They rely on the last cached config. This is why you also need in-app update mechanisms or graceful degradation in the code).
2. *Why do we use A/B testing frameworks instead of just hardcoding 50/50 logic with `Math.random()`?* (Answer: Analytics tracking, stickiness so a user doesn't flip-flop between experiences, and remote control over the percentages).

### 13. Checkpoint
You understand that feature flags are your first line of defense in production, buying you time to fix a bug without leaving users with a broken app experience.

---

## 6. Target SDK Version Upgrades & Android OS Evolution

### 1. What is it?
The annual process of updating your application's `compileSdkVersion` and `targetSdkVersion` to comply with Google Play's requirements, ensuring compatibility with the latest Android OS behavioral changes and security restrictions.

### 2. Why does it exist?
Android evolves. Old APIs are deprecated, privacy boundaries are tightened, and battery life optimizations are enforced. Google forces developers to update their `targetSdkVersion` yearly so apps don't stagnate on ancient security models, ensuring a consistent user experience across the ecosystem.

### 3. Mental model
Imagine you run a restaurant inside a mall. Every year, the mall updates its safety and health regulations (Android OS updates). If you don't update your kitchen to meet the new codes by the deadline (target SDK update), mall security boards up your restaurant (Google Play blocks app updates).

### 4. How it works
- `compileSdkVersion`: The version of the Android SDK you compile your code against. It lets you use new APIs but doesn't change how your app runs on older devices.
- `targetSdkVersion`: The version you are explicitly telling the Android OS "I have tested my app against this version, apply the new behavior changes to my app."
- Every August/September, a new Android OS drops. By November of the *following* year, Google Play mandates that all app updates must target the previous year's API level.

### 5. Code
Upgrading usually involves changing build files and adapting to new manifest requirements or runtime permissions.
```kotlin
// build.gradle.kts
android {
    compileSdk = 35 // Android 15

    defaultConfig {
        minSdk = 26     // Android 8.0
        targetSdk = 35  // We are tested against Android 15
    }
}
```

> **[Extension] Note:** `compileSdk`/`targetSdk = 35` is illustrative, not a claim that Android 15 is the newest OS release at the time you're reading this — by the concept's own logic ("every August/September a new Android OS drops"), a newer API level has almost certainly shipped since this was written. Same caveat as the Gradle/AGP version note elsewhere in this course: check the current Google Play target API requirement (developer.android.com/google/play/requirements/target-sdk) rather than assuming the number in this example is still current.
*Example: Android 13 (`targetSdk = 33`) requires explicit POST_NOTIFICATIONS permission.*
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
```

### 6. Production usage
Annual SDK upgrade sprints. Large teams dedicate weeks to this.
- **Android 12 (API 31):** Mandatory `android:exported` on manifest components, new Splash Screen API.
- **Android 13 (API 33):** Granular media permissions (images vs video), Notification runtime permission.
- **Android 14 (API 34):** Exact alarms require user consent, partial photo picker, predictive back gestures.
- **Android 15 (API 35):** Edge-to-edge UI enforced by default, 16KB page size alignment (NDK impact).

### 7. Common mistakes
- **Blindly bumping the number:** Changing `targetSdk` without reading the Android Behavior Changes documentation. The app will compile fine but crash at runtime.
- **Missing `Build.VERSION.SDK_INT` checks:** Using a new API on an older device causing a `NoSuchMethodError`.
- **Ignoring deprecation warnings:** Letting technical debt pile up until an API is completely removed, making a future upgrade a massive rewrite.

### 8. Debugging
- When you bump `targetSdk`, run your entire automated test suite immediately.
- Pay attention to Logcat warnings labeled `Compat framework`. Android will warn you if you are violating a new policy before it crashes your app.
- Test on the newest Android emulator *and* a device running your `minSdk`.

### 9. Testing
Test across the OS spectrum. Use Firebase Test Lab to run your UI tests on Android 8, 10, 12, and 15 simultaneously to ensure backward and forward compatibility.

### 10. Exercise
Review your app's Manifest. If it has `<activity>` or `<receiver>` tags with intent filters, ensure `android:exported="true"` (or false) is explicitly declared. Then, implement the Android 13 runtime permission request for sending a local notification when an expense is saved.

### 11. Deliberate failure
```xml
<!-- BROKEN in Android 12+: Missing android:exported -->
<activity android:name=".MainActivity">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```
*Result: App installation fails with `Installation did not succeed. The application could not be installed: INSTALL_PARSE_FAILED_MANIFEST_MALFORMED`.*

### 12. Interview questions
1. *What is the difference between `compileSdkVersion` and `targetSdkVersion`?* (Answer: Compile SDK determines what APIs you can write code against. Target SDK tells the OS which behavioral changes to apply to your app at runtime.)
2. *If your `minSdk` is 24, how do you safely use an API introduced in API 33?* (Answer: Wrap the call in an `if (Build.VERSION.SDK_INT >= 33)` block, or use AndroidX compat libraries like `ContextCompat` which handle the branching internally.)

### 13. Checkpoint
You understand that Android is a moving target, and proactive annual SDK upgrades are mandatory for app survival in the Play Store.

---

## 7. CI/CD Automation for Mobile (Fastlane & GitHub Actions)

### 1. What is it?
Continuous Integration and Continuous Deployment (CI/CD) for mobile automates the process of building the app, running tests, signing the APK/AAB with cryptographic keys, and uploading it to Google Play, removing human error from the release process.

### 2. Why does it exist?
Manual releases are prone to disaster. A developer might compile a release with debug endpoints, sign it with the wrong keystore, or forget to bump the version code. CI/CD guarantees that every release is built in a sterile, predictable environment.

### 3. Mental model
Think of CI/CD as an automated assembly line in a car factory. The developer supplies the blueprint (code). The assembly line (GitHub Actions) builds the car, crash-tests it (Unit Tests), paints it with the official company logo (Keystore Signing), and loads it onto the delivery truck (Fastlane uploading to Google Play).

### 4. How it works
- **GitHub Actions:** Provides the cloud servers (runners) that execute scripts when you push code.
- **Fastlane:** A Ruby-based tool specifically built for mobile app automation. It orchestrates the complex steps of Android build tools and Play Store APIs.
- The pipeline securely injects the Keystore file and passwords (stored as encrypted secrets in GitHub) to sign the final `App Bundle (.aab)`.

### 5. Code
*Fastfile (Fastlane configuration)*
```ruby
default_platform(:android)

platform :android do
  desc "Submit a new Beta Build to Google Play"
  lane :beta do
    gradle(task: "clean bundleRelease")
    
    # Upload to Play Store Internal Track
    supply(
      track: "internal",
      aab: "app/build/outputs/bundle/release/app-release.aab"
    )
  end
end
```

*GitHub Actions Workflow (`.github/workflows/release.yml`)*
```yaml
name: Android Release
on:
  push:
    tags:
      - 'v*'

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'zulu'
          java-version: '17'
          
      - name: Decode Keystore
        env:
          ENCODED_KEYSTORE: ${{ secrets.KEYSTORE_BASE64 }}
        run: echo $ENCODED_KEYSTORE | base64 -di > app/release.keystore
          
      - name: Setup Fastlane
        uses: ruby/setup-ruby@v1
        with:
          ruby-version: '3.0'
          bundler-cache: true
          
      - name: Run Fastlane Beta
        env:
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: bundle exec fastlane beta
```

### 6. Production usage
- Every PR triggers a workflow that runs Ktlint, Unit Tests, and builds a Debug APK.
- Merging to `main` triggers a nightly build uploaded to Firebase App Distribution for QA.
- Tagging a commit (e.g., `v1.2.0`) triggers the production build lane, which dynamically bumps the `versionCode`, signs the AAB, and uploads it to Google Play's Closed Testing track.

### 7. Common mistakes
- **Committing Keystores to Git:** Hardcoding keystore passwords or committing the `.jks` file directly to the repository instead of using CI Secrets.
- **Manual Versioning:** Forgetting to increment `versionCode` in `build.gradle`, causing Play Store upload rejections. (Best practice: derive `versionCode` from Git commit count or CI build number).
- **Not archiving mapping files:** R8 obfuscates code. If you don't upload the `mapping.txt` to Crashlytics/Play Console during the CI run, production crash logs will be unreadable gibberish.

### 8. Debugging
- CI logs are your best friend. If Fastlane fails on the `supply` step, check if the Google Play API service account JSON key is expired or lacks permissions.
- If the build fails locally but passes on CI (or vice-versa), check for untracked files locally, or mismatched Java/Gradle versions.

### 9. Testing
You don't "unit test" CI/CD, you test it by running it in a sandbox. Create a dummy Android app and set up a full pipeline deploying to Firebase App Distribution before hooking up your real production app.

### 10. Exercise
Create a GitHub Action that runs `./gradlew testDebugUnitTest` on every pull request. Verify that a failing test blocks the PR from being merged.

### 11. Deliberate failure
```gradle
// BROKEN: Hardcoding sensitive data in the codebase
android {
    signingConfigs {
        release {
            storeFile file("my-release-key.keystore")
            storePassword "SuperSecretPassword123" // NEVER DO THIS
            keyAlias "my-key-alias"
            keyPassword "SuperSecretPassword123" // NEVER DO THIS
        }
    }
}
```

### 12. Interview questions
1. *How do you securely handle Android signing keys in a CI environment?* (Answer: Base64 encode the `.jks` file, store it as a secure secret in the CI provider, decode it during the pipeline run, inject passwords via environment variables, and delete the decoded file after the build).
2. *Why do we use Android App Bundles (.aab) instead of APKs for Google Play releases?* (Answer: AABs allow Google Play to generate optimized, device-specific APKs containing only the resources (DPI, language, CPU architecture) that specific device needs, drastically reducing install size.)

### 13. Checkpoint
You can confidently configure an automated pipeline that takes code from a Git push all the way to a signed, obfuscated artifact ready for store submission.

---

## 8. The Production Incident Playbook & Rollback Strategy

### 1. What is it?
A structured, predefined emergency response protocol executed when a critical bug or crash escapes QA and impacts real users in production.

### 2. Why does it exist?
Chaos during an outage leads to poor decisions. When metrics show a 5% crash rate and customer support is flooded with angry reviews, you need a cold, calculated checklist to stop the bleeding, mitigate the damage, and deploy a fix without making things worse.

### 3. Mental model
Think of an incident response like a hospital ER. 
- Triage: Stop the bleeding (Kill Switch).
- Quarantine: Prevent others from getting sick (Halt Staged Rollout).
- Surgery: Fix the underlying issue (Hotfix).
- Vaccination: Ensure it doesn't happen again (Post-Mortem & automated tests).

### 4. How it works
You cannot "rollback" an app on a user's phone. Once they install v2.0, you cannot force their phone to downgrade to v1.9. Therefore, mobile rollbacks are actually "Roll Forwards" — you release v2.0.1 containing the fix or reverting the feature. (Play Console's newer "halt a fully rolled-out release" feature — see the `[Extension]` note under Concept 1 — can stop the bad version from spreading to *more* users, but it still cannot touch devices that already installed it. Roll-forward remains the only fix for users already on the broken build.)

**The 4-Level Response Protocol:**
- **Level 1 (0–5 minutes): Remote Config Kill Switch.** If the crash is isolated to a feature behind a flag, toggle the flag in the Firebase console. The bleeding stops for active users within seconds.
- **Level 2 (5–15 minutes): Halt Staged Rollout.** Go to Google Play Console. If the app is rolling out at 20%, hit "Halt Rollout." This protects the remaining 80% of your user base from downloading the corrupted update.
- **Level 3 (15–60 minutes): Hotfix & Fast-Track.** Create a `hotfix/v2.0.1` branch from the release tag. Revert the broken commit or apply a surgical fix. Bypass normal QA gates, run automated tests, and use CI to deploy directly to the production track.
- **Level 4 (Post-Fix): Force Update.** If the bug resulted in data corruption, utilize Google Play Core's In-App Update API (`AppUpdateType.IMMEDIATE`) to force affected users to download v2.0.1 before they can continue using the app.

### 5. Code
*Triggering an Immediate In-App Update for a Hotfix*
```kotlin
val appUpdateManager = AppUpdateManagerFactory.create(context)
val appUpdateInfoTask = appUpdateManager.appUpdateInfo

appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
    if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        // Check if it's a critical hotfix we flagged remotely
        && remoteConfig.getBoolean("force_immediate_update")
        && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
    ) {
        // updateResultLauncher is an ActivityResultLauncher registered in the Activity
        // (see Concept 1) — the raw request-code overload is deprecated.
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            updateResultLauncher,
            AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build()
        )
    }
}
```

### 6. Production usage
This happens at every major tech company. 
- Example: A null pointer exception on the login screen prevents new users from signing in. 
- Action: On-call engineer gets paged via PagerDuty. Engineer halts Play Store rollout. Engineer identifies the bad commit, reverts it, creates a hotfix PR, gets a mandatory approval, and pushes the new AAB to Google Play.

### 7. Common mistakes
- **Panicking and making sweeping changes:** Attempting a massive refactor during a hotfix. Hotfixes must be surgical, 1-line changes or direct Git reverts to minimize risk.
- **Rolling out 100% immediately:** Releasing the hotfix to 100% of users immediately. The hotfix itself might contain a worse bug. Always staged rollout, even for hotfixes (e.g., 10% -> 100%).
- **Skipping the Post-Mortem:** Fixing the bug but failing to ask *why* the CI pipeline and QA missed it.

### 8. Debugging
- Use Crashlytics to pinpoint the exact line of code failing and identify which App Version and OS Versions are affected.
- Look at "Crash-Free Users" vs "Crash-Free Sessions". A 95% crash-free session rate might mean 100% of users crashed at least once.

### 9. Testing
The ultimate test: **The Fire Drill**. Once a quarter, the engineering team simulates a production outage on a staging environment to ensure everyone knows how to use the Play Console, Fastlane, and Remote Config under pressure.

### 10. Exercise
Write a "5 Whys" post-mortem document for a hypothetical incident where users were double-charged for an expense entry due to a missing debounce on a submit button. Conclude with action items (e.g., write a UI test to click a button rapidly, implement a debounce extension function).

### 11. Deliberate failure
```bash
# BROKEN: Force-pushing a hotfix directly to main and bypassing CI tests
git commit -am "Emergency fix"
git push origin main --force
```
*Result: You bypassed unit tests, broke the build for everyone else, and the fix actually introduced a memory leak.*

### 12. Interview questions
1. *You just deployed an update and Crashlytics alerts you to a 10% crash rate on launch. You cannot use a remote config flag to fix it. Walk me through your exact steps.* (Answer: Halt staged rollout in Play Console. Identify the commit causing the crash. Branch a hotfix from the release tag. Apply a surgical revert. Run automated tests locally. Push hotfix to CI. Deploy to Play Store. Monitor new rollout.)
2. *Why can't you just rollback to the previous APK in the Play Store?* (Answer: Android package manager does not allow downgrading `versionCode` for security reasons. You must increment the version code and deploy a "roll-forward" patch.)

### 13. Checkpoint
You have graduated from simply writing code to architecting robust, fault-tolerant release strategies. You are ready to be an on-call engineer responsible for the stability of a production app.

---

## Phase 13 Project — Release Pipeline & Production Incident Drill

**Goal:** Build a secure release pipeline with remote kill-switches and execute a simulated production incident response.

**Requirements:**
1. **Security Layer:**
   - Implement `SecureStorageRepository` using `EncryptedSharedPreferences` backed by Android Keystore `MasterKey` (or, per the `[Extension]` note in Concept 3, a Proto DataStore + Tink `StreamingAead` implementation if you want practice with the emerging replacement).
   - Protect the Expense Detail screen with `FLAG_SECURE` to prevent screenshots and task switcher previews of sensitive financial data.
2. **Fastlane & Build Automation:**
   - Write a `fastlane/Fastfile` with a `build_release` lane that builds an AAB, signs it using an environment keystore, and generates R8 mapping files.
3. **Remote Kill Switch:**
   - Create a `FeatureFlagService` wrapping Remote Config.
   - Wrap the "Export to PDF" feature in a kill switch (`is_pdf_export_enabled`).
4. **Incident Response Drill:**
   - Simulate a fatal crash in the PDF export feature.
   - Execute the 4-level incident response drill: 1) Toggle remote kill switch, 2) Halt simulated rollout, 3) Write a reproducing unit test, 4) Patch code and bump version code.

---

## Phase 13 Checkpoint

Answer without looking:
1. If a critical bug with a 15% crash rate slips into production and is currently at a 20% staged rollout, what are the exact first three actions you take, in order?
2. What is the difference between an Upload Key and an App Signing Key in Google Play App Signing, and what do you do if your upload key is compromised?
3. How does `EncryptedSharedPreferences` use the Android Keystore system to ensure data at rest is secure even on a rooted device?
4. When would you choose an `Immediate` in-app update over a `Flexible` in-app update?
5. Why is `FLAG_SECURE` critical for banking and financial Android applications?

---

## Final Course Competency Matrix

A comprehensive summary table mapping the entire 13-Phase curriculum across all dimensions:

*(Corrected to match this course's actual 13-phase numbering — an earlier draft of this table had phases 2–9 mislabeled/shifted and was missing Phase 11 entirely; content is unchanged, only the phase numbers/titles below are fixed.)*

| Phase | Core Competencies Mastered | Architecture & Code Artifacts Built | QA / Java Skill Transformed |
|---|---|---|---|
| 1. Kotlin | Null safety, sealed classes & exhaustive `when`, extension functions, data classes | Pure Kotlin Domain Models | From verbose POJOs & boilerplate to concise, safe state representation |
| 2. Coroutines & Flow | `suspend`, structured concurrency, Dispatchers, `StateFlow`/`SharedFlow` | Async data pipelines & reactive Flow chains | From Thread/RxJava callbacks to structured sequential async code |
| 3. Android Platform Fundamentals | Process/Activity lifecycle, Intents, permissions, WorkManager, ADB | Throwaway platform-verification app | From assumption-driven platform code to lifecycle-safe, permission-aware code |
| 4. Jetpack Compose | Recomposition, state hoisting, stability/skipping, side effects | Declarative UI Screens | From XML layouts & `findViewById` to reactive, code-driven UI |
| 5. App Architecture | MVVM/MVI, UDF, layered dependency rules | ViewModels handling UI State | From spaghetti UI manipulation to predictable state machines |
| 6. Dependency Injection | Hilt / Koin, Scopes, Modules, Bindings | DI Graph for repositories | From `new Object()` coupling to interface-driven testable injection |
| 7. Networking | Retrofit, OkHttp, Ktor, Serialization, resilience patterns | REST API Clients | From raw `HttpURLConnection` to type-safe, resilient API clients |
| 8. Local Persistence | Room Database, DataStore, single source of truth | Offline-first persistence layer | From raw SQLite helpers to reactive ORM data streams |
| 9. Navigation | Jetpack Navigation Compose, type-safe routes, Deep Links | App routing graph | From manual `Intent` passing to type-safe graph navigation |
| 10. Testing | JUnit, Turbine, Fakes, Compose UI tests, Coroutine Test Dispatchers | Unit & Integration Test Suites | From QA Blackbox testing to developer-driven Whitebox coverage |
| 11. Gradle & Modularization | Version catalogs, convention plugins, `-api`/`-impl` split | Multi-module architecture refactor | From a slow monolithic build to fast, parallel multi-module compilation |
| 12. Quality, Performance & Observability | Profiler, LeakCanary, Baseline Profiles, R8 | Optimized, observable release build | From unoptimized debug builds to memory-safe, obfuscated, telemetry-instrumented artifacts |
| 13. Release Engineering, Flags & Experiments | Keystore, Fastlane, Feature Flags, Incident Playbook | CI/CD Pipeline & Incident Playbook | From manual file signing to automated, safe deployment processes |

---

## Complete QA Release & Security → Android Production Release Engineering Translation Table

| QA / Release Concept | Android Production Equivalent | Notes |
|---|---|---|
| Staging Environment / Pre-Prod | Google Play Internal & Closed Testing Tracks | Real device distribution testing |
| Canary Deployment (Backend) | Staged Rollout (1% -> 5% -> 20% -> 100%) | Incremental user exposure |
| Feature Flag (LaunchDarkly/Split) | Firebase Remote Config / Custom Feature Flags | Instant remote kill switch |
| SSL / TLS Verification in API tests | OkHttp `CertificatePinner` | Prevents man-in-the-middle attacks |
| Secrets Management (Vault / AWS KMS) | Android Keystore & `EncryptedSharedPreferences` | Hardware-backed cryptographic storage |
| Release Sign-off / Gate Checklist | Google Play Vitals (Crash-free users > 99.5%, ANR < 0.47%) | Objective store quality thresholds |
| Jenkins / CI Release Pipeline | Fastlane (`Fastfile`) + GitHub Actions | Automated mobile signing & Play Store upload |
| Hotfix Deployment | Hotfix branch + Immediate In-App Update | Forces users to patch critical flaws |
