package slak.ratbwidget

import android.app.Application
import android.util.Log
import com.jakewharton.threetenabp.AndroidThreeTen
import java.io.File

@Suppress("unused")
class App : Application() {
  companion object {
    private const val TAG = "Application"
    lateinit var cacheDir: File
      private set
  }

  override fun onCreate() {
    super.onCreate()
    Log.d(TAG, "Initializing")
    Companion.cacheDir = cacheDir
    requestCache.deserialize()
    AndroidThreeTen.init(this)
    // Try not to let exceptions crash the app
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
      Log.e("UNCAUGHT DEFAULT", thread.toString(), throwable)
    }
  }
}
