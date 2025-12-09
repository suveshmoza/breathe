package com.sidharthify.breathe.widgets

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result
import com.sidharthify.breathe.RetrofitClient

class BreatheWidgetWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        val PREF_ZONE_ID = stringPreferencesKey("zone_id")
        val PREF_ZONE_NAME = stringPreferencesKey("zone_name")
        val PREF_AQI = intPreferencesKey("aqi")
        val PREF_POLLUTANT = stringPreferencesKey("main_pollutant")
        val PREF_LAST_UPDATED = longPreferencesKey("last_updated")
        val PREF_STATUS = stringPreferencesKey("status")
    }

    override suspend fun doWork(): Result {
        val manager = GlanceAppWidgetManager(context)
        val glanceIds = manager.getGlanceIds(BreatheWidget::class.java)

        glanceIds.forEach { glanceId ->
            updateWidgetData(context, glanceId)
        }

        return Result.success()
    }

    private suspend fun updateWidgetData(context: Context, glanceId: GlanceId) {
        updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
            val zoneId = prefs[PREF_ZONE_ID] ?: return@updateAppWidgetState prefs

            try {
                val response = RetrofitClient.api.getZoneAqi(zoneId)
                
                prefs.toMutablePreferences().apply {
                    this[PREF_AQI] = response.nAqi
                    this[PREF_ZONE_NAME] = response.zoneName
                    this[PREF_POLLUTANT] = response.mainPollutant
                    this[PREF_LAST_UPDATED] = System.currentTimeMillis()
                    this[PREF_STATUS] = "Success"
                }
            } catch (e: Exception) {
                prefs.toMutablePreferences().apply {
                    this[PREF_STATUS] = "Error"
                }
            }
        }
        BreatheWidget().update(context, glanceId)
    }
}