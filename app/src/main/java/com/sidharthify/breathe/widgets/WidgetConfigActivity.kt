package com.sidharthify.breathe.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkManager
import androidx.work.OneTimeWorkRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sidharthify.breathe.R
import com.sidharthify.breathe.MainActivity
import com.sidharthify.breathe.RetrofitClient
import com.sidharthify.breathe.Zone
import com.sidharthify.breathe.getAqiColor
import com.sidharthify.breathe.AqiResponse

class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                Surface {
                    ConfigScreen()
                }
            }
        }
    }

    @Composable
    fun ConfigScreen() {
        var zones by remember { mutableStateOf<List<Zone>>(emptyList()) }
        var isLoading by remember { mutableStateOf(true) }

        LaunchedEffect(Unit) {
            try {
                zones = withContext(Dispatchers.IO) {
                    RetrofitClient.api.getZones().zones
                }
            } catch (e: Exception) {
            } finally {
                isLoading = false
            }
        }

        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Text("Select a Zone", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(zones) { zone ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { confirmSelection(zone) },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(zone.name, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun confirmSelection(zone: Zone) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(applicationContext)
                .getGlanceIdBy(appWidgetId)

            updateAppWidgetState(applicationContext, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                prefs.toMutablePreferences().apply {
                    this[BreatheWidgetWorker.PREF_ZONE_ID] = zone.id
                    this[BreatheWidgetWorker.PREF_ZONE_NAME] = zone.name
                    // we trigger a worker fetch immediately after this, so we don't need AQI yet
                }
            }

            BreatheWidget().update(applicationContext, glanceId)

             androidx.work.WorkManager.getInstance(applicationContext)
                .enqueue(androidx.work.OneTimeWorkRequest.from(BreatheWidgetWorker::class.java))

            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(Activity.RESULT_OK, resultValue)
            finish()
        }
    }
}