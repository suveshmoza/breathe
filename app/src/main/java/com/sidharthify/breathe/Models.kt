package com.sidharthify.breathe

import com.google.gson.annotations.SerializedName

data class ZonesResponse(
    val zones: List<Zone>
)

data class Zone(
    val id: String,
    val name: String,
    val provider: String?,
    val lat: Double?,
    val lon: Double?
)

data class AqiResponse(
    @SerializedName("zone_id") val zoneId: String,
    @SerializedName("zone_name") val zoneName: String,
    @SerializedName("us_aqi") val usAqi: Int,
    @SerializedName("main_pollutant") val mainPollutant: String,
    @SerializedName("aqi_breakdown") val aqiBreakdown: Map<String, Int>?,
    @SerializedName("concentrations_us_units") val concentrations: Map<String, Double>?
)

sealed class UiState {
    object Loading : UiState()
    data class Success(
        val zones: List<Zone>,
        val pinnedZones: List<AqiResponse>,
        val pinnedIds: Set<String>
    ) : UiState()
    data class Error(val message: String) : UiState()
}