package com.example.solidfit.healthdata

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID
import android.bluetooth.le.ScanResult


class HeartRateBleManager(
    private val context: Context,
    private val onDeviceFound: (BluetoothDevice) -> Unit,
    private val onBpm: (Int) -> Unit,
    private val onDisconnect: ()-> Unit
) {
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180D-0000-1000-8000-00805F9B34FB")
    private val HEART_RATE_MEASUREMENT_CHAR_UUID = UUID.fromString("00002A37-0000-1000-8000-00805F9B34FB")
    private val CLIENT_CFG_UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter by lazy {
        val mgr = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        mgr.adapter
    }
    private val scanner = bluetoothAdapter.bluetoothLeScanner
    private var gatt: BluetoothGatt? = null

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onDeviceFound(result.device)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                g.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                g.close()
                onDisconnect()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val svc = g.getService(HEART_RATE_SERVICE_UUID) ?: return
            val char = svc.getCharacteristic(HEART_RATE_MEASUREMENT_CHAR_UUID) ?: return

            // Enable notifications
            g.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(CLIENT_CFG_UUID) ?: return
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            g.writeDescriptor(descriptor)
        }

        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == HEART_RATE_MEASUREMENT_CHAR_UUID) {
                val flags = characteristic.properties
                // bit0=0: UINT8, =1: UINT16
                val format = if (flags and 0x01 != 0)
                    BluetoothGattCharacteristic.FORMAT_UINT16
                else
                    BluetoothGattCharacteristic.FORMAT_UINT8

                // offset 1 holds the BPM value
                val bpm = characteristic.getIntValue(format, 1) ?: return
                onBpm(bpm)
            }
        }
    }

    fun startScan() {
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    // Called once user selects bluetooth device from the list
    fun connectTo(device: BluetoothDevice) {
        // Stops the scanning
        scanner.stopScan(scanCallback)
        gatt = device.connectGatt(context, false, gattCallback)
    }

    fun stop() {
        scanner.stopScan(scanCallback)
        gatt?.disconnect()
        gatt?.close()
    }
}
