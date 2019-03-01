package slak.ratbwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.support.annotation.IdRes
import android.util.Log
import android.widget.RemoteViews
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.jetbrains.anko.intentFor
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime

fun padNr(nr: Int): String = nr.toString().padStart(2, '0')

class RATBWidgetProvider : AppWidgetProvider() {
  companion object {
    private const val TAG = "RATBWidgetProvider"

    const val EXTRA_STOP_LIST = "extra_stop_list"
    const val EXTRA_WIDGET_ID = "widget_id"

    const val ACTION_SELECT_LINE = "select_line"
    const val ACTION_SELECT_STOP = "select_stop"

    private const val ACTION_LINE_CHANGE = "action_line_change"
    private const val ACTION_TOGGLE_DIR = "toggle_direction"
    private const val ACTION_STOP_CHANGE = "change_stop"
    private const val ACTION_SHOW_ALL_SCHEDULE = "show_all_schedule"

    private var reqCodes = object {
      private var c = 0
      operator fun invoke() = c++
    }
  }

  /** Update the [RemoteViews] content using the provided [Schedule]. */
  private fun showSchedule(context: Context, views: RemoteViews, schedule: Schedule) {
    fun buildTime(moment: Moment): String = "${padNr(moment.t / 100)}:${padNr(moment.t % 100)}"
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val currentMoment = Moment(now.hour * 100 + now.minute)
    val moments = schedule.pickList(now.dayOfWeek).let {
      if (it != null) return@let it
      views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.not_available))
      views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.not_available))
      return@showSchedule
    }!!.flatten()
    val nextIdx = moments.indices.firstOrNull { idx -> currentMoment < moments[idx] } ?: 0
    val next2Idx = moments.indices.firstOrNull { idx -> moments[nextIdx] < moments[idx] } ?: 1
    val next = buildTime(moments[nextIdx])
    val next2 = buildTime(moments[next2Idx])
    val prevIdx = if (nextIdx == 0) moments.size - 1 else nextIdx - 1
    val prev = buildTime(moments[prevIdx])
    views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.prev_time, prev))
    views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.next_time, next, next2))
  }

  /** Update the [RemoteViews] content using the provided [Route]. */
  private fun showRoute(context: Context, views: RemoteViews, route: Route, reverse: Boolean) {
    val stops = if (reverse) route.stopsFrom else route.stopsTo
    val routeText = context.resources.getString(R.string.route, stops.first().name, stops.last().name)
    views.setTextViewText(R.id.route, routeText)
  }

  /** Create an [Intent] based on the given parameters and set it as the click pending intent for the view at [id]. */
  private fun buildIntent(context: Context,
                          views: RemoteViews,
                          appWidgetId: Int,
                          @IdRes id: Int,
                          use: (Intent) -> Unit = {}) {
    val intent = Intent(context, this@RATBWidgetProvider::class.java)
    intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
    use(intent)
    val reqCode = reqCodes()
    Log.v(TAG, "Building intent for $appWidgetId, reqCode=$reqCode")
    views.setOnClickPendingIntent(id,
        PendingIntent.getBroadcast(context, reqCode, intent, PendingIntent.FLAG_UPDATE_CURRENT))
  }

  override fun onReceive(context: Context, intent: Intent) {
    val id = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)?.get(0)
    Log.v(TAG, "Received broadcast: id=$id, action=${intent.action}")
    when (intent.action) {
      ACTION_TOGGLE_DIR -> {
        context.p.toggleIsReverse(id!!, context.p.lineNr(id))
        updateWidget(context, AppWidgetManager.getInstance(context), id)
      }
      ACTION_LINE_CHANGE -> {
        val newIntent = context.intentFor<PhonyDialog>()
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        newIntent.action = ACTION_SELECT_LINE
        newIntent.putExtra(EXTRA_WIDGET_ID, id)
        context.startActivity(newIntent)
      }
      ACTION_STOP_CHANGE -> {
        val newIntent = context.intentFor<PhonyDialog>()
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_NEW_TASK)
        newIntent.action = ACTION_SELECT_STOP
        newIntent.putExtra(EXTRA_WIDGET_ID, id)
        newIntent.putParcelableArrayListExtra(EXTRA_STOP_LIST, intent.getParcelableArrayListExtra(EXTRA_STOP_LIST))
        context.startActivity(newIntent)
      }
      ACTION_SHOW_ALL_SCHEDULE -> {
        val newIntent = context.intentFor<ViewScheduleActivity>()
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        newIntent.putExtra(EXTRA_WIDGET_ID, id)
        context.startActivity(newIntent)
      }
      else -> super.onReceive(context, intent)
    }
  }

  private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, id: Int) {
    Log.i(TAG, "Updating widget $id")
    val views = RemoteViews(context.packageName, R.layout.initial_layout)

    buildIntent(context, views, id, R.id.refreshBtn) { it.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE }
    buildIntent(context, views, id, R.id.lineNumber) { it.action = ACTION_LINE_CHANGE }
    buildIntent(context, views, id, R.id.swapDirBtn) { it.action = ACTION_TOGGLE_DIR }
    buildIntent(context, views, id, R.id.allMomentsBtn) { it.action = ACTION_SHOW_ALL_SCHEDULE }

    // Defaults
    views.setTextViewText(R.id.prevTime, context.resources.getString(R.string.prev_time, "?"))
    views.setTextViewText(R.id.nextTime, context.resources.getString(R.string.next_time, "?", "?"))
    views.setTextViewText(R.id.route, context.resources.getString(R.string.route, "?", "?"))

    val line = context.p.lineNr(id)
    views.setTextViewText(R.id.lineNumber, line.nr.toString())

    GlobalScope.launch(Dispatchers.Main) {
      val route = getRoute(line).await() ?: return@launch
      Log.v(TAG, "getRoute($line): $route")
      showRoute(context, views, route, context.p.isReverse(id, line))

      val stops = if (context.p.isReverse(id, line)) route.stopsFrom else route.stopsTo
      val targetStop = stops.find { it.stopId == context.p.stopId(id, line) } ?: stops[0]
      views.setTextViewText(R.id.stop, targetStop.name)

      buildIntent(context, views, id, R.id.stop) {
        it.action = ACTION_STOP_CHANGE
        it.putParcelableArrayListExtra(EXTRA_STOP_LIST, ArrayList(stops))
      }

      val schedule = getSchedule(route, targetStop).await() ?: return@launch
      Log.v(TAG, "getSchedule(<route>, $targetStop): $schedule")
      showSchedule(context, views, schedule)

      appWidgetManager.updateAppWidget(id, views)
    }
  }

  override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, ids: IntArray) {
    ids.forEach { updateWidget(context, appWidgetManager, it) }
  }
}
