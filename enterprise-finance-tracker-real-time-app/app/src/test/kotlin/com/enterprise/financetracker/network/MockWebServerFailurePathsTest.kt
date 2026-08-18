package com.enterprise.financetracker.network

import com.enterprise.financetracker.core.network.NetworkClientFactory
import com.enterprise.financetracker.core.network.TokenManager
import com.enterprise.financetracker.core.network.safeApiCall
import com.enterprise.financetracker.data.network.api.FinanceApiService
import com.enterprise.financetracker.domain.model.FinancialResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class MockWebServerFailurePathsTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var tokenManager: TokenManager
    private lateinit var apiService: FinanceApiService

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        tokenManager = TokenManager()

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(500, TimeUnit.MILLISECONDS)
            .readTimeout(500, TimeUnit.MILLISECONDS)
            .build()

        val retrofit = NetworkClientFactory.createRetrofit(
            baseUrl = mockWebServer.url("/").toString(),
            okHttpClient = okHttpClient
        )

        apiService = NetworkClientFactory.createFinanceApiService(retrofit)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun given_200_ok_when_fetchTransactions_called_then_return_FinancialResult_Success() = runTest {
        val sampleJson = """
            [
              {
                "id": "tx_net_1",
                "account_id": "acc_main",
                "title": "Cloud Services",
                "amount": 99.99,
                "type": "EXPENSE",
                "timestamp_epoch_millis": 1738000000000
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(sampleJson)
        )

        val result = safeApiCall { apiService.getTransactions() }

        assertThat(result).isInstanceOf(FinancialResult.Success::class.java)
        val data = (result as FinancialResult.Success).value
        assertThat(data).hasSize(1)
        assertThat(data.first().title).isEqualTo("Cloud Services")
    }

    @Test
    fun given_401_unauthorized_when_api_called_then_return_FinancialResult_Failure_Unauthorized() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"error": "Unauthorized"}""")
        )

        val result = safeApiCall { apiService.getTransactionById("tx_1") }

        assertThat(result).isInstanceOf(FinancialResult.Failure.Unauthorized::class.java)
    }

    @Test
    fun given_404_not_found_when_api_called_then_return_FinancialResult_Failure_ValidationError() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("""{"message": "Transaction not found"}""")
        )

        val result = safeApiCall { apiService.getTransactionById("non_existing_id") }

        assertThat(result).isInstanceOf(FinancialResult.Failure.ValidationError::class.java)
    }

    @Test
    fun given_500_server_error_when_api_called_then_return_FinancialResult_Failure_UnexpectedError() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Crash")
        )

        val result = safeApiCall { apiService.getTransactions() }

        assertThat(result).isInstanceOf(FinancialResult.Failure.UnexpectedError::class.java)
    }

    @Test
    fun given_socket_timeout_when_api_called_then_return_FinancialResult_Failure_NetworkError() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setSocketPolicy(SocketPolicy.NO_RESPONSE) // Simulates hung server / timeout
        )

        val result = safeApiCall { apiService.getTransactions() }

        assertThat(result).isInstanceOf(FinancialResult.Failure.NetworkError::class.java)
    }

    @Test
    fun given_malformed_json_when_api_called_then_return_FinancialResult_Failure_UnexpectedError() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("INVALID_RAW_NON_JSON_CORRUPTED_PAYLOAD")
        )

        val result = safeApiCall { apiService.getTransactions() }

        assertThat(result).isInstanceOf(FinancialResult.Failure.UnexpectedError::class.java)
    }
}
