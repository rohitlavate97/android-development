# Debugging Journal & Exercises — Enterprise Finance Tracker

## 🛠️ The Senior Android Debugging Protocol

When diagnosing defects in Android applications, follow the **4-Step Investigative Sequence**:

```
1. Observe Symptom (Crash log, unexpected UI state, failed assertion)
      ↓
2. Formulate Hypothesis (Trace state origin, inspect lifecycle / thread / boundary)
      ↓
3. Isolate & Reproduce (Write failing unit test or trigger via ADB / debugger)
      ↓
4. Fix & Prevent Regression (Apply surgical fix, verify test passes)
```

---

## 🎯 Stage 1 Debugging Challenges

### Challenge 1: The Account ID Transposition Bug
* **Symptom**: User transfers $500 from Account A to Account B, but Account A is credited and Account B is debited.
* **Code Fragment**:
  ```kotlin
  fun transferFunds(sourceAccountId: String, targetAccountId: String, amount: Double)
  ```
* **Question for QA Engineer**: *Why does this compile cleanly even if a developer accidentally calls `transferFunds(targetAccount.id, sourceAccount.id, 500.0)`? How does Kotlin prevent this at compile time?*
* 💡 **Hint 1**: Look at the parameter types. Both are primitive `String`.
* 💡 **Hint 2**: Read ADR 001 on value classes.
* ✅ **Solution**: Use strongly typed value classes: `sourceAccountId: AccountId`, `targetAccountId: AccountId`. Transposing arguments will fail at compile time.

---

### Challenge 2: The Silent Invariant Violation
* **Symptom**: A user enters an expense of `-$50.00` on the transaction form. The net balance calculation gets corrupted and treats it as an income addition.
* **Question for QA Engineer**: *Where should domain validation live to prevent corrupted records from ever existing in memory or persistence?*
* 💡 **Hint 1**: Does validation belong only in the UI form, or in the entity itself?
* 💡 **Hint 2**: Check Kotlin's `init` block and `require()` function.
* ✅ **Solution**: In `Transaction.init`, call `require(amount > 0) { "Transaction amount must be positive" }`.

---

### Challenge 3: The Portfolio Allocation Math Anomaly
* **Symptom**: A user adds 3 stock holdings. The sum of allocation percentages shows `100.00000000000001%` or `99.999999999999%` due to IEEE-754 floating-point inaccuracies.
* **Question for QA Engineer**: *Why is floating-point arithmetic dangerous for financial calculations, and how do we handle it defensively?*
* 💡 **Hint 1**: Standard `Double` cannot accurately represent all decimal fractions in binary base-2.
* 💡 **Hint 2**: Use clamped percentage helpers or integer basis points (bps) / `BigDecimal`.
* ✅ **Solution**: Coerce allocation percentages using `.coerceIn(0f, 1f)` and round currency displays to 2 decimal places using formatters.

---

## 🎯 Stage 2 Debugging Challenges (Android Platform)

### Challenge 4: The Vanishing Form State on Rotation
* **Symptom**: A user types a 50-character transaction note into an input field. When they rotate the phone to landscape mode, the text disappears.
* **Question for QA Engineer**: *What Android lifecycle event occurs during device rotation, and why does in-memory Activity state get wiped?*
* 💡 **Hint 1**: Configuration changes destroy and recreate the Activity by default.
* 💡 **Hint 2**: Where is temporary UI state saved across teardowns?
* ✅ **Solution**: The OS calls `onSaveInstanceState(outState: Bundle)`. Store user inputs in the bundle or in a ViewModel's `SavedStateHandle`.

---

### Challenge 5: The Android 12+ Unexported Activity Crash
* **Symptom**: App crashes on install/launch on Android 12 (API 31+) with `IllegalArgumentException: Targeting S+ (version 31 and above) requires that an explicit value for android:exported be defined when intent filters are present`.
* **Question for QA Engineer**: *Why did Google make `android:exported` mandatory in Android 12, and how do you fix it?*
* 💡 **Hint 1**: Look at `<activity>` tags in `AndroidManifest.xml` that contain `<intent-filter>`.
* 💡 **Hint 2**: Security: If an activity has an intent-filter, should other apps be able to launch it?
* ✅ **Solution**: Explicitly set `android:exported="true"` on the launcher activity, and `android:exported="false"` on internal activities.

---

## 🎯 Stage 3 Debugging Challenges (Jetpack Compose)

### Challenge 6: The Unstable LazyColumn Jitter
* **Symptom**: A user scrolls down a list of 100 transactions. Whenever a new transaction is prepended, the scroll position jumps erratically and every visible item flashes/recomposes.
* **Code Fragment**:
  ```kotlin
  LazyColumn {
      items(transactions) { tx -> TransactionCard(tx) }
  }
  ```
* **Question for QA Engineer**: *Why does Compose recompose every row when a new item is added at index 0 without a key?*
* 💡 **Hint 1**: What is the default identity key used by `LazyColumn` if none is provided?
* 💡 **Hint 2**: Index-based identity vs domain-based identity.
* ✅ **Solution**: Supply stable domain key: `items(items = transactions, key = { it.id.value })`.

---

### Challenge 7: `remember` vs `rememberSaveable` on Rotation
* **Symptom**: A user types a search query in `TransactionListScreen`. When rotating the device, the search query resets to empty string.
* **Code Fragment**:
  ```kotlin
  var searchQuery by remember { mutableStateOf("") }
  ```
* **Question for QA Engineer**: *Why does `remember` survive recomposition but fail across Activity destruction / rotation?*
* 💡 **Hint 1**: `remember` stores values in the Composition slot table in memory.
* 💡 **Hint 2**: Read ADR 006 on saved state.
* ✅ **Solution**: Use `rememberSaveable` which writes the state to the Android `SavedStateRegistry`.
