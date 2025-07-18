package com.bitchat.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.bitchat.app.mesh.MeshNetworkManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MeshNetworkService : Service() {
    
    companion object {
        private const val TAG = "MeshNetworkService"
    }
    
    @Inject
    lateinit var meshNetworkManager: MeshNetworkManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Mesh Network Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Mesh Network Service started")
        // الشبكة الشبكية تبدأ تلقائياً مع المدير
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Mesh Network Service destroyed")
        meshNetworkManager.destroy()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}