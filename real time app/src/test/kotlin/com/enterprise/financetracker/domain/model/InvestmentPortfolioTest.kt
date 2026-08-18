package com.enterprise.financetracker.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class InvestmentPortfolioTest {

    private val appleHolding = InvestmentHolding(
        ticker = TickerSymbol("AAPL"),
        name = "Apple Inc.",
        shares = 10.0,
        averageBuyPrice = 150.00, // Total Invested: $1500
        currentMarketPrice = 180.00, // Current Value: $1800 -> Unrealized Gain: $300 (20%)
        assetClass = AssetClass.EQUITY
    )

    private val bitcoinHolding = InvestmentHolding(
        ticker = TickerSymbol("BTC"),
        name = "Bitcoin",
        shares = 0.5,
        averageBuyPrice = 60000.00, // Total Invested: $30000
        currentMarketPrice = 50000.00, // Current Value: $25000 -> Unrealized Loss: -$5000 (-16.66%)
        assetClass = AssetClass.CRYPTO
    )

    @Test
    fun given_invalid_ticker_when_instantiating_then_throw_IllegalArgumentException() {
        val exception = assertThrows(IllegalArgumentException::class.java) {
            TickerSymbol("TOOLONGTICKERSYMBOL") // > 10 chars
        }

        assertThat(exception).hasMessageThat().contains("TickerSymbol must be between 1 and 10 alphanumeric characters")
    }

    @Test
    fun given_holdings_when_calculating_portfolio_metrics_then_return_accurate_aggregates() {
        val portfolio = Portfolio(
            id = PortfolioId("port_main"),
            name = "Main Growth Portfolio",
            holdings = listOf(appleHolding, bitcoinHolding)
        )

        // Total Invested: $1,500 + $30,000 = $31,500
        assertThat(portfolio.totalInvestedCapital).isEqualTo(31500.0)

        // Total Market Value: $1,800 + $25,000 = $26,800
        assertThat(portfolio.totalPortfolioValue).isEqualTo(26800.0)

        // Unrealized Gain/Loss: $26,800 - $31,500 = -$4,700
        assertThat(portfolio.totalUnrealizedGainLoss).isEqualTo(-4700.0)
    }

    @Test
    fun given_holding_when_calculating_allocation_then_return_accurate_percentage() {
        val portfolio = Portfolio(
            id = PortfolioId("port_main"),
            name = "Main Growth Portfolio",
            holdings = listOf(appleHolding, bitcoinHolding)
        )

        val appleAllocation = portfolio.calculateAllocation(appleHolding)
        // Apple: 1800 / 26800 ≈ 0.06716 (6.7%)
        assertThat(appleAllocation).isWithin(0.001f).of(0.0671f)

        val btcAllocation = portfolio.calculateAllocation(bitcoinHolding)
        // BTC: 25000 / 26800 ≈ 0.9328 (93.3%)
        assertThat(btcAllocation).isWithin(0.001f).of(0.9328f)
    }
}
