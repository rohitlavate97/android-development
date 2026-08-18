# Enterprise Finance Tracker — User Guide & Manual QA Testing Playbook

This document serves as both the **End-User Guide** on how to navigate and use the application, and the **Comprehensive Manual QA Testing Playbook** containing test cases, edge condition checklists, and bug-reporting templates.

---

# 📱 Part 1: End-User Application Guide

## 1. Authentication & Security
* **Login**: Open the application. Enter your corporate email (e.g. `engineer@enterprise.com`) and secure password (`••••••••`). Tap **Log In**.
* **Biometric Authentication**: If your device supports Fingerprint / Face Unlock, tap the fingerprint icon to authenticate without typing passwords.
* **Session Persistence**: Once logged in, your session remains active. The system uses secure token storage with automatic background token refreshes.

---

## 2. Executive Financial Dashboard
The home screen aggregates your complete financial health:
* **Net Worth Card**: Displays your real-time total net worth, dynamically calculating the sum of your **Liquid Cash** across bank accounts and **Invested Capital** across market portfolios.
* **Holdings Carousel**: Shows your active stock and cryptocurrency holdings (e.g., S&P 500 ETF, Apple, Bitcoin), average buy price, current live price, return percentage (green for gains, red for losses), and asset allocation percentage.
* **Recent Transactions Feed**: Lists the latest 5 transactions. Tap **View All** to navigate to the full transaction list, or tap any individual transaction card to open its detail page.

---

## 3. Transactions & Category Filtering
* **Live Search**: Type in the top search bar (e.g. "Coffee", "Salary", "AWS") to filter transactions instantly in real-time.
* **Category Filter Chips**: Tap category chips (e.g., *All*, *Food*, *Technology*, *Health*, *Salary*) to isolate expenses by department or category.
* **Add Transaction**: Tap the floating action button (**+**) to record a new transaction with custom amount, category, tags, and recurrence flags.

---

## 4. Transaction Detail & Audit View
* **Transaction Metadata**: Displays transaction ID, linked bank account, full timestamp, category badge with color coding, recurrence status, and attached notes.
* **Delete Action**: Tap **Delete Transaction** to remove the entry. The item is removed from the local database immediately and changes propagate throughout all screens.

---

## 5. Spending Trends & Budget Analytics
* **Budget Tracking**: View monthly category spending limits against actual expenditures.
* **Threshold Alerts**: Visual progress bars turn blue for on-track spending and transition to alert red when expenses exceed 100% of the assigned budget.

---

# 🧪 Part 2: Manual QA Testing Playbook & Test Suites

## 📋 QA Test Matrix Overview

| Test Suite | Focus Area | Key Objectives |
|---|---|---|
| **TS-01** | Authentication & Backstack | Verify credential validation, biometrics, and login backstack popping. |
| **TS-02** | Offline-First Resilience (SSOT) | Verify instant local cache rendering and seamless background sync in Airplane mode. |
| **TS-03** | Configuration Changes & Lifecycle | Verify state preservation across device rotations and OS process recreation. |
| **TS-04** | Deep Linking & Synthetic Backstack | Verify direct URL routing and parent navigation up-stack. |
| **TS-05** | Form Validation & Financial Boundaries | Test floating-point edge cases, negative amounts, and special character sanitization. |
| **TS-06** | Performance & Jank Inspection | Verify 60/120 FPS LazyColumn scrolling and memory stability. |

---

## 🔬 Test Suite 1: Authentication & Navigation Backstack

### TC-1.1: Valid Login & Backstack Popping
* **Preconditions**: App freshly installed or user logged out.
* **Steps**:
  1. Launch the application.
  2. Enter valid email: `admin@enterprise.com` and password: `Password123!`.
  3. Tap **Log In**.
  4. Verify the user is redirected to the **Dashboard Screen**.
  5. Press the Android system **Back Button** (or back gesture).
* **Expected Result**: The app **EXITS** to the Android home launcher. The app must **NEVER** navigate back to the Login Screen once logged in (`popUpTo<AuthGraph> { inclusive = true }`).

### TC-1.2: Invalid Credential Validation
* **Steps**:
  1. Enter invalid email without `@`: `invalidemail.com`.
  2. Tap **Log In**.
* **Expected Result**: An inline error message appears: *"Please enter a valid email address"*. The submit button remains disabled or blocks network dispatch.

---

## 🔬 Test Suite 2: Offline-First & Single Source of Truth (SSOT)

### TC-2.1: Airplane Mode Offline Launch
* **Preconditions**: User has previously opened the app and loaded transactions.
* **Steps**:
  1. Turn on **Airplane Mode** (disable Wi-Fi and Mobile Data).
  2. Kill the app process and re-launch.
* **Expected Result**:
  - The app launches instantly without crashing.
  - Dashboard and Transaction List display all previously cached transactions from the Room database in <10ms.
  - No blank screens or unhandled network error dialogs.

### TC-2.2: Seamless Reconnection & Invalidation
* **Steps**:
  1. While still in Airplane Mode, open the app.
  2. Turn off Airplane Mode (reconnect to Internet).
  3. Trigger a sync or pull-to-refresh.
* **Expected Result**: Fresh transactions from the remote server are written into SQLite, and the UI automatically updates via reactive Flow without page flickering.

---

## 🔬 Test Suite 3: Configuration Changes & Process Recreation

### TC-3.1: Device Screen Rotation (State Preservation)
* **Steps**:
  1. Navigate to **TransactionListScreen**.
  2. Type `"Starbucks"` into the search filter bar.
  3. Rotate the device from **Portrait** to **Landscape**.
* **Expected Result**:
  - Search input `"Starbucks"` remains intact in the search bar.
  - Filtered results list remains visible.
  - No flickering, memory leaks, or crashes.

### TC-3.2: Low-Memory OS Process Kill (Don't Keep Activities)
* **Preconditions**: Enable Developer Options -> Check **Don't keep activities**.
* **Steps**:
  1. Navigate to **TransactionDetailScreen** for a specific transaction.
  2. Press the device **Home Button** (sends app to background).
  3. Open 2 other heavy apps (e.g. Camera or YouTube).
  4. Re-open Enterprise Finance Tracker from the Recent Apps switcher.
* **Expected Result**: The app restores to the exact **TransactionDetailScreen** with state intact rather than resetting to the Login screen.

---

## 🔬 Test Suite 4: Production Deep Linking

### TC-4.1: Direct URL Routing via ADB
* **Steps**:
  1. Connect device via USB with ADB enabled.
  2. Execute the following terminal command:
     ```bash
     adb shell am start -a android.intent.action.VIEW \
       -d "https://financetracker.enterprise.com/transactions/tx_100" \
       com.enterprise.financetracker.debug
     ```
* **Expected Result**:
  - The app opens directly to the **Transaction Detail Screen** for `tx_100`.
  - Tap the top-left **Back Arrow (←)**.
  - The app navigates to the **Dashboard Screen** (synthetic backstack verified).

---

## 🔬 Test Suite 5: Financial Boundary & Security Input Testing

### TC-5.1: Extreme Amount Boundaries
* **Test Inputs**:
  - Amount: `$0.00` ➔ Expected: Error *"Amount must be strictly positive"*.
  - Amount: `-$50.00` ➔ Expected: Error *"Negative amounts not permitted"*.
  - Amount: `$0.01` (Minimum valid unit) ➔ Expected: Success.
  - Amount: `$99,999,999.99` (Maximum valid unit) ➔ Expected: Formatted as `$99,999,999.99` without floating-point overflow.

### TC-5.2: SQL & XSS Injection Sanitization
* **Test Inputs in Note / Search Fields**:
  - `' OR 1=1 --`
  - `<script>alert('hack')</script>`
  - `Robert'); DROP TABLE transactions;--`
* **Expected Result**: Inputs are treated strictly as string literals by Room Parameter Binding (`:id`) and Jetpack Compose text rendering without executing malicious payloads.

---

## 🔬 Test Suite 6: Performance & Jank Inspection

### TC-6.1: Fast Scroll / Fling Framerate
* **Steps**:
  1. Open **TransactionListScreen** with 100+ items.
  2. Rapidly fling-scroll up and down continuously for 15 seconds.
  3. Monitor frame drops in Android Studio Profiler or enable **Profile HWUI rendering** (Show GPU Overdraw on device).
* **Expected Result**: UI renders smoothly at **60 FPS / 120 FPS** with zero visual stutter or ANR warnings.

---

# 🐞 Part 3: QA Defect Reporting Template

When discovering a defect during manual testing, log tickets using this standardized format:

```markdown
### [BUG] Short descriptive title (e.g., [Transactions] Screen rotation clears active category filter chip)

**Severity**: High / Medium / Low
**Priority**: P0 (Blocker) / P1 (Critical) / P2 (Normal)
**Build Version**: v1.0.0 (Build 1) — Debug / Release
**Device & OS**: Google Pixel 8 Pro — Android 15 (API 35)

#### Preconditions:
User is authenticated and on the Transaction List screen.

#### Steps to Reproduce:
1. Open the app and navigate to Transactions.
2. Select the "Technology" filter chip.
3. Rotate the device 90 degrees to Landscape mode.

#### Expected Result:
The "Technology" filter chip should remain selected and the list should show only technology items.

#### Actual Result:
The active filter chip resets to "All" and the complete unfiltered list is displayed.

#### Logcat / Stacktrace:
```
(Attach relevant Logcat snippet or screenshot here)
```
```
