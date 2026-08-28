package com.kabutarbaazi.app

import android.app.Application
import androidx.media3.common.util.UnstableApi
import com.kabutarbaazi.app.data.AppContainer

@UnstableApi
class KabutarBaaziApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
