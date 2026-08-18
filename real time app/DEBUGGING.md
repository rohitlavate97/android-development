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

Try to diagnose and solve these 3 realistic domain defects before checking the solution.

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
