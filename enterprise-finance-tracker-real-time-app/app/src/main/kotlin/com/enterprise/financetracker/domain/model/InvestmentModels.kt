package com.enterprise.financetracker.domain.model

enum class AssetClass {
    EQUITY,
    CRYPTO,
    COMMODITY,
    FIXED_INCOME,
    REAL_ESTATE
}

data class InvestmentHolding(
    val ticker: TickerSymbol,
    val name: String,
    val shares: Double,
    val averageBuyPrice: Double,
    val currentMarketPrice: Double,
    val assetClass: AssetClass
) {
    init {
        require(shares > 0.0) { "Shares held must be strictly positive: $shares" }
        require(averageBuyPrice > 0.0) { "Average buy price must be strictly positive: $averageBuyPrice" }
        require(currentMarketPrice >= 0.0) { "Current market price cannot be negative: $currentMarketPrice" }
    }

    val totalInvested: Double
        get() = shares * averageBuyPrice

    val currentMarketValue: Double
        get() = shares * currentMarketPrice

    val unrealizedProfitLoss: Double
        get() = currentMarketValue - totalInvested

    val returnPercentage: Float
        get() = if (totalInvested > 0.0) ((unrealizedProfitLoss / totalInvested) * 100).toFloat() else 0f
}

data class Portfolio(
    val id: PortfolioId,
    val name: String,
    val holdings: List<InvestmentHolding>
) {
    val totalPortfolioValue: Double
        get() = holdings.sumOf { it.currentMarketValue }

    val totalInvestedCapital: Double
        get() = holdings.sumOf { it.totalInvested }

    val totalUnrealizedGainLoss: Double
        get() = totalPortfolioValue - totalInvestedCapital

    val totalReturnPercentage: Float
        get() = if (totalInvestedCapital > 0.0) {
            ((totalUnrealizedGainLoss / totalInvestedCapital) * 100).toFloat()
        } else {
            0f
        }

    fun calculateAllocation(holding: InvestmentHolding): Float {
        val total = totalPortfolioValue
        return if (total > 0.0) (holding.currentMarketValue / total).toFloat().coerceIn(0f, 1f) else 0f
    }
}
