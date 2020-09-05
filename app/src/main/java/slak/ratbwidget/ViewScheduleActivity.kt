package slak.ratbwidget

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_INCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import kotlinx.android.synthetic.main.activity_view_schedule.*
import kotlinx.coroutines.*
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import slak.ratbwidget.RATBWidgetProvider.Companion.EXTRA_WIDGET_ID
import kotlin.coroutines.CoroutineContext

/** Display the entire schedule of the current stop on the current route. */
class ViewScheduleActivity : AppCompatActivity(), CoroutineScope {
  private lateinit var job: Job
  override val coroutineContext: CoroutineContext get() = Dispatchers.IO + job

  /** Turn a list of times from a [schedule] into text. */
  private fun buildScheduleText(schedule: List<HourTimes>): CharSequence {
    val now = ZonedDateTime.now(ZoneId.systemDefault())
    val spannableStringBuilder = SpannableStringBuilder()
    for ((hourStr, minuteTimesStr) in schedule) {
      val hour = hourStr.toInt()
      val minuteTimes = minuteTimesStr.map { it.toInt() }
      val bold = now.hour == hour
      val hourBaseStr = "${padNr(hour)}:00"
      val str = hourBaseStr + " ".repeat(4) + minuteTimes.joinToString(", ") { "${padNr(hour)}:${padNr(it)}" }
      val withSpans = SpannableString(str)
      if (bold) withSpans.setSpan(ForegroundColorSpan(getColor(R.color.highlighted_hour)),
          0, hourBaseStr.length, SPAN_INCLUSIVE_EXCLUSIVE)
      spannableStringBuilder.appendLine(withSpans)
    }
    return spannableStringBuilder
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    job = Job()
    setContentView(R.layout.activity_view_schedule)
    setSupportActionBar(toolbar)
    val id = intent.getIntExtra(EXTRA_WIDGET_ID, 0)
    launch {
      val (lineData, stopData, direction, timetable) = p.fetchData(id) ?: return@launch
      withContext(Dispatchers.Main) {
        title = getString(R.string.schedule_for_title, lineData.name, stopData.name)
        supportActionBar!!.subtitle = getString(R.string.route, lineData.startStopName(direction), lineData.endStopName(direction))
        dailySchedule.text = buildScheduleText(timetable)
      }
    }
  }

  override fun onDestroy() {
    super.onDestroy()
    job.cancel()
  }
}
