package com.bitchat.app.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class WebRTCService : Service() {
    
    companion object {
        private const val TAG = "WebRTCService"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WebRTC Service created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WebRTC Service started")
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WebRTC Service destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}