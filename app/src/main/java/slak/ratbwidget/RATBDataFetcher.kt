package slak.ratbwidget

import android.os.Parcelable
import android.support.annotation.WorkerThread
import android.util.Log
import kotlinx.android.parcel.Parcelize
import org.jsoup.Jsoup
import org.threeten.bp.DayOfWeek
import java.net.URL
import java.util.concurrent.TimeUnit

/** Stores a list of hours, each hour containing a list of minutes. */
typealias TimeList = List<List<Int>>
/** Identifies a stop in the remote data set. */
@Parcelize
data class StopId(val id: Int) : Parcelable
/** Stores the data for a stop on a route. */
@Parcelize
data class Stop(val name: String, val stopId: StopId) : Parcelable
/** The number of a line. */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class Line(val nr: Int)
/** Stores data related to a particular route, and the stops on it. */
data class Route(val line: Line, val stopsTo: List<Stop>, val stopsFrom: List<Stop>)
/** Stores all the data required to build a schedule for any time. */
data class Schedule(val line: Line,
                    val stop: Stop,
                    private val daily: TimeList?,
                    private val saturday: TimeList?,
                    private val sunday: TimeList?) {
  /** Get the correct [TimeList] for the given [dayOfWeek]. */
  fun pickList(dayOfWeek: DayOfWeek) = when (dayOfWeek) {
    DayOfWeek.SATURDAY -> saturday
    DayOfWeek.SUNDAY -> sunday
    else -> daily
  }
}

/** An [Int] of the form HHMM (hour, minute). */
@Suppress("EXPERIMENTAL_FEATURE_WARNING")
inline class Moment(val t: Int) : Comparable<Moment> {
  override fun compareTo(other: Moment): Int = t.compareTo(other.t)
}

/** Converts a [TimeList] to a list of [Moment]s. */
fun TimeList.flatten(): List<Moment> {
  val flat = mutableListOf<Moment>()
  forEachIndexed { hour, hourList -> hourList.forEach { minute -> flat.add(Moment(hour * 100 + minute)) } }
  return flat
}

val requestCache = Cache<String>("Request", TimeUnit.DAYS.toMillis(7))
private const val TAG = "Request"

private var sessionCookie: String? = null
/** Do black magic to obtain the text at the given [url]. */
@WorkerThread
private fun doConnection(url: String): String {
  val urlObj = URL(url).openConnection()
  urlObj.setRequestProperty("Cookie", "PHPSESSID=$sessionCookie;")
  val newText = urlObj.getInputStream().use { it.readBytes().toString(Charsets.UTF_8) }
  val cookies = urlObj.headerFields.entries
      .find { it.key == "Set-Cookie" }?.value?.joinToString(" ")
  if (cookies != null) {
    val sessionId = "PHPSESSID=(.*?);".toRegex().find(cookies)
    sessionCookie = sessionId?.groupValues?.get(1)
    Log.i(TAG, "session cookie: $sessionCookie")
  }
  return newText
}

/**
 * Wrap error handling for [doConnection].
 * @returns the text at [url] if successful, null otherwise
 */
@WorkerThread
private fun getData(url: String, cacheKey: String? = url): String? {
  try {
    if (cacheKey != null) {
      return requestCache.hit(cacheKey).let {
        if (it == null) {
          val newText = doConnection(url)
          requestCache.update(cacheKey, newText)
          return@let newText
        } else {
          return@let it
        }
      }
    } else {
      return doConnection(url)
    }
  } catch (t: Exception) {
    if (cacheKey != null) requestCache.remove(cacheKey)
    Log.e(TAG, "error fetching data", t)
    return null
  }
}

/** Fetch the [Route] for the given [line]. */
@WorkerThread
fun getRoute(line: Line): Route? {
  val text = getData("http://www.ratb.ro/v_bus_urban.php?tlin1=${line.nr}") ?: return null
  val doc = Jsoup.parse(text)
  val stops = doc.select(
      "table[border=\"1\"][cellpadding=\"2\"] > tbody > tr[align=\"left\"]").map { el ->
    val name = el.child(1).child(0).text().split(' ').joinToString(" ") {
      it[0] + it.substring(1).toLowerCase()
    }
    val link = el.child(0).child(0)
    val stopId = link.attr("href").split("=").last().toInt()
    return@map Stop(name, StopId(stopId))
  }
  val stopsTo = stops.filter { it.stopId.id < 50 }
  val stopsFrom = stops.filter { it.stopId.id >= 50 }
  return Route(line, stopsTo, stopsFrom)
}

/** Fetch the [Schedule] for the given [route] and [stop]. */
@WorkerThread
fun getSchedule(route: Route, stop: Stop): Schedule? {
  // Everything about this site is retarded
  getData("http://www.ratb.ro/vv_statie.php?linie=${route.line.nr}&statie=${stop.stopId.id}", null) ?: return null
  val cacheKey = "http://www.ratb.ro/v_statie.php ${route.line.nr} ${stop.stopId.id}"
  val realResponse = getData("http://www.ratb.ro/v_statie.php", cacheKey) ?: return null
  // end retarded code
  val doc = Jsoup.parse(realResponse)
  try {
    val timeLists = doc.select("table[border=\"1\"]").map { el ->
      if (el.child(0).child(1).text().startsWith("NU")) return@map null
      val dataRow = el.child(0).child(2)
      val list = mutableListOf<List<Int>>()
      // First is useless text, drop it
      // Rest are 0-23 hours
      dataRow.children().drop(1).forEach { hourTimes ->
        if (hourTimes.children().isEmpty()) list.add(emptyList())
        else list.add(hourTimes.textNodes().map { it.text().toInt() })
      }
      return@map list as TimeList
    }
    // The cache might be fucked if this is true
    if (timeLists.all { list -> (list ?: emptyList()).all { it.isEmpty() } }) {
      requestCache.remove(cacheKey)
      Log.w(TAG, "Cache might be bad")
      return getSchedule(route, stop)
    }
    return Schedule(route.line, stop, timeLists[0], timeLists[1], timeLists[2])
  } catch (e: Exception) {
    requestCache.remove(cacheKey)
    Log.e(TAG, "error parsing schedule", e)
    Log.v(TAG, realResponse)
    return null
  }
}

/** Get a list of bus lines. */
@WorkerThread
fun getBusList(): List<Int>? {
  val page = getData("http://www.ratb.ro/v_trasee.php") ?: return null
  val doc = Jsoup.parse(page)
  return doc.select("select[name=\"tlin3\"] > option").drop(1).map { it.text().toInt() }
}
