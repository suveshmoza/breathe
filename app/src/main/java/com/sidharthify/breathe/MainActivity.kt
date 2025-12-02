package com.sidharthify.breathe

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope // Explicit Import Fix
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

// --- UI ---
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
    val state by viewModel.uiState.collectAsState()
    val query by viewModel.searchQuery.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.init(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Breathe", style = MaterialTheme.typography.headlineMedium) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (val s = state) {
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.init(context) }) { Text("Retry") }
                    }
                }
                is UiState.Success -> {
                    DashboardContent(
                        zones = s.zones,
                        pinnedZones = s.pinnedZones,
                        pinnedIds = s.pinnedIds,
                        query = query,
                        onSearchChange = viewModel::onSearchQueryChanged,
                        onPinToggle = { id -> viewModel.togglePin(context, id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardContent(
    zones: List<Zone>,
    pinnedZones: List<AqiResponse>,
    pinnedIds: Set<String>,
    query: String,
    onSearchChange: (String) -> Unit,
    onPinToggle: (String) -> Unit
) {
    val filteredZones = zones.filter {
        it.name.contains(query, ignoreCase = true) || it.id.contains(query, ignoreCase = true)
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = onSearchChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search zones...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                shape = MaterialTheme.shapes.extraLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                )
            )
        }

        if (pinnedZones.isNotEmpty() && query.isEmpty()) {
            item {
                Text(
                    "Pinned",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(pinnedZones) { aqiData ->
                AqiCard(aqiData = aqiData, isPinned = true) {
                    onPinToggle(aqiData.zoneId)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }

        item {
            Text(
                if (query.isNotEmpty()) "Search Results" else "All Zones",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (filteredZones.isEmpty()) {
            item {
                Text("No zones found.", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            items(filteredZones) { zone ->
                ZoneCard(
                    zone = zone,
                    isPinned = pinnedIds.contains(zone.id),
                    onPinClick = { onPinToggle(zone.id) }
                )
            }
        }
    }
}

@Composable
fun AqiCard(aqiData: AqiResponse, isPinned: Boolean, onPinClick: () -> Unit) {
    val aqiColor = getAqiColor(aqiData.usAqi)
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = aqiData.zoneName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Main Pollutant: ${aqiData.mainPollutant.uppercase()}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onPinClick) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Air,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = aqiColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${aqiData.usAqi}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = aqiColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "US AQI",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ZoneCard(zone: Zone, isPinned: Boolean, onPinClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = zone.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = zone.provider ?: "Unknown Provider",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onPinClick) {
                Icon(
                    imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun getAqiColor(aqi: Int): Color {
    return when (aqi) {
        in 0..50 -> Color(0xFF00E400) // Green
        in 51..100 -> Color(0xFFFFFF00) // Yellow
        in 101..150 -> Color(0xFFFF7E00) // Orange
        in 151..200 -> Color(0xFFFF0000) // Red
        in 201..300 -> Color(0xFF8F3F97) // Purple
        else -> Color(0xFF7E0023) // Maroon
    }
}