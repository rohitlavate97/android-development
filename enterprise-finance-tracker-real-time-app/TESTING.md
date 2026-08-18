# Testing Strategy — Enterprise Finance Tracker

## 🧪 Developer-Driven Testing vs QA Test Automation

As a software engineer with QA automation experience (Selenium, TestNG, REST Assured), you know how to assert system behavior from the outside. In modern Android engineering, we complement external verification with **White-Box Architecture Testing**:

```
                       ▲
                      / \
                     / E2E\          (Maestro / Appium - Slowest, 5%)
                    /------\
                   / Compose\        (createComposeRule - Fast, 15%)
                  /  UI Tests\
                 /------------\
                /  ViewModel & \     (Turbine + Dispatchers.Test - Instant, 30%)
               /   Flow Tests   \
              /------------------\
             /    Pure Domain     \  (JUnit 5 / Truth - Lightning Fast, 50%)
            /  Entities & UseCases \
           /────────────────────────\
```

---

## 📋 Stage 1 Test Specifications

All domain tests must follow the **Given-When-Then** format:

### Test Suite: `TransactionValidationTest`
1. `given_negative_amount_when_instantiating_transaction_then_throw_IllegalArgumentException`
2. `given_blank_title_when_instantiating_transaction_then_throw_IllegalArgumentException`
3. `given_valid_transaction_when_calculating_net_balance_impact_then_return_correct_signed_amount`

### Test Suite: `InvestmentPortfolioTest`
1. `given_holdings_when_calculating_total_portfolio_value_then_sum_all_current_market_values`
2. `given_holding_when_current_price_drops_then_return_negative_unrealized_profit_and_loss`
3. `given_zero_total_portfolio_value_when_calculating_asset_allocation_then_return_zero_percentage`
