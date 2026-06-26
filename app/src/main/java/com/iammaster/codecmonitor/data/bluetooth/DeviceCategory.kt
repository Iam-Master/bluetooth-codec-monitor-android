package com.iammaster.codecmonitor.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice

enum class DeviceCategory { HEADPHONES, CAR, PHONE, COMPUTER, OTHER }

@SuppressLint("MissingPermission")
fun BluetoothDevice.category(): DeviceCategory {
    val btClass = try { bluetoothClass } catch (e: Exception) { null } ?: return DeviceCategory.OTHER
    return when (btClass.deviceClass) {
        BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET,
        BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES,
        BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> DeviceCategory.HEADPHONES
        BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO,
        BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> DeviceCategory.CAR
        else -> when (btClass.majorDeviceClass) {
            BluetoothClass.Device.Major.PHONE -> DeviceCategory.PHONE
            BluetoothClass.Device.Major.COMPUTER -> DeviceCategory.COMPUTER
            // Most other bonded audio/video devices (many earbuds report an uncategorized
            // or vendor-specific minor class) are headphones rather than truly "other".
            BluetoothClass.Device.Major.AUDIO_VIDEO -> DeviceCategory.HEADPHONES
            // Some earbuds (e.g. CMF/Nothing) don't expose a usable class at all and report
            // UNCATEGORIZED for both major and minor class. Fall back to their advertised
            // Bluetooth service UUIDs (A2DP sink / AVRCP target = audio-receiving accessory),
            // then to a name keyword check, before giving up.
            else -> if (looksLikeHeadphones()) DeviceCategory.HEADPHONES else DeviceCategory.OTHER
        }
    }
}

@SuppressLint("MissingPermission")
private fun BluetoothDevice.looksLikeHeadphones(): Boolean {
    val audioUuids = setOf("0000110b", "0000110e", "0000110c", "0000111e")
    val hasAudioUuid = try {
        uuids?.any { parcelUuid ->
            audioUuids.any { parcelUuid.uuid.toString().startsWith(it) }
        } == true
    } catch (e: Exception) { false }
    if (hasAudioUuid) return true

    val keywords = listOf("buds", "earbud", "earphone", "headphone", "headset", "airpods")
    return name?.lowercase()?.let { n -> keywords.any { n.contains(it) } } == true
}

/** Only fetch/display a web product photo for devices that are actually headphones/earbuds. */
fun DeviceCategory.isPhotoEligible(): Boolean = this == DeviceCategory.HEADPHONES
