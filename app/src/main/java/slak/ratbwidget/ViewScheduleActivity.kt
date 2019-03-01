package slak.ratbwidget

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import kotlinx.android.synthetic.main.activity_view_schedule.*
import kotlinx.coroutines.runBlocking
import org.threeten.bp.DayOfWeek
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_WIDGET_ID

/** Display the entire schedule of the current stop on the current route. */
class ViewScheduleActivity : AppCompatActivity() {
  /** Turn a [TimeList] from a [schedule] into text. */
  private fun buildScheduleText(dayOfWeek: DayOfWeek, schedule: Schedule): CharSequence {
    val timeList = schedule.pickList(dayOfWeek) ?: return resources.getString(R.string.not_available)
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val spannableStringBuilder = SpannableStringBuilder()
    for ((hour, minuteTimes) in timeList.withIndex()) {
      val bold = schedule.pickList(now.dayOfWeek) == timeList && now.hour == hour
      val hourStr = "${padNr(hour)}:00"
      val str = hourStr + " ".repeat(4) + minuteTimes.joinToString(", ") { "${padNr(hour)}:${padNr(it)}" }
      val withSpans = SpannableString(str)
      if (bold) withSpans.setSpan(ForegroundColorSpan(getColor(R.color.highlighted_hour)),
          0, hourStr.length, SPAN_INCLUSIVE_EXCLUSIVE)
      spannableStringBuilder.appendln(withSpans)
    }
    return spannableStringBuilder
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
      title = getString(R.string.schedule_for_title, line.nr, targetStop.name)
      supportActionBar!!.subtitle = getString(R.string.route, stops[0].name, stops.last().name)
      val schedule = getSchedule(route, targetStop).await() ?: return@runBlocking finish()
      dailySchedule.text = buildScheduleText(DayOfWeek.MONDAY, schedule)
      saturdaySchedule.text = buildScheduleText(DayOfWeek.SATURDAY, schedule)
      sundaySchedule.text = buildScheduleText(DayOfWeek.SUNDAY, schedule)
    }
  }
}
