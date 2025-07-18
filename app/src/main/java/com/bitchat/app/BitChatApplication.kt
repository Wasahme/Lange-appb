package com.bitchat.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BitChatApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // تهيئة التطبيق
        initializeApp()
    }
    
    private fun initializeApp() {
        // يمكن إضافة تهيئة إضافية هنا
        // مثل Firebase، Analytics، إلخ
    }
}