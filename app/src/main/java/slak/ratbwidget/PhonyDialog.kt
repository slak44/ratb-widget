package slak.ratbwidget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.ArrayAdapter
import android.widget.ListView
import kotlinx.coroutines.*
import org.jetbrains.anko.intentFor
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_LINE
import slak.ratbwidget.RATBWidgetProvider.Companion.ACTION_SELECT_STOP
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_STOP_LIST
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_WIDGET_ID
import kotlin.coroutines.CoroutineContext

/** An activity themed as a dialog. */
class PhonyDialog : AppCompatActivity(), CoroutineScope {
  private lateinit var job: Job
  override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job
  private var widgetId: Int = 0

  /** Create an empty [ListView] to add dialog options later. */
  private fun getInitialListView(): ListView {
    val lv = ListView(this)
    val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding)
    lv.setPadding(0, padding, 0, padding)
    return lv
  }

  /** Set the line selection dialog as the contentView. */
  private fun runSelectLine() {
    title = getString(R.string.select_line_title)
    launch {
      val items = getLineList() ?: emptyList()
      withContext(Dispatchers.Main) {
        val listView = getInitialListView()
        listView.adapter = ArrayAdapter(
            this@PhonyDialog, itemLayout, items.map { it.name })
        listView.setOnItemClickListener { _, _, position, _ ->
          p.setLineId(widgetId, Line(items[position].id))
          callUpdateWidgets()
          finish()
        }
        setContentView(listView)
      }
    }
  }

  /** Set the stop selection dialog as the contentView. */
  private fun runSelectStop() {
    title = getString(R.string.select_stop_title)
    val stops = intent!!.extras!!.getParcelableArrayList<APIStop>(EXTRA_STOP_LIST)!!
    val listView = getInitialListView()
    listView.adapter = ArrayAdapter(this, itemLayout, stops.map { it.name })
    listView.setOnItemClickListener { _, _, position, _ ->
      p.setStopId(widgetId, p.lineId(widgetId), StopId(stops[position].id))
      callUpdateWidgets()
      finish()
    }
    setContentView(listView)
  }

  /** Tell the widget to update itself. */
  private fun callUpdateWidgets() {
    val intent = intentFor<RATBWidgetProvider>()
    intent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(widgetId))
    sendBroadcast(intent)
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    job = Job()
    widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, 0)
    when (intent.action) {
      ACTION_SELECT_LINE -> runSelectLine()
      ACTION_SELECT_STOP -> runSelectStop()
      else -> throw IllegalStateException("Can't happen, lol")
    }
  }

  override fun onPause() {
    super.onPause()
    finish()
  }

  override fun onDestroy() {
    super.onDestroy()
    job.cancel()
  }

  companion object {
    private const val itemLayout = R.layout.select_dialog_singlechoice_material
  }
}
