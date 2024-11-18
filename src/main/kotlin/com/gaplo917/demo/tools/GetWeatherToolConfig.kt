package com.gaplo917.demo.tools

import com.fasterxml.jackson.annotation.JsonCreator
import org.springframework.ai.model.function.FunctionCallback
import org.springframework.ai.model.function.FunctionCallbackWrapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Function

@Deprecated(message = "small models do not work will with many tools. ")
data class GetWeatherForecastRequest @JsonCreator constructor(
    val location: String
)

@Deprecated(message = "small models do not work will with many tools. ")
data class GetWeatherForecastResponse(
    val forecast: List<Forecast>
) {
    data class Forecast(
        val date: String,
        val temperature: String,
        val description: String
    )
}

@Deprecated(message = "small models do not work will with many tools. ")
const val GET_WEATHER_TOOL_NAME = "getWeatherForecast"

@Deprecated(message = "small models do not work will with many tools. ")
@Configuration
class GetWeatherToolConfig {
    @Bean(value = [GET_WEATHER_TOOL_NAME])
    fun getWeatherForecast(): FunctionCallback {
        return FunctionCallbackWrapper.builder(Function<GetWeatherForecastRequest, GetWeatherForecastResponse> { req ->
            GetWeatherForecastResponse(
                forecast = listOf(
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-01",
                        temperature = "20°C",
                        description = "Sunny"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-02",
                        temperature = "18°C",
                        description = "Cloudy"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-03",
                        temperature = "16°C",
                        description = "Rainy"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-04",
                        temperature = "14°C",
                        description = "Snowy"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-05",
                        temperature = "12°C",
                        description = "Windy"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-06",
                        temperature = "10°C",
                        description = "Foggy"
                    ),
                    GetWeatherForecastResponse.Forecast(
                        date = "2024-01-07",
                        temperature = "8°C",
                        description = "Stormy"
                    )
                )
            )
        }).withName(GET_WEATHER_TOOL_NAME)
            .withDescription("Get weather forecast")
            .withInputType(GetWeatherForecastRequest::class.java)
            .build()
    }
}
