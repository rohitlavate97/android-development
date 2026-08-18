# Enterprise Finance Tracker — Local Setup, Build & Run Guide

This guide provides step-by-step instructions to configure your local development environment, build the multi-module project, run automated test suites, and generate signed release artifacts for **Enterprise Finance Tracker**.

---

## 📋 1. Prerequisites & System Requirements

| Tool | Recommended Version | Purpose |
|---|---|---|
| **Operating System** | Windows 10/11, macOS (Apple Silicon/Intel), Linux | Host OS |
| **JDK** | **Java 17** (Azul Zulu / Eclipse Temurin) | Kotlin & Gradle Compilation |
| **Android Studio** | **Ladybug (2024.2.1+)** or higher | Official IDE |
| **Android SDK** | **API 35** (Platform 35, Build-Tools 35.0.0) | Target OS SDK |
| **Android Emulator** | Pixel 8 / 9 AVD with **API 34 or 35** (Google Play Image) | Local testing |

---

## 🛠️ 2. Environment Variables Configuration

Ensure the following environment variables are configured on your operating system:

### Windows (PowerShell)
```powershell
# Set JAVA_HOME to JDK 17
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\zulu17', 'User')

# Set ANDROID_HOME to Android SDK
[System.Environment]::SetEnvironmentVariable('ANDROID_HOME', "$env:LOCALAPPDATA\Android\Sdk", 'User')

# Add platform-tools (adb) to PATH
$currentPath = [System.Environment]::GetEnvironmentVariable('Path', 'User')
[System.Environment]::SetEnvironmentVariable('Path', "$currentPath;$env:LOCALAPPDATA\Android\Sdk\platform-tools", 'User')
```

### macOS / Linux (`~/.zshrc` or `~/.bashrc`)
```bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-17.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools"
```

---

## 📥 3. Opening the Project in Android Studio

1. Launch **Android Studio**.
2. Click **Open** (or `File -> Open`).
3. Select the project directory:
   ```
   D:\Projects\Android Development\enterprise-finance-tracker-real-time-app
   ```
4. Configure the Gradle JDK:
   - Navigate to `File -> Settings` (Windows/Linux) or `Android Studio -> Settings` (macOS).
   - Go to `Build, Execution, Deployment -> Build Tools -> Gradle`.
   - Set **Gradle JDK** to **Java 17 (Zulu 17 or Embedded JDK 17)**.
5. Click **Sync Project with Gradle Files** (elephant icon 🐘).

---

## 🚀 4. Building & Running the Application

### Option A: Via Android Studio (GUI)
1. In the top toolbar, select the **`app`** run configuration.
2. Select your running Android Emulator or connected physical device.
3. Click the green **Run (▶)** button (or press `Shift + F10`).

### Option B: Via Command Line (CLI)

#### 1. Assemble and Install the Debug APK
```bash
# Windows
.\gradlew.bat installDebug

# macOS / Linux
./gradlew installDebug
```

#### 2. Launch the Application via ADB
```bash
adb shell am start -n com.enterprise.financetracker.debug/com.enterprise.financetracker.MainActivity
```

---

## 🧪 5. Running Automated Tests

The application features a comprehensive 70/20/10 test pyramid across all 9 modules.

### Run All Unit & Integration Tests (Parallel Mode)
```bash
# Windows
.\gradlew.bat test --parallel

# macOS / Linux
./gradlew test --parallel
```

### Run Tests for a Specific Module
```bash
# Run Room DAO and persistence tests in :core:database
.\gradlew.bat :core:database:test

# Run MockWebServer networking tests in :core:network
.\gradlew.bat :core:network:test

# Run ViewModel and MVI state tests in :app
.\gradlew.bat :app:test
```

### View Test Reports
After running tests, open the generated HTML test report in your browser:
```
enterprise-finance-tracker-real-time-app/app/build/reports/tests/testDebugUnitTest/index.html
```

---

## 📦 6. Building Production / Release Artifacts

The release build enables **R8 Full-Mode minification**, dead code shrinking, and ProGuard optimization.

### 1. Build Signed / Unsigned Release APK
```bash
# Windows
.\gradlew.bat assembleRelease

# macOS / Linux
./gradlew assembleRelease
```
*Output Location:* `app/build/outputs/apk/release/app-release.apk`

### 2. Build Android App Bundle (AAB for Google Play)
```bash
# Windows
.\gradlew.bat bundleRelease

# macOS / Linux
./gradlew bundleRelease
```
*Output Location:* `app/build/outputs/bundle/release/app-release.aab`

### 3. Release Signing Keystore Configuration (CI/CD)
To sign release builds, provide your keystore credentials via environment variables:
```bash
export KEYSTORE_PATH="/path/to/release.keystore"
export KEYSTORE_PASSWORD="your_keystore_password"
export KEY_ALIAS="finance_tracker_key"
export KEY_PASSWORD="your_key_password"

./gradlew bundleRelease
```

---

## 🔗 7. Testing Deep Links via ADB

Verify production deep link handling without launching a browser:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d "https://financetracker.enterprise.com/transactions/tx_100" \
  com.enterprise.financetracker.debug
```
*Expected Behavior:* The app opens directly to `TransactionDetailDestination` with `transactionId = "tx_100"`, and the back arrow returns to `DashboardDestination`.

---

## ⚡ 8. Baseline Profiles & Macrobenchmarks

To pre-compile ahead-of-time (AOT) DEX profiles to eliminate startup and scroll jank:

```bash
# Generate Baseline Profile rules
.\gradlew.bat :app:generateBaselineProfile
```

---

## ❓ 9. Troubleshooting & FAQ

### Issue 1: `Unsupported class file major version 65 / JDK Mismatch`
* **Cause**: Gradle is running with JDK 21+ while the project expects JDK 17.
* **Fix**: Ensure `Settings -> Build Tools -> Gradle -> Gradle JDK` is set to JDK 17.

### Issue 2: `Cannot find Room schema directory`
* **Cause**: Room KSP compiler expects the schema export directory.
* **Fix**: The schema directory is configured in `build.gradle.kts` under `ksp { arg("room.schemaLocation", "$projectDir/schemas") }`. Gradle creates this automatically on build.

### Issue 3: `Device unauthorized`
* **Cause**: Android phone connected via USB requires RSA key authorization.
* **Fix**: Unlock device screen and tap **Always allow from this computer** on the USB debugging prompt. Run `adb kill-server && adb devices`.
