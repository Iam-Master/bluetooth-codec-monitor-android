package com.iammaster.codecmonitor

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.iammaster.codecmonitor.data.bluetooth.category
import com.iammaster.codecmonitor.data.export.ExportFormat
import com.iammaster.codecmonitor.data.export.HistoryExporter
import com.iammaster.codecmonitor.data.photos.DevicePhotoFetcher
import com.iammaster.codecmonitor.data.preferences.AppSettings
import com.iammaster.codecmonitor.service.CodecMonitorService
import com.iammaster.codecmonitor.ui.screens.DashboardScreen
import com.iammaster.codecmonitor.ui.screens.DevicesScreen
import com.iammaster.codecmonitor.ui.screens.HistoryScreen
import com.iammaster.codecmonitor.ui.screens.SettingsScreen
import com.iammaster.codecmonitor.ui.theme.CodecMonitorTheme
import com.iammaster.codecmonitor.ui.viewmodels.DashboardViewModel
import com.iammaster.codecmonitor.ui.viewmodels.HistoryViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

private sealed class Destination(val route: String, val label: String) {
    object Dashboard : Destination("dashboard", "Dashboard")
    object History : Destination("history", "History")
    object Devices : Destination("devices", "Devices")
    object Settings : Destination("settings", "Settings")
}

class MainActivity : ComponentActivity() {

    private lateinit var app: CodecMonitorApp
    private lateinit var viewModel: DashboardViewModel
    private lateinit var historyViewModel: HistoryViewModel
    private lateinit var photoFetcher: DevicePhotoFetcher

    private var hasBluetoothPermission by mutableStateOf(false)
    private var monitoringOwnedByActivity = false
    private var pendingExportRows: List<com.iammaster.codecmonitor.data.local.HistoryEntity> = emptyList()
    private var pendingExportFormat: ExportFormat? = null

    private val createDocumentLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingExportFormat
        if (uri != null && format != null) {
            contentResolver.openOutputStream(uri)?.use { out ->
                out.write(HistoryExporter.export(pendingExportRows, format))
            }
        }
        pendingExportFormat = null
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            hasBluetoothPermission = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true ||
                permissions[Manifest.permission.BLUETOOTH] == true
            if (hasBluetoothPermission) {
                onPermissionGranted()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        app = application as CodecMonitorApp
        viewModel = DashboardViewModel(app.bluetoothMonitor)
        historyViewModel = HistoryViewModel(app.historyRepository)
        photoFetcher = DevicePhotoFetcher(applicationContext)

        checkAndRequestPermissions()

        setContent {
            val settings by app.settingsRepository.settings.collectAsState(initial = AppSettings())

            CodecMonitorTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    val items = listOf(Destination.Dashboard, Destination.History, Destination.Devices, Destination.Settings)

                    Scaffold(
                        bottomBar = {
                            NavigationBar {
                                val navBackStackEntry by navController.currentBackStackEntryAsState()
                                val currentDestination = navBackStackEntry?.destination
                                items.forEach { dest ->
                                    val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(dest.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                when (dest) {
                                                    Destination.Dashboard -> Icons.Default.Home
                                                    Destination.History -> Icons.Default.History
                                                    Destination.Devices -> Icons.Default.Devices
                                                    Destination.Settings -> Icons.Default.Settings
                                                },
                                                contentDescription = dest.label
                                            )
                                        },
                                        label = { Text(dest.label) }
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        NavHost(
                            navController = navController,
                            startDestination = Destination.Dashboard.route,
                            modifier = Modifier.padding(padding)
                        ) {
                            composable(Destination.Dashboard.route) {
                                val codecStatus by viewModel.codecStatus.collectAsState()
                                val connectedDevice by viewModel.connectedDevice.collectAsState()
                                val connectedDevicesList by viewModel.connectedDevicesList.collectAsState()
                                val stabilityStatus by viewModel.stabilityStatus.collectAsState()
                                var photoFile by remember { mutableStateOf<File?>(null) }
                                var batteryInfo by remember { mutableStateOf<com.iammaster.codecmonitor.data.bluetooth.BatteryInfo?>(null) }

                                androidx.compose.runtime.LaunchedEffect(connectedDevice?.name) {
                                    val name = connectedDevice?.name
                                    photoFile = if (name != null) {
                                        photoFetcher.getCachedPhotoFile(name) ?: photoFetcher.fetchIfMissing(name)
                                    } else null
                                    batteryInfo = app.bluetoothMonitor.getBatteryInfo(connectedDevice)
                                }

                                DashboardScreen(
                                    codecStatus = codecStatus,
                                    hasPermission = hasBluetoothPermission,
                                    deviceConnected = connectedDevice != null,
                                    connectedDevices = connectedDevicesList,
                                    selectedDevice = connectedDevice,
                                    onDeviceSelected = { viewModel.selectDevice(it) },
                                    stabilityStatus = stabilityStatus,
                                    devicePhotoFile = photoFile,
                                    batteryInfo = batteryInfo,
                                    onSettingsClick = {
                                        navController.navigate(Destination.Settings.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                            composable(Destination.History.route) {
                                val points by historyViewModel.points.collectAsState()
                                val range by historyViewModel.range.collectAsState()
                                val selectedMac by historyViewModel.selectedMac.collectAsState()
                                val deviceOptions by historyViewModel.deviceOptions.collectAsState()

                                LaunchedEffect(Unit) { historyViewModel.refreshDeviceOptions() }

                                HistoryScreen(
                                    points = points,
                                    range = range,
                                    onRangeSelected = { historyViewModel.setRange(it) },
                                    onExportRequested = { format ->
                                        lifecycleScope.launch {
                                            pendingExportRows = historyViewModel.exportRows()
                                            pendingExportFormat = format
                                            createDocumentLauncher.launch("codec_monitor_history.${format.extension}")
                                        }
                                    },
                                    deviceOptions = deviceOptions,
                                    selectedMac = selectedMac,
                                    onDeviceFilterSelected = { historyViewModel.setSelectedMac(it) }
                                )
                            }
                            composable(Destination.Devices.route) {
                                val connectedDevice by viewModel.connectedDevice.collectAsState()
                                val connectedDevicesList by viewModel.connectedDevicesList.collectAsState()
                                var bondedDevices by remember { mutableStateOf(app.bluetoothMonitor.getBondedDevices()) }
                                val photoMap = remember { mutableStateMapOf<String, File?>() }

                                LaunchedEffect(Unit) {
                                    bondedDevices = app.bluetoothMonitor.getBondedDevices()
                                    val toFetch = bondedDevices.mapNotNull { device ->
                                        val name = device.name ?: return@mapNotNull null
                                        val isHeadphones = device.category() == com.iammaster.codecmonitor.data.bluetooth.DeviceCategory.HEADPHONES
                                        if (!isHeadphones) return@mapNotNull null
                                        val cached = photoFetcher.getCachedPhotoFile(name)
                                        if (cached != null) {
                                            photoMap[name] = cached
                                            null
                                        } else {
                                            name
                                        }
                                    }
                                    // Fetch all missing photos in parallel instead of one-by-one.
                                    val results = toFetch.map { name ->
                                        async { name to photoFetcher.fetchIfMissing(name) }
                                    }.map { it.await() }
                                    results.forEach { (name, file) -> photoMap[name] = file }
                                }

                                DevicesScreen(
                                    bondedDevices = bondedDevices,
                                    connectedDevices = connectedDevicesList,
                                    activeDevice = connectedDevice,
                                    photoFor = { name -> photoMap[name] }
                                )
                            }
                            composable(Destination.Settings.route) {
                                SettingsScreen(
                                    settings = settings,
                                    onPollIntervalChanged = { lifecycleScope.launch { app.settingsRepository.setPollIntervalMs(it) } },
                                    onRetentionChanged = { lifecycleScope.launch { app.settingsRepository.setHistoryRetentionDays(it) } },
                                    onNotificationsChanged = { lifecycleScope.launch { app.settingsRepository.setNotificationsEnabled(it) } },
                                    onMonitorInBackgroundChanged = { lifecycleScope.launch { app.settingsRepository.setMonitorInBackground(it) } },
                                    onThemeChanged = { lifecycleScope.launch { app.settingsRepository.setThemeMode(it) } }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isEmpty()) {
            hasBluetoothPermission = true
            onPermissionGranted()
        } else {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun onPermissionGranted() {
        lifecycleScope.launch {
            val current = app.settingsRepository.settings.first()
            if (current.monitorInBackground) {
                CodecMonitorService.start(this@MainActivity)
            } else {
                monitoringOwnedByActivity = true
                viewModel.startMonitoring()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only stop monitoring here if this Activity is the one that started it directly.
        // When background monitoring is enabled, CodecMonitorService owns the shared
        // BluetoothMonitor's lifecycle instead — calling stopMonitoring() here would
        // unregister its receivers out from under the still-running foreground service.
        if (monitoringOwnedByActivity) {
            viewModel.stopMonitoring()
        }
        photoFetcher.close()
    }
}
