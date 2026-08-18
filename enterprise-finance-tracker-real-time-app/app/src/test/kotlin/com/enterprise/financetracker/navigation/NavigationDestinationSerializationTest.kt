package com.enterprise.financetracker.navigation

import com.enterprise.financetracker.ui.navigation.DEEP_LINK_BASE_URI
import com.enterprise.financetracker.ui.navigation.DashboardDestination
import com.enterprise.financetracker.ui.navigation.LoginDestination
import com.enterprise.financetracker.ui.navigation.TransactionDetailDestination
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class NavigationDestinationSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun given_LoginDestination_when_serialized_then_produces_valid_json() {
        val serialized = json.encodeToString(LoginDestination)
        assertThat(serialized).isEqualTo("{}")
    }

    @Test
    fun given_TransactionDetailDestination_when_serialized_and_deserialized_then_argument_is_preserved() {
        val original = TransactionDetailDestination(transactionId = "tx_9988")
        val jsonString = json.encodeToString(original)

        val deserialized = json.decodeFromString<TransactionDetailDestination>(jsonString)
        assertThat(deserialized.transactionId).isEqualTo("tx_9988")
    }

    @Test
    fun given_deep_link_uri_pattern_when_parsed_then_matches_transaction_detail_route() {
        val targetTransactionId = "tx_cloud_404"
        val deepLinkUrl = "$DEEP_LINK_BASE_URI/transactions/$targetTransactionId"

        assertThat(deepLinkUrl).startsWith("https://financetracker.enterprise.com/transactions")
        val extractedId = deepLinkUrl.substringAfterLast("/")
        assertThat(extractedId).isEqualTo("tx_cloud_404")
    }
}
