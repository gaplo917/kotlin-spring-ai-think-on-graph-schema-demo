package com.gaplo917.demo.services

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient

interface CoinPriceService {
    suspend fun getTickers(symbols: List<String>): List<CryptoTicker>
}

data class CryptoTicker(
    val symbol: String,
    val openPrice: String,
    val highPrice: String,
    val lowPrice: String,
    val lastPrice: String,
    val volume: String,
    val quoteVolume: String,
    val openTime: Long,
    val closeTime: Long,
    val firstId: Long,
    val lastId: Long,
    val count: Int
)

@Service
class CoinPriceServiceImpl : CoinPriceService {
    private val logger = LoggerFactory.getLogger(this::class.java)

    private val client by lazy {
        WebClient.builder()
            .baseUrl("https://api.binance.com")
            .build()
    }

    // Sample
    // GET https://api.binance.com/api/v3/ticker/24hr?symbols=["BTCUSDT","ETHUSDT"]&type=MINI
    // [{"symbol":"BTCUSDT","openPrice":"96224.02000000","highPrice":"96439.69000000","lowPrice":"94640.00000000","lastPrice":"95494.89000000","volume":"27858.82523000","quoteVolume":"2657591443.13856260","openTime":1732756510747,"closeTime":1732842910747,"firstId":4166814897,"lastId":4171738797,"count":4923901}]
    override suspend fun getTickers(symbols: List<String>): List<CryptoTicker> {
        logger.info("[DEMO_COIN_PRICE] request tickers with symbols: {}", symbols)

        return client.get().uri("/api/v3/ticker/24hr") {
            it.queryParam("symbols", """[${symbols.joinToString(",") { "\"$it\"" }}]""")
                .queryParam("type", "MINI")
                .build()
        }.retrieve()
            .bodyToFlux(CryptoTicker::class.java)
            .asFlow()
            .toList()
    }
}
