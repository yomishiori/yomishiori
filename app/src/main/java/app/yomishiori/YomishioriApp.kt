package app.yomishiori

import android.app.Application
import android.content.Context

class YomishioriApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as YomishioriApp).container
