package com.bitchat.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bitchat.app.bluetooth.BluetoothManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BluetoothMeshService : Service() {
    
    companion object {
        private const val TAG = "BluetoothMeshService"
    }
    
    @Inject
    lateinit var bluetoothManager: BluetoothManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Bluetooth Mesh Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Bluetooth Mesh Service started")
        // بدء الشبكة الشبكية
        bluetoothManager.startAdvancedMeshNetwork()
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Bluetooth Mesh Service destroyed")
        bluetoothManager.stopAdvancedMeshNetwork()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}