package slak.ratbwidget

import android.content.SharedPreferences
import android.os.Parcelable
import android.support.annotation.WorkerThread
import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.android.parcel.Parcelize
import java.net.URL
import java.util.concurrent.TimeUnit

/** Identifies a stop in the remote data set. */
inline class StopId(val id: Int) {
  override fun toString() = id.toString()
}

/** The number of a line. */
inline class Line(val id: Int) {
  override fun toString() = id.toString()
}

/** An [Int] of the form HHMM (hour, minute). */
inline class Moment(val t: Int) : Comparable<Moment> {
  override fun compareTo(other: Moment): Int = t.compareTo(other.t)
}

/** Converts a [HourTimes] list to a flat list of [Moment]s. */
fun List<HourTimes>.flatten(): List<Moment> {
  return flatMap { hourTimes -> hourTimes.minutes.map { Moment(hourTimes.hour.toInt() * 100 + it.toInt()) } }
}

data class APIOrg(val id: Int, val logo: String)

@Parcelize
data class APIStop(
    val id: Int,
    @JsonProperty("lat") val latitude: Int,
    @JsonProperty("lng") val longitude: Int,
    val name: String,
    val description: String
) : Parcelable

data class APILine(
    val id: Int,
    val name: String,
    val color: String,
    @JsonProperty("has_notifications") val hasNotifications: Boolean,
    val type: String,
    @JsonProperty("ticket_sms") val ticketSmsTarget: String?,
    @JsonProperty("price_ticket_sms") val ticketSmsPriceText: String?,
    @JsonProperty("organization") val organization: APIOrg
)

data class APILineStops(
    val id: Int,
    val name: String,
    val color: String,
    @JsonProperty("has_notifications") val hasNotifications: Boolean,
    val type: String,
    val organization: APIOrg,
    @JsonProperty("segment_path") val segmentPath: String,
    @JsonProperty("direction_name_retur") val directionReverse: String,
    @JsonProperty("direction_name_tur") val directionDirect: String,
    val stops: List<APIStop>,
    @JsonProperty("ticket_sms") val ticketSmsTarget: Unit?,
    @JsonProperty("price_ticket_sms") val ticketSmsPriceText: Unit?,
) {
  fun startStopName(direction: Int) = if (direction == 0) directionReverse else directionDirect
  fun endStopName(direction: Int) = if (direction == 0) directionDirect else directionReverse
}

data class APIArrivingTime(val arrivingTime: Int, val timetable: Boolean)

data class HourTimes(val hour: String, val minutes: List<String>)

data class APILineTimetable(
    val id: Int,
    val name: String,
    val color: String,
    @JsonProperty("has_notifications") val hasNotifications: Boolean,
    val type: String,
    val organization: APIOrg,
    @JsonProperty("arriving_time") val arrivingTime: Int,
    @JsonProperty("arriving_times") val arrivingTimes: List<APIArrivingTime>?,
    val direction: Int,
    @JsonProperty("direction_name") val directionName: String,
    @JsonProperty("is_timetable") val timetableExists: Boolean,
    val timetable: List<HourTimes>?,
    val description: String,
)

data class APITimetable(
    val description: String,
    val lines: List<APILineTimetable>,
    val name: String,
    @JsonProperty("transport_type") val type: String
) {
  fun timetableOf(lineId: Line, direction: Int) = lines.firstOrNull { Line(it.id) == lineId && it.direction == direction }?.timetable
}

data class APITicketData(val name: String, val description: String, val price: String, val sms: String)

data class APITicketInfo(
    val disclaimer: String,
    @JsonProperty("sms_number") val smsNumber: String,
    val tickets: List<APITicketData>
)

data class APILinesObject(
    val lines: List<APILine>,
    @JsonProperty("ticket_info") val ticketInfo: APITicketInfo
)

val requestCache = Cache<String>("Request", TimeUnit.MINUTES.toMillis(45))
private const val TAG = "Request"

/**
 * Wrap error handling for [URL.readText].
 * @returns the text at [url] if successful, null otherwise
 */
@WorkerThread
private fun getData(url: String, cacheKey: String? = url): String? {
  try {
    if (cacheKey != null) {
      return requestCache.hit(cacheKey).let {
        if (it == null) {
          val newText = URL(url).readText()
          requestCache.update(cacheKey, newText)
          return@let newText
        } else {
          return@let it
        }
      }
    } else {
      return URL(url).readText()
    }
  } catch (t: Exception) {
    if (cacheKey != null) requestCache.remove(cacheKey)
    Log.e(TAG, "error fetching data", t)
    return null
  }
}

/** Get a list of bus lines. */
@WorkerThread
fun getLineList(): List<APILine>? {
  val json = getData("https://info.stbsa.ro/rp/api/lines") ?: return null
  Log.v(TAG, json)
  return try {
    val mapper = jacksonObjectMapper().readerFor(APILinesObject::class.java)
    val apiObject = mapper.readValue(json) as APILinesObject
    apiObject.lines
  } catch (e: InvalidDefinitionException) {
    requestCache.clear()
    Log.e(TAG, "", e)
    null
  }
}

@WorkerThread
fun getLineDirection(lineId: Line, direction: Int): APILineStops? {
  val url = "https://info.stbsa.ro/rp/api/lines/$lineId/direction/$direction"
  val json = getData(url) ?: return null
  Log.v(TAG, json)
  return try {
    val mapper = jacksonObjectMapper().readerFor(APILineStops::class.java)
    mapper.readValue(json)
  } catch (e: InvalidDefinitionException) {
    requestCache.remove(url)
    Log.e(TAG, "", e)
    null
  }
}

@WorkerThread
fun getStop(lineId: Line, stopId: StopId): APITimetable? {
  val url = "https://info.stbsa.ro/rp/api/lines/$lineId/stops/$stopId"
  val json = getData(url) ?: return null
  Log.v(TAG, json)
  return try {
    val mapper = jacksonObjectMapper()
    val apiObject = mapper.readValue(json) as List<APITimetable>
    apiObject.firstOrNull()
  } catch (e: InvalidDefinitionException) {
    requestCache.remove(url)
    Log.e(TAG, "", e)
    null
  }
}

data class FetchResult(val lineData: APILineStops, val stopData: APITimetable, val direction: Int, val timetable: List<HourTimes>)

@WorkerThread
fun SharedPreferences.fetchData(widgetId: Int): FetchResult? {
  val lineId = lineId(widgetId)
  val direction = if (isReverse(widgetId, lineId)) 1 else 0
  val lineData = getLineDirection(lineId, direction) ?: return null
  val stopData = getStop(lineId, stopId(widgetId, lineId) ?: StopId(lineData.stops[0].id)) ?: return null
  val timetable = stopData.timetableOf(Line(lineData.id), direction) ?: return null
  return FetchResult(lineData, stopData, direction, timetable)
}
