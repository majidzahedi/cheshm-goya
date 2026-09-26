package ir.cheshmgoya.app

import android.app.Application

class CheshmGoyaApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
