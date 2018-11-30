package slak.ratbwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.support.annotation.UiThread
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ListView
import kotlinx.coroutines.runBlocking
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_LINE
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_STOP
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_STOP_LIST

/** An activity themed as a dialog. */
class PhonyDialog : AppCompatActivity() {
  companion object {
    private const val itemLayout = R.layout.select_dialog_singlechoice_material
  }

  /** Create an empty [ListView] to add dialog options later. */
  private fun getInitialListView(): ListView {
    val lv = ListView(this)
    val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
    lv.setPadding(0, padding, 0, padding)
    return lv
  }

  /** Set the line selection dialog as the contentView. */
  @UiThread
  private suspend fun runSelectLine() {
    title = getString(R.string.select_line_title)
    val items = getBusList().await() ?: emptyList()
    val listView = getInitialListView()
    listView.adapter = ArrayAdapter<Int>(
        this@PhonyDialog, itemLayout, items)
    listView.setOnItemClickListener { _, _, position, _ ->
      p.lineNr = items[position]
      callUpdateWidgets()
      finish()
    }
    setContentView(listView)
  }

  /** Set the stop selection dialog as the contentView. */
  private fun runSelectStop() {
    title = getString(R.string.select_stop_title)
    val stops = intent!!.extras!!.getParcelableArrayList<Stop>(EXTRA_STOP_LIST)!!
    val listView = getInitialListView()
    listView.adapter = ArrayAdapter<String>(this, itemLayout, stops.map { it.name })
    listView.setOnItemClickListener { _, _, position, _ ->
      p.setStopId(p.lineNr, stops[position].stopId)
      callUpdateWidgets()
      finish()
    }
    setContentView(listView)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    when (intent.action) {
      // We can use runBlocking here because the UI is stuck waiting anyway
      ACTION_SELECT_LINE -> runBlocking { runSelectLine() }
      ACTION_SELECT_STOP -> runSelectStop()
      else -> throw IllegalStateException("Can't happen, lol")
    }
  }

  override fun onPause() {
    super.onPause()
    finish()
  }

  /** Programatically update the widgets. */
  private fun callUpdateWidgets() {
    // From https://stackoverflow.com/a/7738687
    val widgetProvider = RATBWidgetProvider::class.java
    val intent = Intent(this, widgetProvider)
    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    val ids = AppWidgetManager.getInstance(application).getAppWidgetIds(ComponentName(application, widgetProvider))
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    sendBroadcast(intent)
  }
}
