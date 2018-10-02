package slak.ratbwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.support.annotation.IdRes
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

private const val PREF_DIR_REVERSE = "dir_reverse"
const val PREF_LINE_NR = "pref_LINENR"
const val PREF_STOP_TO = "pref_stop_to"
const val PREF_STOP_FROM = "pref_stop_from"

fun SharedPreferences.isReverse() = getBoolean(PREF_DIR_REVERSE, false)
fun SharedPreferences.lineNr() = getInt(PREF_LINE_NR, 185)
fun SharedPreferences.stopId() = getInt(if (isReverse()) PREF_STOP_FROM else PREF_STOP_TO, 0)

class RATBWidgetProvider : AppWidgetProvider() {
  companion object {
    private const val TAG = "RATBWidgetProvider"

    const val EXTRA_STOP_LIST = "extra_stop_list"

    const val ACTION_SELECT_LINE = "select line"
    const val ACTION_SELECT_STOP_TO = "select stop to"
    const val ACTION_SELECT_STOP_FROM = "select stop from"

    private const val ACTION_LINE_CHANGE = "blablabla action line change"
    private const val ACTION_TOGGLE_DIR = "toggle direction"
    private const val ACTION_STOP_CHANGE = "change_stop"

    private val reqCodes = generateSequence(0) { it + 1 }
  }

  private fun showSchedule(context: Context, views: RemoteViews, schedule: Schedule) {
    fun padNr(nr: Int): String = nr.toString().padStart(2, '0')
    fun buildTime(moment: Moment): String = "${padNr(moment / 100)}:${padNr(moment % 100)}"
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val currentMoment = now.hour * 100 + now.minute
    val moments = schedule.pickList(now.dayOfWeek).orElse {
      views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.not_available))
      views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.not_available))
      return@showSchedule
    }.flatten()
    val nextIdx = moments.indices.firstOrNull { idx -> currentMoment <= moments[idx] } ?: 0
    val next = buildTime(moments[nextIdx])
    val prevIdx = if (nextIdx == 0) moments.size - 1 else nextIdx - 1
    val prev = buildTime(moments[prevIdx])
    views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.prev_time, prev))
    views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.next_time, next))
  }

  private fun showRoute(context: Context, views: RemoteViews, route: Route, reverse: Boolean) {
    val stops = if (reverse) route.stopsFrom else route.stopsTo
    val routeText =
        context.resources.getString(R.string.route, stops.first().name, stops.last().name)
    views.setTextViewText(R.id.route, routeText)
  }

  private fun buildIntent(context: Context,
                          views: RemoteViews,
                          appWidgetIds: IntArray,
                          @IdRes id: Int,
                          use: (Intent) -> Unit = {}) {
    val intent = Intent(context, this@RATBWidgetProvider::class.java)
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
    use(intent)
    views.setOnClickPendingIntent(id, PendingIntent.getBroadcast(
        context, reqCodes.take(1).single(), intent, PendingIntent.FLAG_UPDATE_CURRENT))
  }

  private fun callUpdate(context: Context, appWidgetIds: IntArray) {
    onUpdate(context, AppWidgetManager.getInstance(context), appWidgetIds)
  }

  override fun onReceive(context: Context, intent: Intent) {
    val p = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    when (intent.action) {
      ACTION_TOGGLE_DIR -> {
        p.use { putBoolean(PREF_DIR_REVERSE, !p.isReverse()) }
        callUpdate(context, intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS))
      }
      ACTION_LINE_CHANGE -> {
        val newIntent = Intent(context, PhonyDialog::class.java)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        newIntent.action = ACTION_SELECT_LINE
        context.startActivity(newIntent)
      }
      ACTION_STOP_CHANGE -> {
        val newIntent = Intent(context, PhonyDialog::class.java)
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        newIntent.action = if (p.isReverse()) ACTION_SELECT_STOP_FROM else ACTION_SELECT_STOP_TO
        newIntent.putParcelableArrayListExtra(EXTRA_STOP_LIST,
            intent.getParcelableArrayListExtra(EXTRA_STOP_LIST))
        context.startActivity(newIntent)
      }
      else -> super.onReceive(context, intent)
    }
  }

  override fun onUpdate(context: Context,
                        appWidgetManager: AppWidgetManager,
                        ids: IntArray) {
    val p = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    val views = RemoteViews(context.packageName, R.layout.initial_layout)

    buildIntent(context, views, ids, R.id.refreshBtn) {
      it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
    }
    buildIntent(context, views, ids, R.id.lineNumber) { it.action = ACTION_LINE_CHANGE }
    buildIntent(context, views, ids, R.id.swapDirBtn) { it.action = ACTION_TOGGLE_DIR }

    // Defaults
    views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.prev_time, "?"))
    views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.next_time, "?"))
    views.setTextViewText(R.id.route, context.resources.getString(R.string.route, "?", "?"))

    val line = p.lineNr()
    views.setTextViewText(R.id.lineNumber, line.toString())

    launch(UI) {
      val route = getRoute(line).await() ?: return@launch
      Log.d(TAG, "getRoute($line): $route")
      showRoute(context, views, route, p.isReverse())

      val stops = if (p.isReverse()) route.stopsFrom else route.stopsTo
      val targetStop = stops.find { it.stopId == p.stopId() } ?: stops[0]
      views.setTextViewText(R.id.stop, targetStop.name)

      buildIntent(context, views, ids, R.id.stop) {
        it.action = ACTION_STOP_CHANGE
        it.putParcelableArrayListExtra(EXTRA_STOP_LIST, ArrayList(stops))
      }

      val schedule = getSchedule(route, targetStop).await() ?: return@launch
      Log.d(TAG, "getSchedule(<route>, $targetStop): $schedule")
      showSchedule(context, views, schedule)
    }.invokeOnCompletion {
      appWidgetManager.updateAppWidget(ids, views)
    }
  }
}
