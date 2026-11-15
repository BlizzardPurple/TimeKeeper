package com.office.timekeeper.domain

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Fetches weather forecast from Open-Meteo API (free, no API key).
 * Returns hourly precipitation data for the office location.
 */
object WeatherService {
    private const val TAG = "WeatherService"
    private val IST = ZoneId.of("Asia/Kolkata")

    data class HourlyForecast(
        val time: LocalDateTime,
        val precipitationProbability: Int, // 0-100%
        val precipitationMm: Double,
        val weatherCode: Int // WMO code
    )

    data class WeatherSuggestion(
        val hasRainSoon: Boolean,
        val rainStartTime: LocalDateTime?, // when rain starts
        val suggestion: String,
        val currentWeatherDesc: String,
        val currentTempC: Double?
    )

    /**
     * Fetches the next 6 hours of hourly forecast for the given location.
     * Returns null on any failure (network, parsing, etc.)
     */
    suspend fun fetchForecast(latitude: Double, longitude: Double): List<HourlyForecast>? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    "https://api.open-meteo.com/v1/forecast?" +
                    "latitude=$latitude&longitude=$longitude" +
                    "&hourly=precipitation_probability,precipitation,weather_code" +
                    "&current=temperature_2m,weather_code" +
                    "&timezone=Asia/Kolkata" +
                    "&forecast_days=1"
                )
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 10_000
                connection.readTimeout = 10_000

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = JSONObject(response)
                val hourly = json.getJSONObject("hourly")
                val times = hourly.getJSONArray("time")
                val probabilities = hourly.getJSONArray("precipitation_probability")
                val precipitations = hourly.getJSONArray("precipitation")
                val weatherCodes = hourly.getJSONArray("weather_code")

                val now = LocalDateTime.now(IST)
                val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")

                val forecasts = mutableListOf<HourlyForecast>()
                for (i in 0 until times.length()) {
                    val time = LocalDateTime.parse(times.getString(i), formatter)
                    if (time.isAfter(now) && time.isBefore(now.plusHours(6))) {
                        forecasts.add(
                            HourlyForecast(
                                time = time,
                                precipitationProbability = probabilities.optInt(i, 0),
                                precipitationMm = precipitations.optDouble(i, 0.0),
                                weatherCode = weatherCodes.optInt(i, 0)
                            )
                        )
                    }
                }
                forecasts
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch weather", e)
                null
            }
        }
    }

    /**
     * Analyzes forecast and generates a suggestion combining weather + remaining work hours.
     *
     * @param forecasts hourly forecast data
     * @param remainingMinutes minutes remaining to complete weekly target
     * @param commuteBufferMinutes commute buffer (default 15)
     */
    fun generateSuggestion(
        forecasts: List<HourlyForecast>,
        remainingMinutes: Long,
        commuteBufferMinutes: Int = 15
    ): WeatherSuggestion {
        val now = LocalDateTime.now(IST)

        // Find first hour with rain (probability > 50% or precipitation > 0.5mm)
        val rainHour = forecasts.firstOrNull {
            it.precipitationProbability > 50 || it.precipitationMm > 0.5
        }

        val currentDesc = if (forecasts.isEmpty()) "Unknown" else describeWeather(forecasts.first().weatherCode)

        if (rainHour == null) {
            return WeatherSuggestion(
                hasRainSoon = false,
                rainStartTime = null,
                suggestion = "All clear — no rain expected in the next 6 hours.",
                currentWeatherDesc = currentDesc,
                currentTempC = null
            )
        }

        val minutesUntilRain = java.time.Duration.between(now, rainHour.time).toMinutes()
        val leaveByTime = rainHour.time.minusMinutes(commuteBufferMinutes.toLong())
        val timeFormat = DateTimeFormatter.ofPattern("h:mm a")

        val suggestion = if (remainingMinutes <= 0) {
            "🌧️ Rain at ${rainHour.time.format(timeFormat)}. Target already met — leave when ready!"
        } else if (remainingMinutes <= minutesUntilRain - commuteBufferMinutes) {
            "🌧️ Rain at ${rainHour.time.format(timeFormat)}. You'll finish in ${remainingMinutes}min — leave by ${leaveByTime.format(timeFormat)} to stay dry."
        } else {
            "🌧️ Rain at ${rainHour.time.format(timeFormat)}. You need ${remainingMinutes}min more but rain starts in ${minutesUntilRain}min. Plan accordingly."
        }

        return WeatherSuggestion(
            hasRainSoon = true,
            rainStartTime = rainHour.time,
            suggestion = suggestion,
            currentWeatherDesc = currentDesc,
            currentTempC = null
        )
    }

    private fun describeWeather(code: Int): String = when (code) {
        0 -> "☀️ Clear"
        1, 2, 3 -> "⛅ Partly cloudy"
        45, 48 -> "🌫️ Foggy"
        51, 53, 55 -> "🌦️ Drizzle"
        61, 63, 65 -> "🌧️ Rain"
        66, 67 -> "🌧️ Freezing rain"
        71, 73, 75, 77 -> "❄️ Snow"
        80, 81, 82 -> "🌧️ Rain showers"
        85, 86 -> "❄️ Snow showers"
        95, 96, 99 -> "⛈️ Thunderstorm"
        else -> "🌤️ Fair"
    }
}
