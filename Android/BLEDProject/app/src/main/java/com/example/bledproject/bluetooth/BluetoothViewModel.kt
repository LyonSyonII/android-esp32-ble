package com.example.bledproject.bluetooth


import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.ActivityCompat
import com.example.bledproject.R
import com.example.bledproject.data.UserStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID


class BluetoothViewModel(
	myContext: Context,
	bluetoothAdapter: BluetoothAdapter,
	val userStore: UserStore,
	val messageHandler: (String) -> Unit
) {
	val devices = mutableStateListOf<BluetoothDevice>()

	val scanning = mutableStateOf(false)
	val context = myContext

	private val manuallyDisconnected = mutableStateOf(false)

	// state of connection
	var connected = mutableStateOf(false)
	var connectedDevice = mutableStateOf("")

	// TODO: remove receivedData and use messageHandler instead
	var receivedData = mutableStateOf("")

	var thisGatt: BluetoothGatt? = null
	var writeCharacteristic: BluetoothGattCharacteristic? = null

	private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
	private val scanCallback = object : ScanCallback() {
		override fun onScanResult(
			callbackType: Int,
			result: ScanResult
		) {
			val device = result.device
			// only add if not already in list
			if (!devices.contains(device)) devices.add(device)
		}
	}
	val gattObject = object : BluetoothGattCallback() {
		override fun onConnectionStateChange(
			gatt: BluetoothGatt,
			status: Int,
			newState: Int
		) {
			super.onConnectionStateChange(
				gatt,
				status,
				newState
			)
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Log.d(
					"GattCallback",
					"Successfully connected to device"
				)
				connected.value = true
				manuallyDisconnected.value = false
				saveDevice(gatt.device)
				if (ActivityCompat.checkSelfPermission(
						context,
						Manifest.permission.BLUETOOTH_CONNECT
					) != PackageManager.PERMISSION_GRANTED
				) {
					// TODO: Consider calling
					//    ActivityCompat#requestPermissions
					// here to request the missing permissions, and then overriding
					//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
					//                                          int[] grantResults)
					// to handle the case where the user grants the permission. See the documentation
					// for ActivityCompat#requestPermissions for more details.
					return
				}
				gatt.discoverServices()
			} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
				Log.d(
					"GattCallback",
					"Successfully disconnected from device"
				)
				connected.value = false
				connectedDevice.value = ""
				if (!manuallyDisconnected.value) {
					// automatically reconnect
					connectToDevice(gatt.device)
				}
			}
		}

		override fun onServicesDiscovered(
			gatt: BluetoothGatt?,
			status: Int
		) {
			super.onServicesDiscovered(
				gatt,
				status
			)
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Log.d(
					"GattCallback",
					"Discovered Services"
				)
				thisGatt = gatt
				gatt?.services?.forEach { service ->
					Log.d(
						"GattCallback",
						"Service: ${service.uuid}"
					)
					service.characteristics.forEach { characteristic ->
						Log.d(
							"GattCallback",
							"Characteristic: ${characteristic.uuid}"
						)
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            // TODO: Consider calling
                            //    ActivityCompat#requestPermissions
                            // here to request the missing permissions, and then overriding
                            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                            //                                          int[] grantResults)
                            // to handle the case where the user grants the permission. See the documentation
                            // for ActivityCompat#requestPermissions for more details.
                            return
                        }
                        gatt.readCharacteristic(characteristic);


						characteristic.descriptors.forEach { descriptor ->
							Log.d(
								"GattCallback",
								"Descriptor: ${descriptor.uuid}"
							)
						}
					}
				}
			} else {
				Log.d(
					"GattCallback",
					"Failed to discover services"
				)
			}
		}

		override fun onCharacteristicChanged(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			value: ByteArray
		) {
			super.onCharacteristicChanged(
				gatt,
				characteristic,
				value
			)
			Log.d(
				"GattCallback",
				"Characteristic changed"
			)
			if (characteristic.uuid == UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")) {
				// Characteristic changed
				val readValue = String(characteristic.value)
				Log.d(
					"BluetoothScreen",
					"Value: $readValue"
				)
				receivedData.value = readValue
				messageHandler(readValue)
			}
		}

		override fun onCharacteristicRead(
			gatt: BluetoothGatt,
			characteristic: BluetoothGattCharacteristic,
			value: ByteArray,
			status: Int
		) {

			val s = value.toString(Charsets.UTF_8);
			println("Read characteristic: $s")
			receivedData.value = s
		}
	}

	fun startScan() {
		scanning.value = true
		val scanSettings = ScanSettings.Builder()
			.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
			.build()
		val scanFilter = ScanFilter.Builder()
			.build()
		val scanFilters = listOf(scanFilter)

		if (ActivityCompat.checkSelfPermission(
				context,
				Manifest.permission.BLUETOOTH_SCAN
			) != PackageManager.PERMISSION_GRANTED
		) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return
		}
		bluetoothLeScanner.startScan(
			scanFilters,
			scanSettings,
			scanCallback
		)

	}

	fun writeCharacteristic(message: String) {
		val characteristic = writeCharacteristic ?: return
		characteristic.setValue(message.toByteArray(Charsets.UTF_8))
		if (ActivityCompat.checkSelfPermission(
				context,
				Manifest.permission.BLUETOOTH_CONNECT
			) != PackageManager.PERMISSION_GRANTED
		) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return
		}
		thisGatt?.writeCharacteristic(characteristic)
	}

	fun stopScan() {
		scanning.value = false
		if (ActivityCompat.checkSelfPermission(
				context,
				Manifest.permission.BLUETOOTH_SCAN
			) != PackageManager.PERMISSION_GRANTED
		) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return
		}
		bluetoothLeScanner.stopScan(scanCallback)
	}

	fun connectToDevice(device: BluetoothDevice) {
		if (ActivityCompat.checkSelfPermission(
				context,
				Manifest.permission.BLUETOOTH_CONNECT
			) != PackageManager.PERMISSION_GRANTED
		) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return
		}


		device.connectGatt(
			context,
			false,
			gattObject
		)
		connectedDevice.value = device.address
	}

	fun disconnect() {
		// disconnect from device
		if (ActivityCompat.checkSelfPermission(
				context,
				Manifest.permission.BLUETOOTH_CONNECT
			) != PackageManager.PERMISSION_GRANTED
		) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return
		}
		thisGatt?.disconnect()
		manuallyDisconnected.value = true
	}

	fun saveDevice(device: BluetoothDevice) {
		// save device address
		CoroutineScope(Dispatchers.IO).launch {
			device.address?.let {
				userStore.saveToken(
					context.getString(R.string.bluetoothDeviceAddress),
					it
				)
			}
			Log.d(
				"GattCallback",
				"Saved bluetooth device address: " + userStore.getAccessToken(context.getString(R.string.bluetoothDeviceAddress))
			)
		}
	}


}