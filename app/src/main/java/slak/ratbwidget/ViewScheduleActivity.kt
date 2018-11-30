package slak.ratbwidget

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_view_schedule.*
import kotlinx.coroutines.runBlocking
import org.threeten.bp.DayOfWeek
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_WIDGET_ID

/** Display the entire schedule of the current stop on the current route. */
class ViewScheduleActivity : AppCompatActivity() {
  /** Turn a [TimeList] from a [schedule] into text. */
  private fun buildScheduleText(dayOfWeek: DayOfWeek, schedule: Schedule): String {
    val timeList = schedule.pickList(dayOfWeek) ?: return resources.getString(R.string.not_available)
    return timeList.asSequence().mapIndexed { hour, minuteTimes ->
      "${padNr(hour)}:00    " + minuteTimes.joinToString(", ") { "${padNr(hour)}:${padNr(it)}" }
    }.joinToString("\n")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_view_schedule)
    setSupportActionBar(toolbar)
    val id = intent.getIntExtra(EXTRA_WIDGET_ID, 0)
    runBlocking {
      val line = p.lineNr(id)
      val route = getRoute(line).await() ?: return@runBlocking finish()
      val stops = if (p.isReverse(id, line)) route.stopsFrom else route.stopsTo
      val targetStop = stops.find { it.stopId == p.stopId(id, line) } ?: stops[0]
      title = getString(R.string.schedule_for_title, line, targetStop.name)
      supportActionBar!!.subtitle = getString(R.string.route, stops[0].name, stops.last().name)
      val schedule = getSchedule(route, targetStop).await() ?: return@runBlocking finish()
      dailySchedule.text = buildScheduleText(DayOfWeek.MONDAY, schedule)
      saturdaySchedule.text = buildScheduleText(DayOfWeek.SATURDAY, schedule)
      sundaySchedule.text = buildScheduleText(DayOfWeek.SUNDAY, schedule)
    }
  }
}
