package com.sidharthify.breathe

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// --- Navigation Enum ---
enum class AppScreen(val label: String, val iconFilled: ImageVector, val iconOutlined: ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Explore("Explore", Icons.Filled.Search, Icons.Outlined.Search),
    Settings("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

// --- ViewModel ---
class BreatheViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private var allZones = listOf<Zone>()

    fun init(context: Context) {
        viewModelScope.launch {
            try {
                val zonesResp = RetrofitClient.api.getZones()
                allZones = zonesResp.zones

                val prefs = context.getSharedPreferences("breathe_prefs", Context.MODE_PRIVATE)
                val pinnedSet = prefs.getStringSet("pinned_ids", emptySet()) ?: emptySet()

                val pinnedAqiList = mutableListOf<AqiResponse>()
                for (id in pinnedSet) {
                    try {
                        pinnedAqiList.add(RetrofitClient.api.getZoneAqi(id))
                    } catch (e: Exception) {
                    }
                }

                _uiState.value = UiState.Success(
                    zones = allZones,
                    pinnedZones = pinnedAqiList,
                    pinnedIds = pinnedSet
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Failed to connect: ${e.localizedMessage}")
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun togglePin(context: Context, zoneId: String) {
        val currentState = _uiState.value
        if (currentState is UiState.Success) {
            val newPinnedIds = currentState.pinnedIds.toMutableSet()
            if (newPinnedIds.contains(zoneId)) {
                newPinnedIds.remove(zoneId)
            } else {
                newPinnedIds.add(zoneId)
            }

            context.getSharedPreferences("breathe_prefs", Context.MODE_PRIVATE)
                .edit().putStringSet("pinned_ids", newPinnedIds).apply()

            init(context)
        }
    }
}

// --- Main Activity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = dynamicDarkColorScheme(LocalContext.current)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BreatheApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BreatheApp(viewModel: BreatheViewModel = viewModel()) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(AppScreen.Home) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppScreen.values().forEach { screen ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                if (currentScreen == screen) screen.iconFilled else screen.iconOutlined, 
                                contentDescription = screen.label
                            ) 
                        },
                        label = { Text(screen.label) },
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (val s = state) {
                is UiState.Loading -> LoadingScreen()
                is UiState.Error -> ErrorScreen(s.message) { viewModel.init(context) }
                is UiState.Success -> {
                    when (currentScreen) {
                        AppScreen.Home -> HomeScreen(
                            pinnedZones = s.pinnedZones,
                            onGoToExplore = { currentScreen = AppScreen.Explore }
                        )
                        AppScreen.Explore -> ExploreScreen(
                            zones = s.zones,
                            pinnedIds = s.pinnedIds,
                            query = viewModel.searchQuery.collectAsState().value,
                            onSearchChange = viewModel::onSearchQueryChanged,
                            onPinToggle = { id -> viewModel.togglePin(context, id) }
                        )
                        AppScreen.Settings -> SettingsScreen()
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------
// SECTION 1: HOME
// ----------------------------------------------------------------
@Composable
fun HomeScreen(
    pinnedZones: List<AqiResponse>,
    onGoToExplore: () -> Unit
) {
    var selectedZone by remember { mutableStateOf(pinnedZones.firstOrNull()) }
    
    LaunchedEffect(pinnedZones) {
        if (selectedZone == null && pinnedZones.isNotEmpty()) {
            selectedZone = pinnedZones.first()
        }
    }

    if (pinnedZones.isEmpty()) {
        EmptyState(onGoToExplore)
    } else {
        val currentDisplayZone = selectedZone ?: pinnedZones.first()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
        ) {
            Text(
                "Pinned Locations",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 12.dp),
                color = MaterialTheme.colorScheme.primary
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pinnedZones) { zone ->
                    PinnedMiniCard(
                        zone = zone,
                        isSelected = zone.zoneId == currentDisplayZone.zoneId,
                        onClick = { selectedZone = zone }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 2. Main Dashboard (Changes based on selection)
            MainDashboardDetail(currentDisplayZone)
        }
    }
}

@Composable
fun PinnedMiniCard(zone: AqiResponse, isSelected: Boolean, onClick: () -> Unit) {
    val aqiColor = getAqiColor(zone.usAqi)
    
    Card(
        onClick = onClick,
        modifier = Modifier.width(150.dp).height(110.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = zone.zoneName,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(aqiColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${zone.usAqi}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun MainDashboardDetail(zone: AqiResponse) {
    val aqiColor = getAqiColor(zone.usAqi)

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text("Current Air Quality", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(zone.zoneName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        // Big AQI Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(aqiColor.copy(alpha = 0.4f), aqiColor.copy(alpha = 0.1f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${zone.usAqi}",
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 90.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = aqiColor
                )
                Surface(
                    color = aqiColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        text = "AQI",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Primary: ${zone.mainPollutant.uppercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("Pollutants", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(16.dp))

        val pollutants = zone.concentrations ?: emptyMap()
        if (pollutants.isEmpty()) {
            Text("No detailed pollutant data available.")
        } else {
            FlowRowGrid(pollutants)
        }
    }
}

@Composable
fun EmptyState(onGoToExplore: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.PushPin, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(modifier = Modifier.height(16.dp))
        Text("No Pinned Zones", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Pin locations in Explore to view them here.", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGoToExplore) {
            Text("Go to Explore")
        }
    }
}

// ----------------------------------------------------------------
// SECTION 2: EXPLORE
// ----------------------------------------------------------------
@Composable
fun ExploreScreen(
    zones: List<Zone>,
    pinnedIds: Set<String>,
    query: String,
    onSearchChange: (String) -> Unit,
    onPinToggle: (String) -> Unit
) {
    val filteredZones = zones.filter {
        it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search J&K...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = MaterialTheme.shapes.extraLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredZones) { zone ->
                ZoneListItem(
                    zone = zone,
                    isPinned = pinnedIds.contains(zone.id),
                    onPinClick = { onPinToggle(zone.id) }
                )
            }
        }
    }
}

@Composable
fun ZoneListItem(zone: Zone, isPinned: Boolean, onPinClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isPinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(zone.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(zone.provider ?: "Unknown", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPinClick) {
                Icon(
                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if(isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ----------------------------------------------------------------
// SECTION 3: SETTINGS
// ----------------------------------------------------------------
@Composable
fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        
        SettingsItem(title = "App Theme", subtitle = "System Default (Material You)")
        SettingsItem(title = "Data Standards", subtitle = "Indian National Air Quality Index (NAQI)")
        SettingsItem(title = "Sources", subtitle = "CPCB (Govt. of India) & OpenWeather")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "Built with Kotlin & FastAPI.\nData may be delayed by 1-2 hours.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, subtitle: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
}

// --- Utils ---

@Composable
fun FlowRowGrid(pollutants: Map<String, Double>) {
    val items = pollutants.entries.toList()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { rowItems ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEach { (key, value) ->
                    PollutantCard(
                        modifier = Modifier.weight(1f),
                        name = formatPollutantName(key),
                        value = "$value",
                        unit = if (key.lowercase() == "co") "mg/m³" else "µg/m³"
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun PollutantCard(modifier: Modifier = Modifier, name: String, value: String, unit: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.width(4.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 4.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorScreen(msg: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Connection Failed", style = MaterialTheme.typography.titleLarge)
            Text(msg, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}

fun formatPollutantName(key: String): String {
    return when(key.lowercase()) {
        "pm2_5", "pm2.5" -> "PM2.5"
        "pm10" -> "PM10"
        "no2" -> "NO₂"
        "so2" -> "SO₂"
        "co" -> "CO"
        "o3" -> "O₃"
        "nh3" -> "NH₃"
        else -> key.uppercase()
    }
}

fun getAqiColor(aqi: Int): Color {
    return when (aqi) {
        in 0..50 -> Color(0xFF00B050) // Good (Green)
        in 51..100 -> Color(0xFF92D050) // Satisfactory (Light Green)
        in 101..200 -> Color(0xFFFFFF00) // Moderate (Yellow)
        in 201..300 -> Color(0xFFFF9900) // Poor (Orange)
        in 301..400 -> Color(0xFFFF0000) // Very Poor (Red)
        else -> Color(0xFFC00000) // Severe (Deep Red)
    }
}