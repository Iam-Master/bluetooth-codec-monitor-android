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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class CodecStatus(
    val codecName: String,
    val sampleRate: Int,
    val bitDepth: Int,
    val estimatedBitrate: Int,
    val isEstimated: Boolean = true
)

@SuppressLint("MissingPermission") // Permissions are handled in UI
class BluetoothMonitor(private val context: Context) : BluetoothProfile.ServiceListener {

    private val _codecStatus = MutableStateFlow<CodecStatus?>(null)
    val codecStatus: StateFlow<CodecStatus?> = _codecStatus

    private val _connectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val connectedDevice: StateFlow<BluetoothDevice?> = _connectedDevice

    private val _connectedDevicesList = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val connectedDevicesList: StateFlow<List<BluetoothDevice>> = _connectedDevicesList

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        manager.adapter
    }

    private var a2dpProxy: BluetoothA2dp? = null

    private val codecConfigReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "android.bluetooth.a2dp.profile.action.CODEC_CONFIG_CHANGED") {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val codecStatusObj = intent.getParcelableExtra<android.os.Parcelable>("android.bluetooth.extra.CODEC_STATUS")
                if (device != null && codecStatusObj != null) {
                    if (_connectedDevice.value == null || _connectedDevice.value == device) {
                        _connectedDevice.value = device
                        parseCodecStatusObj(codecStatusObj)
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
        _connectedDevicesList.value = devices
        
        if (devices.isNotEmpty()) {
            if (_connectedDevice.value == null || !devices.contains(_connectedDevice.value)) {
                _connectedDevice.value = devices[0]
                requestCurrentCodecStatus(devices[0])
            }
        } else {
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
    }

    fun stopMonitoring() {
        context.unregisterReceiver(codecConfigReceiver)
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
                parseCodecStatusObj(statusObj)
            }
        } catch (e: Exception) {
            Log.e("BluetoothMonitor", "Reflection failed for getCodecStatus: ${e.message}")
        }
    }

    private fun parseCodecStatusObj(statusObj: Any) {
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
