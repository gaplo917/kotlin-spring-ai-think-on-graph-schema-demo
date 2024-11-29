package com.gaplo917.demo.tools

import com.fasterxml.jackson.annotation.JsonCreator
import com.gaplo917.demo.services.CoinPriceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.model.function.FunctionCallbackWrapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function

data class GetCoinPriceRequest @JsonCreator constructor(
    val symbols: List<String>
)

data class GetCoinPriceResponse(
    val prices: List<CoinPrice>
) {
    data class CoinPrice(
        val symbol: String,
        val price: String
    )
}

const val GET_COIN_PRICE_LIST = "getCoinPriceList"

@Configuration
class GetCoinPriceToolConfig {
    private val logger = LoggerFactory.getLogger(this.javaClass)

    @Bean(value = [GET_COIN_PRICE_LIST])
    fun getCoinPrice(coinPriceService: CoinPriceService): FunctionCallback {
        return FunctionCallbackWrapper.builder(Function<GetCoinPriceRequest, GetCoinPriceResponse> { req ->
            runBlocking(Dispatchers.IO) {
                logger.info("[DEMO_COIN_PRICE] symbols: {}", req.symbols)
                GetCoinPriceResponse(
                    coinPriceService.getTickers(req.symbols).map {
                        GetCoinPriceResponse.CoinPrice(
                            symbol = it.symbol,
                            price = it.lastPrice
                        )
                    }
                )
            }
        }).withName(GET_COIN_PRICE_LIST)
            .withDescription("Get coin price list from exchange. Required `symbols` parameters from user. Example symbols are BTCUSDT")
            .withInputType(GetCoinPriceRequest::class.java)
            .build()
    }
}
