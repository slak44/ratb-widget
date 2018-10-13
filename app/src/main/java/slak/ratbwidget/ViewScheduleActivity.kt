package slak.ratbwidget

import android.os.Bundle
import android.preference.PreferenceManager
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_view_schedule.*
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import org.threeten.bp.DayOfWeek

/** Display the entire schedule of the current stop on the current route. */
class ViewScheduleActivity : AppCompatActivity() {
  /** Turn a [TimeList] from a [schedule] into text. */
  private fun buildScheduleText(dayOfWeek: DayOfWeek, schedule: Schedule): String {
    val timeList = schedule.pickList(dayOfWeek).orElse { return resources.getString(R.string.not_available) }
    return timeList.asSequence().mapIndexed { hour, minuteTimes ->
      "${padNr(hour)}:00    " + minuteTimes.joinToString(", ") { "${padNr(hour)}:${padNr(it)}" }
    }.joinToString("\n")
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_view_schedule)
    setSupportActionBar(toolbar)
    val p = PreferenceManager.getDefaultSharedPreferences(this)
    launch(UI) {
      val route = getRoute(p.lineNr()).await() ?: return@launch finish()
      val stops = if (p.isReverse()) route.stopsFrom else route.stopsTo
      val targetStop = stops.find { it.stopId == p.stopId() } ?: stops[0]
      title = getString(R.string.schedule_for_title, p.lineNr(), targetStop.name)
      supportActionBar!!.subtitle = getString(R.string.route, stops[0].name, stops.last().name)
      val schedule = getSchedule(route, targetStop).await() ?: return@launch finish()
      dailySchedule.text = buildScheduleText(DayOfWeek.MONDAY, schedule)
      saturdaySchedule.text = buildScheduleText(DayOfWeek.SATURDAY, schedule)
      sundaySchedule.text = buildScheduleText(DayOfWeek.SUNDAY, schedule)
    }
  }
}
