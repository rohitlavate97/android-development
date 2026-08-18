package com.enterprise.financetracker.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-Safe Navigation Destinations using Kotlinx Serialization.
 * Replaces fragile String-based routes with compile-time type-safe objects and classes.
 * (Phase 9 Concept 1 & ADR 024)
 */

// Nested Graph 1: Authentication
@Serializable
data object AuthGraph

@Serializable
data object LoginDestination

// Nested Graph 2: Main Application
@Serializable
data object MainGraph

@Serializable
data object DashboardDestination

@Serializable
data object TransactionListDestination

@Serializable
data class TransactionDetailDestination(
    val transactionId: String
)
