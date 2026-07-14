package com.campus.lostandfound

import android.app.Application
import com.campus.lostandfound.di.AppContainer

class CampusLostAndFoundApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
