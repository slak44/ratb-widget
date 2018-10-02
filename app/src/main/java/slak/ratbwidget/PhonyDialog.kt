package slak.ratbwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ListView
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_LINE
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_STOP_FROM
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_STOP_TO
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_STOP_LIST

class PhonyDialog : AppCompatActivity() {
  companion object {
    private const val itemLayout = R.layout.select_dialog_singlechoice_material
  }

  private lateinit var p: SharedPreferences

  private fun getInitialListView(): ListView {
    val lv = ListView(this)
    val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
    lv.setPadding(0, padding, 0, padding)
    return lv
  }

  private fun runSelectLine(): Deferred<ListView> = async2(UI) {
    title = getString(R.string.select_line_title)
    val items = getBusList().await() ?: emptyList()
    val listView = getInitialListView()
    listView.adapter = ArrayAdapter<Int>(
        this@PhonyDialog, itemLayout, items)
    listView.setOnItemClickListener { _, _, position, _ ->
      p.use { putInt(PREF_LINE_NR, items[position]) }
      callUpdateWidgets()
      finish()
    }
    return@async2 listView
  }

  private fun runSelectStop(action: String): ListView {
    title = getString(R.string.select_stop_title)
    val stops = intent.extras.getParcelableArrayList<Stop>(EXTRA_STOP_LIST)
    val listView = getInitialListView()
    listView.adapter = ArrayAdapter<String>(this, itemLayout, stops.map { it.name })
    listView.setOnItemClickListener { _, _, position, _ ->
      val name = if (action == ACTION_SELECT_STOP_TO) PREF_STOP_TO else PREF_STOP_FROM
      p.use { putInt(name, stops[position].stopId) }
      callUpdateWidgets()
      finish()
    }
    return listView
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    p = PreferenceManager.getDefaultSharedPreferences(this@PhonyDialog)
    launch(UI) {
      setContentView(when (intent.action) {
        ACTION_SELECT_LINE -> runSelectLine().await()
        ACTION_SELECT_STOP_TO,
        ACTION_SELECT_STOP_FROM -> runSelectStop(intent.action)
        else -> throw IllegalStateException("Can't happen, lol")
      })
    }
  }

  override fun onPause() {
    super.onPause()
    finish()
  }

  private fun callUpdateWidgets() {
    // From https://stackoverflow.com/a/7738687
    val intent = Intent(this, RATBWidgetProvider::class.java)
    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    val ids = AppWidgetManager.getInstance(application)
        .getAppWidgetIds(ComponentName(application, RATBWidgetProvider::class.java))
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    sendBroadcast(intent)
  }
}
