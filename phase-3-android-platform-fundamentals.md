# PHASE 3 — ANDROID PLATFORM FUNDAMENTALS (Week 5)

**Objective:** Know the platform under the framework. Compose hides it, but does not remove it.
**Why this phase matters:** Understanding Android lifecycle, process death, Intents, permissions, and manifest configurations prevents memory leaks, state loss on rotation/backgrounding, and crashes in production.
**Prerequisites:** Phase 1 (Kotlin) and Phase 2 (Coroutines & Flow) complete.
**Project deliverable:** Throwaway platform verification app (permission request, survives rotation, deep link handling, WorkManager job, process death survival).
**Concepts covered:** 14 total, each with the full 13-step teaching sequence.

---

## 1. Process & App Lifecycle

### 1. What is it
The Android OS manages app processes, not the app itself. The OS can kill your app's process at any time when it's in the background to free up RAM. The `Application` class is the global base class that lives as long as the process.

### 2. Why does it exist
Mobile devices have limited memory and battery. Unlike a server JVM (Spring Boot) that runs indefinitely, Android aggressively terminates background apps to keep the active foreground app running smoothly.

### 3. Mental model
Think of your app as a pop-up shop. The landlord (Android OS) lets you operate when customers are there. But if you close the door (go to background) and the landlord needs space for a bigger shop, your shop is instantly bulldozed (Process Death). You must rebuild it when the customer returns.

### 4. How it works
- **Cold Start:** App process doesn't exist. OS creates it, instantiates `Application`, then the first `Activity`. (Slow)
- **Warm Start:** Process exists but Activity was destroyed, or process was kept alive but empty. (Medium)
- **Hot Start:** App is merely brought from background to foreground. (Fast)
- **Process Death (Low Memory Kill - LMK):** OS kills the background process. It saves a small state bundle. When the user returns, a *new* process is created, bypassing standard destruction callbacks.

### 5. Code
```kotlin
class ExpenseTrackerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Called once per process creation.
        // Initialize global singletons, crash reporting, DI graph.
        // DO NOT do heavy main-thread work here, or Cold Starts will lag.
    }
}
```

### 6. Production usage
Initializing DI (Hilt/Koin), Crashlytics, logging frameworks, or fetching feature flags on app launch.

### 7. Common mistakes
❌ **Wrong:** Storing crucial app state in a static variable or `Application` singleton.
```kotlin
object AppState { var selectedExpenseId: String? = null } // Dies during Process Death!
```
✅ **Right:** Passing IDs via Intents/Navigation or persisting to DB/SavedState.

### 8. Debugging
To simulate Process Death (crucial for QA):
1. Put app in background (Home button).
2. Terminal: `adb shell am kill com.your.app.package` (or use Logcat's "Terminate Application" button).
3. Bring app to foreground via Recent Apps.

### 9. Testing
In Appium, you can trigger process kills or use `adb` commands in test setup to verify the app recovers gracefully without losing the user's place.

### 10. Exercise
Create a custom `Application` class, log a message in `onCreate`, and observe it in Logcat during a Cold Start vs Hot Start.

### 11. Deliberate failure
Store a user ID in an `object` singleton. Login, put app in background, simulate process death via ADB, return to app. Watch it crash with NullPointerException when accessing the ID.

### 12. Interview questions
- *Junior:* What is the `Application` class used for?
- *Senior:* How do you simulate and handle Android process death? What happens to static variables?

### 13. Checkpoint
Can you explain why a static variable is not a safe place to store the current user's session token if the app goes to the background?

---

## 2. Activity Lifecycle & State Restoration

### 1. What is it
An `Activity` is a single screen with a UI. Its lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`) dictates its visibility and interaction state. State restoration determines how data survives Configuration Changes (like rotation) and Process Death.

### 2. Why does it exist
To let the OS coordinate hardware resources (camera, sensors) and memory based on what the user is actually looking at.

### 3. Mental model
It's a theater play.
- `onCreate`: Building the set.
- `onStart`: Curtain opens, audience sees the set.
- `onResume`: Actors start speaking (interactive).
- `onPause`: Actors pause (partially visible, e.g., a dialog on top).
- `onStop`: Curtain closes (background).
- `onDestroy`: Set is dismantled.

### 4. How it works
- **Configuration Change (Rotation, Dark Mode):** Activity is destroyed and instantly recreated. `ViewModel` survives this!
- **Process Death:** Process dies. `ViewModel` dies! Only `SavedStateHandle` (a Bundle saved to OS memory) survives.

### 5. Code
```kotlin
class ExpenseViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    // Survives BOTH rotation AND process death!
    val expenseName = savedStateHandle.getStateFlow("EXPENSE_NAME", "")

    fun updateName(name: String) {
        savedStateHandle["EXPENSE_NAME"] = name
    }
}
```

### 6. Production usage
Keeping form input text intact when the user rotates the phone or leaves the app to reply to a text and comes back.

### 7. Common mistakes
❌ **Wrong:** Relying only on `ViewModel` for state. It loses data on process death.
✅ **Right:** Using `SavedStateHandle` in the ViewModel or `rememberSaveable` in Compose for UI state that must survive process death.

### 8. Debugging
- Enable "Don't keep activities" in Developer Options. This instantly destroys activities when navigating away, simulating low-memory situations without killing the whole process.

### 9. Testing
Appium: `driver.rotate(ScreenOrientation.LANDSCAPE)`. Verify inputs aren't cleared.

### 10. Exercise
Write a screen with a text field. Type text, rotate the device. Does it stay? Now put it in background, kill process via adb, and return. Does it stay?

### 11. Deliberate failure
Use standard `remember { mutableStateOf("") }` in Compose. Rotate the phone. The text disappears.

### 12. Interview questions
- *Junior:* What is the difference between `onPause` and `onStop`?
- *Senior:* Compare `ViewModel`, `SavedStateHandle`, and `SharedPreferences`. When do you use each for state?

### 13. Checkpoint
If a user is typing a long expense description, switches to the Camera app (which uses lots of RAM causing process death), and switches back, how do you ensure their drafted text isn't gone?

---

## 3. Single-Activity vs Multi-Activity Architecture

### 1. What is it
**Multi-Activity:** Every screen in the app is a separate `Activity` class. (Legacy)
**Single-Activity:** One `MainActivity` hosts the UI, and swapping screens is done via Fragments or Compose Navigation. (Modern)

### 2. Why does it exist
Activities are heavy OS-level components. Starting an Activity involves IPC (Inter-Process Communication) with the system server. It's slow and makes sharing data between screens cumbersome (requires Serialization/Intents).

### 3. Mental model
Multi-Activity is building a new brick-and-mortar building for every department in a company. Single-Activity is one big building with movable cubicle walls (Composables).

### 4. How it works
In modern Android (Jetpack Compose), `MainActivity` sets the content to a `NavHost`. The NavHost swaps out lightweight Composables based on the current route.

### 5. Code
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseAppTheme {
                val navController = rememberNavController()
                NavHost(navController, startDestination = "home") {
                    composable("home") { HomeScreen(navController) }
                    composable("details") { DetailScreen() }
                }
            }
        }
    }
}
```

### 6. Production usage
99% of new apps use Single-Activity. Multiple Activities are only used for special OS integrations: e.g., a specific Activity for a Share Target, or launching an isolated process.

### 7. Common mistakes
❌ **Wrong:** Creating an `ExpenseDetailActivity` in a Compose app.
✅ **Right:** Creating an `ExpenseDetailScreen` composable and navigating to it.

### 8. Debugging
Use Layout Inspector in Android Studio to see the Compose tree within the single `MainActivity`.

### 9. Testing
In Appium, you previously waited for Activity changes (`driver.currentActivity()`). In Single-Activity, the activity never changes. You must wait for UI elements to appear on screen instead.

### 10. Exercise
Implement a basic 2-screen Compose app with a NavHost. Notice that `MainActivity` is the only Activity in the manifest.

### 11. Deliberate failure
Try to pass a complex, non-serializable Kotlin object between two actual `Activity` classes via Intent extras. It will fail.

### 12. Interview questions
- *Junior:* Why do we prefer Single-Activity architecture with Compose?
- *Senior:* When *would* you explicitly need a second Activity in a modern application?

### 13. Checkpoint
How does Single-Activity change the way you write automated QA tests compared to an app built in 2015?

---

## 4. Intents & Deep Links

### 1. What is it
An `Intent` is a messaging object used to request an action from another app component.
- **Explicit Intent:** Specifies the exact class to start (e.g., `MainActivity`).
- **Implicit Intent:** Declares an *action* (e.g., "view a map"), and the OS finds an app that can handle it.
- **Deep Links:** URLs that click through to a specific screen in your app.

### 2. Why does it exist
To allow apps to communicate with the OS and other apps securely without knowing their internal implementations.

### 3. Mental model
Explicit Intent: Addressing a letter to "John Doe, 123 Main St" (internal app navigation).
Implicit Intent: Addressing a letter to "Any Plumber near me" (sharing text, opening a URL).

### 4. How it works
Deep links are implicit intents. Your app tells the OS via the Manifest: "I can handle `https://expensetracker.com/receipt/*`". When the user clicks that link, the OS offers your app as an option (or opens it directly if verified via App Links).

### 5. Code
```kotlin
// Implicit Intent to share text
val sendIntent = Intent().apply {
    action = Intent.ACTION_SEND
    putExtra(Intent.EXTRA_TEXT, "Look at this expense!")
    type = "text/plain"
}
context.startActivity(Intent.createChooser(sendIntent, "Share via"))
```

### 6. Production usage
Opening a web browser, launching the camera, sending emails, or users clicking an email link that opens the app directly to an expense receipt (Deep Link).

### 7. Common mistakes
❌ **Wrong:** Hardcoding `android:exported="true"` on every Activity. It exposes internal screens to other apps.
✅ **Right:** Only export Activities that need to be launched from outside (like `MainActivity` for the launcher, or activities handling deep links).

### 8. Debugging
Use ADB to test deep links:
`adb shell am start -W -a android.intent.action.VIEW -d "https://expensetracker.com/receipt/123" com.your.app`

### 9. Testing
QA Automation tip: If an activity is `exported="false"`, you *cannot* launch it directly via `adb shell am start`. You must launch the main app and navigate to it, simulating real user flow.

### 10. Exercise
Add an intent filter to your manifest to intercept `expensetracker://open` and parse the URI in `MainActivity`.

### 11. Deliberate failure
Set `android:exported="false"` on an Activity that handles deep links. The OS will throw an `ActivityNotFoundException` or silently fail to route the link.

### 12. Interview questions
- *Junior:* What is the difference between an explicit and implicit intent?
- *Senior:* Explain Android App Links vs Deep Links. How do you prove domain ownership?

### 13. Checkpoint
If you want users to share a receipt PDF to your app from their File Manager, what kind of Intent are you dealing with?

---

## 5. `AndroidManifest.xml` Anatomy

### 1. What is it
The control center of your app. It declares to the Android OS what your app contains (Activities, Services, Receivers), what permissions it needs, and hardware requirements.

### 2. Why does it exist
The OS needs to know the app's structure *before* executing any code. It dictates app icon, theme, entry points, and security constraints.

### 3. Mental model
It's the app's Passport and Customs Declaration form. It tells the OS who you are, what you're bringing in, and what access you need.

### 4. How it works
Every app component must be declared here. In Android 11+, package visibility is restricted; if your app needs to know about other apps installed on the phone, you must declare them in the `<queries>` block.

### 5. Code
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Permissions -->
    <uses-permission android:name="android.permission.INTERNET" />
    
    <!-- Package visibility (Android 11+) -->
    <queries>
        <package android:name="com.google.android.youtube" />
    </queries>

    <application
        android:name=".ExpenseTrackerApp"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name">
        
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <!-- Launcher Intent Filter -->
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 6. Production usage
Managing versions, requesting `INTERNET` access (required for almost everything), declaring the `Application` class, configuring cleartext traffic rules.

### 7. Common mistakes
❌ **Wrong:** Forgetting to declare a new Activity in the manifest. (Crash on launch: `ActivityNotFoundException`).
✅ **Right:** Understanding that Compose mitigates this by using one Activity.

### 8. Debugging
When checking merged manifests (since libraries add their own manifest entries), open `AndroidManifest.xml` in Android Studio and click the "Merged Manifest" tab at the bottom to see the final output.

### 9. Testing
QA checks: Ensuring `android:debuggable="false"` in release builds, verifying proper permissions are declared so Appium can grant them via capabilities.

### 10. Exercise
Create a new Activity class but don't add it to the Manifest. Try to launch it via an Intent. Observe the crash.

### 11. Deliberate failure
Remove the `INTERNET` permission and attempt to make a Retrofit network call. Watch the `SecurityException`.

### 12. Interview questions
- *Junior:* What is the purpose of the Android Manifest?
- *Senior:* How does the Merged Manifest resolve conflicts between your app and third-party SDKs?

### 13. Checkpoint
If you integrate a third-party crash reporting library, how does it initialize itself without you adding code to `onCreate`? (Hint: It uses a `ContentProvider` declared in its own manifest that merges into yours).

---

## 6. Resources & Qualifiers

### 1. What is it
Android separates static assets (strings, colors, layouts, drawables) from code into the `res/` directory. Qualifiers (e.g., `-night`, `-es`, `-xxhdpi`) allow the OS to automatically load different resources based on device state.

### 2. Why does it exist
To support thousands of different Android devices (tablets, phones, TVs) and user configurations (languages, dark mode) without writing massive `if/else` blocks in code.

### 3. Mental model
It's an automated vending machine. You ask for "String: greeting", and based on the user's settings (Spanish, Dark Mode), the OS routes your request to `values-es/strings.xml` and hands you "Hola".

### 4. How it works
Resource folders are appended with qualifiers. 
- `drawable-xxhdpi` (high density screens)
- `values-night` (dark mode colors)
- `values-w600dp` (tablets / wide screens)
The OS finds the most specific match at runtime.

### 5. Code
`res/values/strings.xml`
```xml
<string name="welcome_user">Welcome, %1$s!</string>
<plurals name="expense_count">
    <item quantity="one">%d expense</item>
    <item quantity="other">%d expenses</item>
</plurals>
```
Kotlin:
```kotlin
val text = stringResource(R.string.welcome_user, "Rohit")
val pluralText = pluralStringResource(R.plurals.expense_count, 5, 5)
```

### 6. Production usage
Internationalization (i18n), handling singular/plural text correctly, providing vector graphics that scale cleanly, and seamless dark mode support.

### 7. Common mistakes
❌ **Wrong:** Hardcoding strings in UI code (`Text("Welcome")`).
✅ **Right:** Always using `stringResource(R.string.welcome)`.

### 8. Debugging
If the wrong string or image is loading, check the hierarchy of qualifiers. Android prioritizes Locale > Screen Size > Density.

### 9. Testing
Appium: `driver.setSetting("locale", "es-ES")`. Verify the UI strings change correctly.

### 10. Exercise
Create `values-es/strings.xml` and translate your app name. Change your emulator's language to Spanish and see the app name change on the home screen.

### 11. Deliberate failure
Use string concatenation for translations: `Text("You have " + count + " expenses")`. In some languages, grammar requires the count at the *end* of the sentence. Concatenation breaks i18n completely.

### 12. Interview questions
- *Junior:* How do you support Dark Mode in Android?
- *Senior:* Explain the priority order of Resource Qualifiers. What happens if a specific string is missing in `values-es`?

### 13. Checkpoint
Why must you use `<plurals>` instead of an `if (count == 1)` check for language translations?

---

## 7. Runtime Permissions

### 1. What is it
Dangerous permissions (Camera, Location, Contacts, Notifications) must be explicitly granted by the user via a system dialog at runtime, not just declared in the Manifest.

### 2. Why does it exist
User privacy. Before Android 6 (Marshmallow), all permissions were granted at install time, which was a massive security flaw.

### 3. Mental model
Manifest declaration: "I intend to use the camera."
Runtime permission: A bouncer actually stopping you at the door and asking the user, "Do you want to let this app use the camera right now?"

### 4. How it works
1. Declare in Manifest.
2. Check if already granted (`ContextCompat.checkSelfPermission`).
3. If not, request it via `ActivityResultLauncher`.
4. Handle the result. If denied twice (or permanently), you must show a rationale and direct them to app settings.
*Note: Android 13+ requires `POST_NOTIFICATIONS` permission.*

### 5. Code
```kotlin
val requestPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) {
        // Access Camera
    } else {
        // Show rationale / fallback UI
    }
}

Button(onClick = { 
    requestPermissionLauncher.launch(Manifest.permission.CAMERA) 
}) {
    Text("Open Camera")
}
```

### 6. Production usage
Uploading receipt images (Camera/Media), tracking location of purchases, sending push notifications for due bills.

### 7. Common mistakes
❌ **Wrong:** Requesting all permissions immediately on app launch (users will reject and uninstall).
✅ **Right:** Requesting permissions *in context* (e.g., asking for Camera only when they click "Scan Receipt").

### 8. Debugging
Settings -> Apps -> Your App -> Permissions. You can toggle them manually here to test the "already granted" vs "denied" states. 
Terminal: `adb shell pm grant com.your.app android.permission.CAMERA`

### 9. Testing
In Appium, capability `autoGrantPermissions = true` bypasses dialogs for fast E2E runs. For explicit permission testing, use Appium to locate and click the system dialog buttons (`id: com.android.permissioncontroller:id/permission_allow_button`).

### 10. Exercise
Implement the Android 13 `POST_NOTIFICATIONS` permission request flow in your app when the user clicks a "Enable Alerts" button.

### 11. Deliberate failure
Try to start the Camera intent without checking/requesting runtime permission. Watch the app crash with a `SecurityException`.

### 12. Interview questions
- *Junior:* What is the difference between normal and dangerous permissions?
- *Senior:* Explain the "Rationale" flow. How do you handle a user who has permanently denied a permission?

### 13. Checkpoint
If your QA script is failing because a permission popup blocks the UI on a fresh install, what Appium capability or ADB command can you use to solve it?

---
*End of Phase 3, Part 1.*


---

## 8. Background Execution & WorkManager

### 1. What is it
`WorkManager` is Android's recommended library for executing deferrable, guaranteed background work, meaning work that doesn't need to happen immediately but *must* happen eventually, even if the app is killed or the device restarts.

### 2. Why does it exist
To save battery, Android introduced aggressive background restrictions over the years (Doze mode, App Standby, background service limits). You can no longer just spin up a background thread or service and expect it to run indefinitely. `WorkManager` abstracts away these OS-level restrictions and uses the correct underlying job scheduler based on the API level.

### 3. Mental model
Think of it like a **reliable cron job or task queue** given directly to the operating system. You say, "Hey Android, when the device is charging and on Wi-Fi, upload this log file." The OS says, "Got it, I'll handle it, even if you crash."

### 4. How it works
You define a `Worker` class containing the task. You create a `WorkRequest` (either `OneTimeWorkRequest` or `PeriodicWorkRequest`) and attach constraints (e.g., NetworkType.UNMETERED). You enqueue it. WorkManager saves the request in a local Room database, ensuring it survives process death and reboots.

### 5. Code
```kotlin
import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 1. Define the CoroutineWorker
class SyncTransactionsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // e.g., val api = NetworkClient.api
            // api.syncTransactions()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) {
                Result.retry() // Will use exponential backoff
            } else {
                Result.failure()
            }
        }
    }
}

// 2. Enqueue the work in an Activity or Application
fun scheduleSync(context: Context) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi
        .setRequiresBatteryNotLow(true)
        .build()

    val syncRequest = PeriodicWorkRequestBuilder<SyncTransactionsWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
        .setConstraints(constraints)
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            WorkRequest.MIN_BACKOFF_MILLIS,
            java.util.concurrent.TimeUnit.MILLISECONDS
        )
        .build()

    WorkManager.getInstance(context).enqueueUniquePeriodicWork(
        "SyncTransactions",
        ExistingPeriodicWorkPolicy.KEEP,
        syncRequest
    )
}
```

### 6. Production usage
- Syncing local database transactions to the server periodically.
- Uploading large images or log files.
- Periodically fetching configuration updates or feature flags.

### 7. Common mistakes
- **Wrong tool:** Using `WorkManager` for immediate UI updates (like fetching data for the screen the user is currently looking at). Use Kotlin Coroutines in `viewModelScope` for that.
- **Ignoring process death:** Storing state in singletons/memory and expecting the `Worker` to access it. Workers start fresh; they must load state from persistent storage or input data.

### 8. Debugging
- Use the **App Inspection** tool in Android Studio -> **Background Task Inspector** tab to view enqueued, running, and failed jobs.
- `adb shell dumpsys jobscheduler` to see OS-level job constraints.

### 9. Testing
- Use `WorkManagerTestInitHelper` from `androidx.work:work-testing`.
- `TestListenableWorkerBuilder` lets you run a `Worker` directly in a unit test to verify `Result.success()`.

### 10. Exercise
Write a `OneTimeWorkRequest` that simulates a database backup, requiring the device to be charging. Pass a string filename as input data to the worker.

### 11. Deliberate failure
Try calling a suspending network request inside a standard `Worker` (not `CoroutineWorker`). Observe how it blocks the thread and how handling concurrency is messy compared to `CoroutineWorker`.

### 12. Interview questions
- *Q: What's the difference between Coroutines and WorkManager?* A: Coroutines are for concurrency within the app's current lifecycle. WorkManager is for guaranteed execution independent of the app's lifecycle.
- *Q: How does WorkManager handle app force stops?* A: If the user explicitly "Force Stops" the app from settings, all alarms and WorkManager jobs are cancelled by the OS until the user launches the app again.

### 13. Checkpoint
You understand that `WorkManager` is for guaranteed deferrable work, while `viewModelScope` coroutines are for immediate, UI-bound work.

---

## 9. Notifications & Notification Channels

### 1. What is it
A system-level UI element outside your app to inform users of events.

### 2. Why does it exist
To pull users back into the app or alert them to critical background events (e.g., a message received, an expense limit reached).

### 3. Mental model
Think of notifications like **OS-managed sticky notes**. You write the note and hand it to the OS `NotificationManager`. The OS decides how and when to show it based on user preferences and Notification Channels.

### 4. How it works
You use `NotificationCompat.Builder` to construct the UI. Starting in Android 8.0 (API 26), all notifications must be assigned to a `NotificationChannel` (e.g., "Marketing", "Alerts"). Starting in Android 13 (API 33), you must explicitly request the `POST_NOTIFICATIONS` runtime permission.

### 5. Code
```kotlin
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

fun showExpenseAlertNotification(context: Context, amount: Double) {
    val channelId = "budget_alerts"
    
    // 1. Create Channel (Required for API 26+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId,
            "Budget Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when you exceed your budget"
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    // 2. Check Permission (Required for API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return // Handle permission request gracefully in UI, don't crash
    }

    // 3. Create PendingIntent for tap action
    val intent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    }
    val pendingIntent: PendingIntent = PendingIntent.getActivity(
        context, 0, intent, PendingIntent.FLAG_IMMUTABLE
    )

    // 4. Build and Show
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(R.drawable.ic_alert) // Always use a transparent white icon
        .setContentTitle("Budget Exceeded!")
        .setContentText("You spent $$amount, exceeding your daily limit.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)

    NotificationManagerCompat.from(context).notify(1001, builder.build())
}
```

### 6. Production usage
- Chat apps for new messages.
- Banking apps for transaction alerts.
- Media players using foreground service notifications.

### 7. Common mistakes
- **Missing `FLAG_IMMUTABLE`:** `PendingIntent` requires mutability flags on modern Android. App will crash if omitted.
- **Wrong icon format:** Using a colored or complex SVG as `setSmallIcon`. The OS masks it, resulting in a solid gray square. Small icons must be alpha-only (transparent and white).

### 8. Debugging
- Check device Settings -> Apps -> Your App -> Notifications to see if a specific channel was muted by the user (or during testing).
- `adb shell dumpsys notification` to see active notifications and channel configurations.

### 9. Testing
- UI Automator can interact with the system notification shade.
- For unit testing, wrap `NotificationManager` in an interface and assert that the correct methods are called.

### 10. Exercise
Create a notification with an "Action Button" (e.g., "Dismiss" or "Mark as Read") that triggers a `BroadcastReceiver`.

### 11. Deliberate failure
Post a notification on Android 13+ without declaring `POST_NOTIFICATIONS` in the Manifest or requesting it at runtime. Observe the silent failure or crash depending on how it's called.

### 12. Interview questions
- *Q: Why did Android introduce Notification Channels?* A: To give users granular control. Instead of blocking *all* notifications from an app, a user can block "Promotions" but keep "Direct Messages".
- *Q: What is a `PendingIntent`?* A: A token you give to a foreign application (like the OS Notification Manager) allowing it to execute an `Intent` on your app's behalf, with your app's permissions.

### 13. Checkpoint
You know that targeting Android 13 requires runtime permissions, Android 8+ requires channels, and all tap actions require a `PendingIntent`.

---

## 10. `Context` Leakage & Lifecycles

### 1. What is it
`Context` is an abstract class providing access to Android system services, resources, and app environment. "Leaking" it means holding a reference to a short-lived `Context` (like an Activity) in a long-lived object, preventing garbage collection.

### 2. Why does it exist
Android apps don't have a single `main()` entry point. Activities, Services, and Receivers are instantiated by the OS. `Context` provides the linkage back to the OS environment.

### 3. Mental model
Think of `Context` as an **ID badge**. 
- The `Application` context is a visitor badge valid for the whole building (app lifespan).
- The `Activity` context is a VIP backstage pass valid only for one specific concert (the UI screen).
If you give your backstage pass to an accountant (Singleton) who files it away permanently, the system can never tear down the concert stage (Memory Leak).

### 4. How it works
When the screen rotates, Android destroys the `Activity` and recreates it. If a Singleton, `ViewModel`, or background Coroutine holds a reference to the old `Activity` instance (or a View attached to it), the Garbage Collector cannot reclaim the heavy Activity object (including all its views and bitmaps), causing OutOfMemory (OOM) crashes.

### 5. Code
```kotlin
// THE WRONG WAY (Memory Leak)
object DatabaseManager {
    var context: Context? = null // NEVER HOLD AN ACTIVITY CONTEXT IN A SINGLETON
    
    fun init(c: Context) {
        context = c
    }
}

// IN ACTIVITY
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    DatabaseManager.init(this) // 'this' is the Activity Context. LEAK!
}

// THE RIGHT WAY
object DatabaseManager {
    var appContext: Context? = null
    
    fun init(c: Context) {
        // applicationContext is safe to hold globally
        appContext = c.applicationContext 
    }
}
```

### 6. Production usage
- Passing `Context` to Repositories to access Room or DataStore (must use Application Context).
- Passing `Context` to UI inflation/dialogs (must use Activity Context for correct themes).

### 7. Common mistakes
- Passing `Context` into a `ViewModel`. **Rule:** ViewModels should NEVER contain `android.*` imports (except `AndroidViewModel`, which takes Application context).
- Passing a `View` (which inherently holds an Activity Context) into a long-running coroutine or Singleton.

### 8. Debugging
- **LeakCanary:** The gold standard library for finding memory leaks in debug builds.
- Android Studio Profiler: Take a heap dump, filter by Activity name, and see if multiple instances exist after rotating the device.

### 9. Testing
- Automated UI tests that rotate the screen multiple times while monitoring heap usage.

### 10. Exercise
Create a static object and pass `this` from an Activity. Rotate the screen 5 times. Take a Heap Dump in Android Studio and find the 5 leaked Activity instances.

### 11. Deliberate failure
Write a Coroutine using `GlobalScope.launch` inside an Activity `onCreate`, make it `delay(10000)`. Rotate the screen. Note that the old coroutine is still running and holds a reference to the destroyed Activity.

### 12. Interview questions
- *Q: What is the difference between Application Context and Activity Context?* A: Application context is tied to the app process lifecycle; use it for singletons, databases. Activity context is tied to UI lifecycle and holds theme info; use it for Views, Dialogs, and layout inflation.
- *Q: How do you access string resources inside a `ViewModel` without a Context?* A: You don't. The `ViewModel` should emit data/state, and the UI layer (Activity/Fragment/Compose) should read that state and resolve the string resource using its own `Context`.

### 13. Checkpoint
You understand that `Context` is the bridge to the OS, and holding UI contexts in background/global scope causes severe memory leaks.

---

## 11. Local Storage Architecture

### 1. What is it
Android's file system for apps. It includes internal storage (private), external storage (public/shared), `SharedPreferences` (legacy key-value), and Jetpack DataStore (modern key-value/proto).

### 2. Why does it exist
Apps need to persist state between launches, but Android must isolate app data for security while allowing controlled sharing of media files.

### 3. Mental model
- **Internal Storage (`filesDir`):** Your private diary. No one else can read it. Wiped when uninstalled.
- **External Storage / Scoped Storage:** The public library. You need permission to put books there or read others' books (Images, Downloads).
- **DataStore:** A fast, asynchronous filing cabinet for settings and flags.

### 4. How it works
- **Internal:** Direct File I/O via `context.filesDir`.
- **Scoped Storage (API 29+):** Apps only have access to their own app-specific directory on external storage. To access public media, you use `MediaStore` API. To pick generic files, you use Storage Access Framework (SAF - system file picker).
- **DataStore (Preferences):** Built on Kotlin Coroutines/Flow, replaces `SharedPreferences`. It performs asynchronous, transactional updates, avoiding the blocking UI thread ANRs (Application Not Responding) that `SharedPreferences.apply()` was notorious for.

### 5. Code
```kotlin
import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// 1. Setup DataStore at the top level
val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    
    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")

    // 2. Read as a Flow (Reactive)
    val isDarkMode: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[DARK_MODE_KEY] ?: false
        }

    // 3. Write via Coroutine (Transactional)
    suspend fun setDarkMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DARK_MODE_KEY] = enabled
        }
    }
}
```

### 6. Production usage
- Internal storage: Caching downloaded JSON, storing Room databases.
- DataStore: User preferences, "has_seen_onboarding" flags, session tokens.
- SAF: Letting users export database backups to their Google Drive or local Downloads folder.

### 7. Common mistakes
- **Using `SharedPreferences` in modern apps:** It's legacy. `commit()` blocks the thread. `apply()` is async but can pause the main thread during Activity pauses, causing ANRs.
- **Requesting `READ_EXTERNAL_STORAGE` blindly:** On Android 13+, this permission is deprecated for media. You must use `READ_MEDIA_IMAGES`, etc., or better, use the Photo Picker which requires no permissions.

### 8. Debugging
- Android Studio **Device File Explorer**: View internal app files directly (`data/data/com.your.package`).
- Use `adb shell run-as com.your.package ls -l files/` to browse internal storage without root.

### 9. Testing
- For DataStore, pass a `PreferenceDataStoreFactory` created with a temporary file in unit tests.

### 10. Exercise
Migrate a legacy `SharedPreferences` implementation to Jetpack Preferences DataStore.

### 11. Deliberate failure
Try to read a file from the root of `/sdcard/` on an Android 13 emulator without SAF. Watch the `SecurityException`.

### 12. Interview questions
- *Q: Why is Jetpack DataStore better than SharedPreferences?* A: DataStore is fully asynchronous using Coroutines/Flow, guaranteeing thread safety and non-blocking I/O, whereas SharedPreferences is synchronous and prone to ANRs.
- *Q: What is Scoped Storage?* A: A privacy feature introduced in Android 10 that restricts apps to only access their own private directories and explicit media types, preventing rogue apps from scraping external storage.

### 13. Checkpoint
You understand the differences between internal/external storage and why DataStore > SharedPreferences.

---

## 12. Android API Levels & Compatibility

### 1. What is it
The versioning system of the Android OS. Represented by integer SDK (Software Development Kit) levels (e.g., API 33 is Android 13).

### 2. Why does it exist
Android devices are heavily fragmented. Your app must run on old phones without crashing when calling new APIs, while also supporting new features on new phones.

### 3. Mental model
- `minSdk`: The oldest OS your app will install on. (The bouncer at the door).
- `targetSdk`: The OS version you designed and tested the app for. (The rules of the club).
- `compileSdk`: The latest SDK tools used to build the app. (The construction equipment).

### 4. How it works
If `targetSdk = 33`, Android 13 will apply Android 13 rules (like needing POST_NOTIFICATIONS permission). If `targetSdk = 31` but runs on an Android 13 device, the device runs your app in a "compatibility mode," applying older rules so your app doesn't break. 
Because `compileSdk` gives you access to all new classes, you must manually check `Build.VERSION.SDK_INT` before calling APIs that don't exist on `minSdk`.

### 5. Code
```kotlin
import android.os.Build

fun setupVibration(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    // API branching
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // API 26+
        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
    } else { // Legacy
        @Suppress("DEPRECATION")
        vibrator.vibrate(50)
    }
}
```

### 6. Production usage
Handling edge cases for new OS restrictions (e.g., Bluetooth permissions changed heavily in Android 12).

### 7. Common mistakes
- **Ignoring lint warnings:** Android Studio will underline new APIs in red if you call them without an SDK check. Ignoring this results in a `NoSuchMethodError` crash on older devices.
- **Not updating targetSdk:** Google Play requires apps to update their `targetSdk` within one year of a new Android release, or the app is hidden from the store.

### 8. Debugging
- Create Emulators targeting your `minSdk` and your `targetSdk` to verify behavior at both extremes.

### 9. Testing
- You can mock `Build.VERSION.SDK_INT` using reflection or libraries like Robolectric to test branching logic.

### 10. Exercise
Write a function that requests exact alarms. Note how Android 12 (API 31) handles `SCHEDULE_EXACT_ALARM` vs Android 14 (API 34) which denies it by default.

### 11. Deliberate failure
Set your `minSdk` to 24. Call a method only available in API 33 without an `if` check. Run it on an API 24 emulator. Observe the runtime crash.

### 12. Interview questions
- *Q: What does `targetSdk` actually do at runtime?* A: It tells the OS which behavioral changes to apply. It opts your app into new security and lifecycle restrictions.
- *Q: What is API Desugaring?* A: A toolchain process that allows you to use newer Java 8+ APIs (like `java.time.LocalDate`) on older Android devices that don't natively support them by rewriting the bytecode at compile time.

### 13. Checkpoint
You can explain `minSdk` vs `targetSdk` vs `compileSdk` to a junior developer.

---

## 13. Build Artifacts & Packaging

### 1. What is it
How your Kotlin code, XML, and images are compiled and zipped up for distribution via APK (Android Package) or AAB (Android App Bundle).

### 2. Why does it exist
Android devices have different CPU architectures (ARM, x86) and screen densities. Shipping all resources to all devices wastes user bandwidth and storage.

### 3. Mental model
- **APK:** The old way. A giant heavy suitcase containing everything for every climate.
- **AAB:** The modern way. A customized wardrobe. Google Play takes the AAB, looks at the downloading user's specific phone, and builds a customized mini-APK (Split APK) containing *only* what that phone needs.

### 4. How it works
The Gradle build process compiles Kotlin to `.class`, converts it to Dalvik Executable (`.dex`), compiles resources, and zips it. 
**R8 / ProGuard** runs during the release build to:
- **Shrink:** Remove unused code.
- **Optimize:** Inline functions, rewrite loops.
- **Obfuscate:** Rename `TransactionManager` to `a.b.c` to deter reverse engineering.

### 5. Code
`build.gradle.kts` (App module)
```kotlin
android {
    buildTypes {
        release {
            isMinifyEnabled = true // Turns on R8
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // signingConfig = signingConfigs.getByName("release")
        }
    }
}
```
`proguard-rules.pro`
```pro
# Tell R8 NOT to obfuscate your data models if used with Gson/Retrofit reflection
-keep class com.expensetracker.models.** { *; }
```

### 6. Production usage
- AABs are mandatory for new Google Play Store uploads.
- Play App Signing is used where Google holds your master release key, and you upload with an "upload key".

### 7. Common mistakes
- **Crashing in Release, fine in Debug:** Usually caused by R8 obfuscating a class that is accessed via reflection (like JSON serialization or Room entities). Solution: Add `@Keep` annotation or Proguard rules.
- **Losing the keystore file:** If you manage your own signing key and lose it, you can *never* update that app again.

### 8. Debugging
- Android Studio -> **Build -> Analyze APK**. Lets you see the size breakdown of DEX files and resources, and view the obfuscated bytecode.
- Use `mapping.txt` outputted by R8 to de-obfuscate release crash logs from Firebase Crashlytics.

### 9. Testing
- Always test a `release` build locally before uploading to Play Store to catch R8/obfuscation issues.

### 10. Exercise
Enable `isMinifyEnabled = true` on the `debug` build type. Run the app. Check if any JSON parsing breaks.

### 11. Deliberate failure
Create a data class for a Retrofit API response. Enable Minify. Do not use `@Keep` or ProGuard rules. Attempt to make the API call in the release build. Watch Gson/Moshi fail to parse because the variable names were renamed to `a`, `b`, `c`.

### 12. Interview questions
- *Q: What is the difference between APK and AAB?* A: APK is an installable binary. AAB is a publishing format that Google Play uses to generate device-optimized Split APKs, reducing download size.
- *Q: Why do apps crash in release but not debug?* A: Code shrinking (R8/ProGuard) stripped out code it thought was unused, or obfuscation broke reflection-based libraries.

### 13. Checkpoint
You understand AABs, R8 obfuscation, and why `@Keep` is necessary for reflection.

---

## 14. ADB & Platform Debugging Mastery

### 1. What is it
ADB (Android Debug Bridge) is a command-line tool that lets you communicate with a device.

### 2. Why does it exist
GUI tools (like Android Studio) are great, but slow and sometimes abstract away raw system data. ADB provides low-level scripting, debugging, and automation capabilities.

### 3. Mental model
ADB is an **SSH connection** to the Android OS matrix. You are Neo seeing the green falling code.

### 4. How it works
A client runs on your PC, a daemon runs on the Android device, and a server manages the connection via USB or Wi-Fi.

### 5. Code (Terminal Commands)
```bash
# View logs from a specific package, formatted nicely
adb logcat -v time --pid=$(adb shell pidof -s com.expensetracker)

# Clear app data (like clicking "Clear Data" in Settings)
adb shell pm clear com.expensetracker

# Kill the app process (simulate OS Low Memory Killer)
adb shell am kill com.expensetracker

# Force stop the app completely
adb shell am force-stop com.expensetracker

# Start an Activity directly
adb shell am start -n com.expensetracker/.MainActivity

# Trigger a Deep Link
adb shell am start -W -a android.intent.action.VIEW -d "https://expensetracker.com/settings" com.expensetracker

# Grant a permission without UI interaction
adb shell pm grant com.expensetracker android.permission.POST_NOTIFICATIONS
```

### 6. Production usage
- QA automation scripts (Appium runs ADB commands under the hood).
- Quickly resetting app state without clicking through device settings.

### 7. Common mistakes
- Misunderstanding `am kill` vs `am force-stop`. `kill` simulates process death (app is still in recents, `SavedStateHandle` is kept). `force-stop` completely nukes the app (alarms cancelled, state wiped, removed from recents).
- Forgetting `adb kill-server` when the daemon hangs and devices say "Offline".

### 8. Debugging
- `adb bugreport` generates a massive zip file with full system logs and battery history.
- `adb shell dumpsys activity` shows the back stack and current Activity state.

### 9. Testing
- Use ADB in CI/CD pipelines to install APKs and run instrumentation tests.

### 10. Exercise
Use `adb shell am kill` to terminate your app while it's in the background, then bring it back to the foreground from the Recents menu to test `SavedStateHandle`.

### 11. Deliberate failure
Try to use `am start` to launch an Activity that does not have `android:exported="true"` in the Manifest. Observe the `SecurityException`.

### 12. Interview questions
- *Q: How do you simulate Android killing your app for memory without force-stopping it?* A: Press Home to put the app in the background, then run `adb shell am kill <package>`. Open it from Recents.
- *Q: What does `adb shell pm clear` do?* A: It wipes internal storage, databases, DataStore, and cache. It's essentially a fresh install.

### 13. Checkpoint
You can navigate, control, and manipulate an Android app entirely from the command line.

---

## Phase 3 Project — Platform Verification & Process-Death Survival App

**Goal:** Build and verify a throwaway app demonstrating platform fundamentals.

**Requirements:**
1. A single Activity hosting a screen with:
   - A runtime permission request (e.g., `POST_NOTIFICATIONS`) with rationale dialog
   - A counter/text field that survives both screen rotation AND process death using `SavedStateHandle`
   - An explicit intent and an implicit intent (e.g., share text or open URL)
   - Deep link registration in `AndroidManifest.xml` (`https://expensetracker.com/transaction/{id}`)
   - A `WorkManager` background job (`CoroutineWorker`) that runs when connected to unmetered network/charging
2. Test process death survival using `adb shell am kill <package>` or Android Studio's "Terminate Application" button.
3. Verify deep link trigger via `adb shell am start -W -a android.intent.action.VIEW -d "https://expensetracker.com/transaction/123" <package>`.

---

## Phase 3 Checkpoint

Answer without looking:
1. What survives a configuration change (e.g. rotation) vs what survives process death (OS kill) vs what survives force stop?
2. Which storage mechanism corresponds to `ViewModel`, `SavedStateHandle`, `DataStore`/Room?
3. Why does holding an Activity `Context` inside a repository or singleton leak memory?
4. What is the difference between `minSdk`, `targetSdk`, and `compileSdk`?
5. Why would an `adb am start -n com.example.app/.SecretActivity` command fail with `SecurityException: Permission Denial`?
6. When is `WorkManager` the right choice instead of a Coroutine running in `viewModelScope` or `lifecycleScope`?
7. What happens if an app targets Android 13+ and posts a notification without requesting `POST_NOTIFICATIONS`?

---

## Complete QA / Test Automation → Android Platform Translation Table

| QA / Appium / Automation Concept | Android Platform Equivalent | Notes |
|---|---|---|
| `driver.rotate(ScreenOrientation.LANDSCAPE)` | Activity destruction & recreation (`onDestroy` -> `onCreate`) | Tests state retention |
| App killed in background (`driver.terminateApp()`) | Process Death / LMK (Low Memory Killer) | Tests `SavedStateHandle` / persistence |
| `desiredCapabilities.setCapability("appWaitActivity")` | Main launcher activity in `AndroidManifest.xml` | `<intent-filter>` with `ACTION_MAIN` |
| Handling OS Permission Alerts | Runtime Permissions (`ActivityResultLauncher`) | Requires rationale UI + system dialog |
| `driver.get("https://app.link/...")` | Deep Links & Android App Links (`<data>` tag in Manifest) | Handled by Intent filter |
| Push notification verification | Notification Channels + `NotificationManager` | Needs notification channel ID on Android 8+ |
| `adb shell pm clear` | Clears app internal data, `DataStore`, databases, cache | Resets app to clean first-run state |
| Background tasks / cron tests | `WorkManager` / `JobScheduler` | Guaranteed deferrable execution |
