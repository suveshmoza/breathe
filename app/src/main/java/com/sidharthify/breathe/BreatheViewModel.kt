package com.sidharthify.breathe

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class BreatheViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(AppState())
    val uiState = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun init(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val prefs = context.getSharedPreferences("breathe_prefs", Context.MODE_PRIVATE)
            val pinnedSet = prefs.getStringSet("pinned_ids", emptySet()) ?: emptySet()

            try {
                val zonesList = RetrofitClient.api.getZones().zones

                val allJobs = zonesList.map { zone ->
                    async {
                        try { RetrofitClient.api.getZoneAqi(zone.id) } catch (e: Exception) { null }
                    }
                }

                val allResults = allJobs.awaitAll().filterNotNull()
                val pinnedResults = allResults.filter { it.zoneId in pinnedSet }

                _uiState.value = AppState(
                    isLoading = false,
                    error = if (zonesList.isEmpty()) "Could not connect to server." else null,
                    zones = zonesList,
                    allAqiData = allResults,
                    pinnedZones = pinnedResults,
                    pinnedIds = pinnedSet
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error: ${e.localizedMessage}"
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(context: Context, zoneId: String) {
        val currentSet = _uiState.value.pinnedIds.toMutableSet()
        val isAdding = !currentSet.contains(zoneId)

        if (isAdding) currentSet.add(zoneId) else currentSet.remove(zoneId)

        context.getSharedPreferences("breathe_prefs", Context.MODE_PRIVATE)
            .edit().putStringSet("pinned_ids", currentSet).apply()

        val updatedPinnedList = _uiState.value.allAqiData.filter { it.zoneId in currentSet }

        _uiState.value = _uiState.value.copy(
            pinnedIds = currentSet,
            pinnedZones = updatedPinnedList
        )
    }

    fun checkForUpdates(context: Context, currentVersion: String) {
        viewModelScope.launch {
            try {
                val latestReleaseUrl = "https://api.github.com/repos/breathe-OSS/breathe/releases/latest"

                val response = withContext(Dispatchers.IO) {
                    URL(latestReleaseUrl).readText()
                }

                val json = JSONObject(response)
                val latestTag = json.getString("tag_name")
                val htmlUrl = json.getString("html_url")

                val cleanCurrent = currentVersion.removePrefix("v")
                val cleanLatest = latestTag.removePrefix("v")

                if (cleanLatest != cleanCurrent) {
                    Toast.makeText(context, "Update found: $latestTag", Toast.LENGTH_LONG).show()
                    val browserIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse(htmlUrl)
                    )
                    context.startActivity(browserIntent)
                } else {
                    Toast.makeText(context, "No updates found. You are on the latest version.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Failed to check for updates.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}