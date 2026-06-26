package com.iammaster.codecmonitor.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.iammaster.codecmonitor.data.stability.StabilityStatus
import com.iammaster.codecmonitor.data.stability.StabilityTracker
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class CodecStatus(
    val codecName: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val estimatedBitrate: Int,
    val isEstimated: Boolean = true
)

enum class MonitorEventType { CONNECT, DISCONNECT, SWITCH, UPGRADE, DOWNGRADE, CODEC_CHANGE }

data class BatteryInfo(val left: Int?, val right: Int?, val case: Int?, val main: Int?)

data class MonitorEvent(
    val type: MonitorEventType,
    val deviceName: String?,
    val codecName: String?
)

@SuppressLint("MissingPermission") // Permissions are handled in UI
class BluetoothMonitor(private val context: Context) : BluetoothProfile.ServiceListener {

    private val _codecStatus = MutableStateFlow<CodecStatus?>(null)
    val codecStatus: StateFlow<CodecStatus?> = _codecStatus

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val _connectedDevicesList = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val connectedDevicesList: StateFlow<List<BluetoothDevice>> = _connectedDevicesList

    private val stabilityTracker = StabilityTracker()
    private val _stabilityStatus = MutableStateFlow(stabilityTracker.computeStability())
    val stabilityStatus: StateFlow<StabilityStatus> = _stabilityStatus

    private val _events = MutableSharedFlow<MonitorEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<MonitorEvent> = _events

    // Bounded to avoid unbounded growth over the lifetime of this process-scoped singleton —
    // only the most recently seen devices matter for upgrade/downgrade detection.
    private val lastCodecNameByMac: MutableMap<String, String> = LinkedHashMap()
    private val maxTrackedMacs = 20

    // Battery level reported via the classic Bluetooth HFP "battery indicator" broadcast.
    // This is how the system's own status bar/notification battery icon is sourced for most
    // headsets/earbuds — there is no public, non-privileged API to query it on demand, only
    // this broadcast that fires when the headset reports an update.
    private val hfpBatteryByAddress = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") return
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
            if (level in 0..100) {
                hfpBatteryByAddress[device.address] = level
            }
        }
    }

    fun getBatteryInfo(device: BluetoothDevice?): BatteryInfo? {
        if (device == null) return null
        val metadataInfo = try {
            val keyNames = listOf(
                "METADATA_UNTETHERED_LEFT_BATTERY",
                "METADATA_UNTETHERED_RIGHT_BATTERY",
                "METADATA_UNTETHERED_CASE_BATTERY",
                "METADATA_MAIN_BATTERY"
            )
            val getMetadataMethod = device.javaClass.getMethod("getMetadata", Int::class.javaPrimitiveType)
            val values = keyNames.map { keyName ->
                try {
                    val keyField = BluetoothDevice::class.java.getField(keyName)
                    val keyInt = keyField.getInt(null)
                    val bytes = getMetadataMethod.invoke(device, keyInt) as? ByteArray
                    bytes?.toString(Charsets.UTF_8)?.trim()?.toIntOrNull()
                } catch (e: Exception) {
                    null
                }
            }
            val (left, right, case, main) = values
            if (left == null && right == null && main == null) null else BatteryInfo(left, right, case, main)
        } catch (e: Exception) {
            null
        }
        if (metadataInfo != null) return metadataInfo

        val hfpLevel = hfpBatteryByAddress[device.address] ?: return null
        return BatteryInfo(left = null, right = null, case = null, main = hfpLevel)
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    /** All paired ("saved") devices, regardless of current connection state. */
    fun getBondedDevices(): List<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

    private var a2dpProxy: BluetoothA2dp? = null

    private val codecConfigReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED") {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val codecStatusObj = intent.getParcelableExtra<android.os.Parcelable>("android.bluetooth.extra.CODEC_STATUS")
                if (device != null && codecStatusObj != null) {
                    if (_connectedDevice.value == null || _connectedDevice.value == device) {
                        _connectedDevice.value = device
                        parseCodecStatusObj(codecStatusObj, device)
                    }
                }
            } else if (intent?.action == BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
                updateConnectedDevicesList()
            }
        }
    }

    fun selectDevice(device: BluetoothDevice) {
        _connectedDevice.value = device
        requestCurrentCodecStatus(device)
    }

    private fun updateConnectedDevicesList() {
        val devices = a2dpProxy?.connectedDevices ?: emptyList()
        val previousDevice = _connectedDevice.value
        _connectedDevicesList.value = devices

        if (devices.isNotEmpty()) {
            if (previousDevice == null) {
                _events.tryEmit(MonitorEvent(MonitorEventType.CONNECT, devices[0].name, null))
            } else if (!devices.contains(previousDevice)) {
                stabilityTracker.recordEvent()
                _events.tryEmit(MonitorEvent(MonitorEventType.SWITCH, devices[0].name, null))
            }
            _stabilityStatus.value = stabilityTracker.computeStability()
            if (previousDevice == null || !devices.contains(previousDevice)) {
                _connectedDevice.value = devices[0]
                requestCurrentCodecStatus(devices[0])
            }
        } else {
            if (previousDevice != null) {
                stabilityTracker.recordEvent()
                _events.tryEmit(MonitorEvent(MonitorEventType.DISCONNECT, previousDevice.name, null))
                _stabilityStatus.value = stabilityTracker.computeStability()
            }
            _connectedDevice.value = null
            _codecStatus.value = null
        }
    }

    fun startMonitoring() {
        bluetoothAdapter?.getProfileProxy(context, this, BluetoothProfile.A2DP)

        val filter = IntentFilter().apply {
            addAction("android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED")
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        }
        context.registerReceiver(codecConfigReceiver, filter)
        context.registerReceiver(batteryReceiver, IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"))
    }

    fun stopMonitoring() {
        context.unregisterReceiver(codecConfigReceiver)
        context.unregisterReceiver(batteryReceiver)
        a2dpProxy?.let {
            bluetoothAdapter?.closeProfileProxy(BluetoothProfile.A2DP, it)
        }
    }

    override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
        if (profile == BluetoothProfile.A2DP) {
            a2dpProxy = proxy as BluetoothA2dp
            updateConnectedDevicesList()
        }
    }

    override fun onServiceDisconnected(profile: Int) {
        if (profile == BluetoothProfile.A2DP) {
            a2dpProxy = null
            _codecStatus.value = null
            _connectedDevice.value = null
        }
    }

    private fun requestCurrentCodecStatus(device: BluetoothDevice?) {
        if (device == null || a2dpProxy == null) return
        try {
            // Use reflection to call hidden getCodecStatus method
            val method = a2dpProxy!!.javaClass.getDeclaredMethod("getCodecStatus", BluetoothDevice::class.java)
            val statusObj = method.invoke(a2dpProxy, device)
            if (statusObj != null) {
                parseCodecStatusObj(statusObj, device)
            }
        } catch (e: Exception) {
            Log.e("BluetoothMonitor", "Reflection failed for getCodecStatus: ${e.message}")
        }
    }

    private fun parseCodecStatusObj(statusObj: Any, device: BluetoothDevice? = null) {
        try {
            // Get codecConfig from BluetoothCodecStatus
            val getCodecConfigMethod = statusObj.javaClass.getDeclaredMethod("getCodecConfig")
            val configObj = getCodecConfigMethod.invoke(statusObj)
            
            if (configObj != null) {
                val getCodecTypeMethod = configObj.javaClass.getDeclaredMethod("getCodecType")
                val getSampleRateMethod = configObj.javaClass.getDeclaredMethod("getSampleRate")
                val getBitsPerSampleMethod = configObj.javaClass.getDeclaredMethod("getBitsPerSample")
                
                val codecType = getCodecTypeMethod.invoke(configObj) as Int
                val sampleRateMask = getSampleRateMethod.invoke(configObj) as Int
                val bitsPerSampleMask = getBitsPerSampleMethod.invoke(configObj) as Int
                
                val codecName = when (codecType) {
                    0 -> "SBC"
                    1 -> "AAC"
                    2 -> "aptX"
                    3 -> "aptX HD"
                    4 -> "LDAC"
                    else -> "Unknown ($codecType)"
                }
                
                val sampleRate = decodeSampleRate(sampleRateMask)
                val bitDepth = decodeBitsPerSample(bitsPerSampleMask)
                val estimatedBitrate = estimateBitrate(codecType, sampleRate, bitDepth)
                
                _codecStatus.value = CodecStatus(
                    codecName = codecName,
                    sampleRate = sampleRate,
                    bitDepth = bitDepth,
                    estimatedBitrate = estimatedBitrate
                )

                val mac = device?.address
                if (mac != null) {
                    val previousCodec = lastCodecNameByMac[mac]
                    if (previousCodec != null && previousCodec != codecName) {
                        if (stabilityTracker.isDowngrade(previousCodec, codecName)) {
                            stabilityTracker.recordEvent()
                            _events.tryEmit(MonitorEvent(MonitorEventType.DOWNGRADE, device.name, codecName))
                        } else if (stabilityTracker.isDowngrade(codecName, previousCodec)) {
                            _events.tryEmit(MonitorEvent(MonitorEventType.UPGRADE, device.name, codecName))
                        } else {
                            _events.tryEmit(MonitorEvent(MonitorEventType.CODEC_CHANGE, device.name, codecName))
                        }
                        _stabilityStatus.value = stabilityTracker.computeStability()
                    }
                    if (lastCodecNameByMac.size >= maxTrackedMacs && mac !in lastCodecNameByMac) {
                        val oldest = lastCodecNameByMac.keys.firstOrNull()
                        if (oldest != null) lastCodecNameByMac.remove(oldest)
                    }
                    lastCodecNameByMac[mac] = codecName
                }
            }
        } catch (e: Exception) {
            Log.e("BluetoothMonitor", "Failed to parse codec status: ${e.message}")
        }
    }

    private fun decodeSampleRate(mask: Int): Int {
        return when {
            (mask and 0x01) != 0 -> 44100
            (mask and 0x02) != 0 -> 48000
            (mask and 0x04) != 0 -> 88200
            (mask and 0x08) != 0 -> 96000
            (mask and 0x10) != 0 -> 176400
            (mask and 0x20) != 0 -> 192000
            else -> 0
        }
    }

    private fun decodeBitsPerSample(mask: Int): Int {
        return when {
            (mask and 0x01) != 0 -> 16
            (mask and 0x02) != 0 -> 24
            (mask and 0x04) != 0 -> 32
            else -> 0
        }
    }

    private fun estimateBitrate(codecType: Int, sampleRate: Int, bitDepth: Int): Int {
        // Very rough estimations based on theoretical max/target bitrates of codecs
        return when (codecType) {
            0 -> 328 // SBC Max
            1 -> 256 // AAC Target
            2 -> 352 // aptX Target
            3 -> 576 // aptX HD Target
            4 -> if (sampleRate >= 96000) 990 else if (sampleRate >= 48000) 660 else 330 // LDAC varies
            else -> 0
        }
    }
}
