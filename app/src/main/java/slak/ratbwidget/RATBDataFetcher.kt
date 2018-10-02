package slak.ratbwidget

import android.os.Parcelable
import android.util.Log
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.CommonPool
import kotlinx.coroutines.experimental.Deferred
import org.jsoup.Jsoup
import org.threeten.bp.DayOfWeek
import java.net.URL
import java.util.concurrent.TimeUnit

typealias TimeList = List<List<Int>>
@Parcelize
data class Stop(val name: String, val stopId: Int) : Parcelable
data class Route(val line: Int, val stopsTo: List<Stop>, val stopsFrom: List<Stop>)
data class Schedule(val line: Int,
                    val stop: Stop,
                    private val daily: Optional<TimeList>,
                    private val saturday: Optional<TimeList>,
                    private val sunday: Optional<TimeList>) {
  fun pickList(dayOfWeek: DayOfWeek) = when (dayOfWeek) {
    DayOfWeek.SATURDAY -> saturday
    DayOfWeek.SUNDAY -> sunday
    else -> daily
  }
}

typealias Moment = Int

fun TimeList.flatten(): List<Moment> {
  val flat = mutableListOf<Moment>()
  forEachIndexed { hour, hourList -> hourList.forEach { minute -> flat.add(hour * 100 + minute) } }
  return flat
}

val requestCache = Cache<String>("Request", TimeUnit.DAYS.toMillis(7))
private const val TAG = "Request"

private var sessionCookie: String? = null
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

private fun getData(url: String, cacheKey: Optional<String> = url.opt()): String? {
  try {
    cacheKey.ifPresent {
      return@getData requestCache.hit(it).orElse {
        val newText = doConnection(url)
        requestCache.update(it, newText)
        return@orElse newText
      }
    }
    return doConnection(url)
  } catch (t: Exception) {
    cacheKey.ifPresent { requestCache.remove(it) }
    Log.e(TAG, "error fetching data", t)
    return null
  }
}

fun getRoute(line: Int): Deferred<Route?> = async2(CommonPool) {
  // FIXME url may change based on line nr
  val text = getData("http://www.ratb.ro/v_bus_urban.php?tlin1=$line") ?: return@async2 null
  val doc = Jsoup.parse(text)
  val stops = doc.select(
      "table[border=\"1\"][cellpadding=\"2\"] > tbody > tr[align=\"left\"]").map {
    val name = it.child(1).child(0).text().split(' ').joinToString(" ") {
      it[0] + it.substring(1).toLowerCase()
    }
    val link = it.child(0).child(0)
    val stopId = link.attr("href").split("=").last().toInt()
    return@map Stop(name, stopId)
  }
  val stopsTo = stops.filter { it.stopId < 50 }
  val stopsFrom = stops.filter { it.stopId >= 50 }
  return@async2 Route(line, stopsTo, stopsFrom)
}

fun getSchedule(route: Route, stop: Stop): Deferred<Schedule?> = async2(CommonPool) {
  // This is retarded, yes
  getData("http://www.ratb.ro/vv_statie.php?linie=${route.line}&statie=${stop.stopId}", Empty())
      ?: return@async2 null
  val cacheKey = "http://www.ratb.ro/v_statie.php ${route.line} ${stop.stopId}"
  val realResponse = getData("http://www.ratb.ro/v_statie.php", cacheKey.opt())
      ?: return@async2 null
  val doc = Jsoup.parse(realResponse)
  try {
    val timeLists = doc.select("table[border=\"1\"]").map {
      if (it.child(0).child(1).text().startsWith("NU")) return@map Empty<TimeList>()
      val dataRow = it.child(0).child(2)
      val list = mutableListOf<List<Int>>()
      // First is useless text, drop it
      // Rest are 0-23 hours
      dataRow.children().drop(1).forEach {
        if (it.children().isEmpty()) list.add(emptyList())
        else list.add(it.textNodes().map { it.text().toInt() })
      }
      return@map (list as TimeList).opt()
    }
    return@async2 Schedule(route.line, stop, timeLists[0], timeLists[1], timeLists[2])
  } catch (e: Exception) {
    requestCache.remove(cacheKey)
    Log.e(TAG, "error parsing schedule", e)
    Log.v(TAG, realResponse)
    return@async2 null
  }
}

fun getBusList(): Deferred<List<Int>?> = async2(CommonPool) {
  val page = getData("http://www.ratb.ro/v_trasee.php") ?: return@async2 null
  val doc = Jsoup.parse(page)
  doc.select("select[name=\"tlin3\"] > option").drop(1).map { it.text().toInt() }
}
