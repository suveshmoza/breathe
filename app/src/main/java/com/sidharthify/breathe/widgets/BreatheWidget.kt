package com.sidharthify.breathe.widgets

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sidharthify.breathe.MainActivity
import com.sidharthify.breathe.getAqiColor
import com.sidharthify.breathe.widgets.BreatheWidgetWorker.Companion.PREF_AQI
import com.sidharthify.breathe.widgets.BreatheWidgetWorker.Companion.PREF_POLLUTANT
import com.sidharthify.breathe.widgets.BreatheWidgetWorker.Companion.PREF_STATUS
import com.sidharthify.breathe.widgets.BreatheWidgetWorker.Companion.PREF_ZONE_NAME

class BreatheWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(100.dp, 100.dp),
            DpSize(200.dp, 100.dp),
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent()
            }
        }
    }

    @Composable
    private fun WidgetContent() {
        val context = LocalContext.current
        val prefs = currentState<androidx.datastore.preferences.core.Preferences>()
        val size = LocalSize.current

        val zoneName = prefs[PREF_ZONE_NAME] ?: "Select Zone"
        val aqi = prefs[PREF_AQI] ?: 0
        val pollutant = prefs[PREF_POLLUTANT] ?: "-"
        val status = prefs[PREF_STATUS] ?: "Loading"

        val aqiColor = getAqiColor(aqi)

        val bgColor = if (status == "Success") ColorProvider(aqiColor) else GlanceTheme.colors.surfaceVariant
        val contentColor = if (status == "Success") ColorProvider(Color.Black) else GlanceTheme.colors.onSurfaceVariant

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(24.dp)
                .clickable(actionStartActivity<MainActivity>())
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (status == "Error") {
                Text("Refresh", style = TextStyle(color = contentColor))
            } else {
                if (size.width < 180.dp) {
                    // small layout
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$aqi",
                            style = TextStyle(
                                fontSize = 42.sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = zoneName,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = contentColor
                            ),
                            maxLines = 2
                        )
                    }
                } else {
                    // wide layout
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$aqi",
                                style = TextStyle(
                                    fontSize = 48.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                )
                            )
                            Text(
                                text = "NAQI",
                                style = TextStyle(fontSize = 10.sp, color = contentColor)
                            )
                        }
                        
                        Spacer(GlanceModifier.width(24.dp))
                        
                        Column(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = zoneName,
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = contentColor
                                ),
                                maxLines = 1
                            )
                            Spacer(GlanceModifier.height(4.dp))

                            val pollutantColor = if(status == "Success") {
                                ColorProvider(Color.Black.copy(alpha = 0.7f))
                            } else {
                                contentColor
                            }

                            Text(
                                text = "Main Pollutant: $pollutant",
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    color = pollutantColor
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

class BreatheWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BreatheWidget()
}