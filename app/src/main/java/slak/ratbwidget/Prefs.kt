package slak.ratbwidget

import android.content.Context
import android.content.SharedPreferences
import android.preference.PreferenceManager

val Context.p: SharedPreferences
  get() = PreferenceManager.getDefaultSharedPreferences(this)

/** Wraps [SharedPreferences]'s edit-change-apply boilerplate. */
private fun SharedPreferences.use(block: SharedPreferences.Editor.() -> Unit) {
  val editor = edit()
  block(editor)
  editor.apply()
}

private const val PREF_DIR_REVERSE = "dir_reverse"
private const val PREF_LINE_NR = "pref_LINENR"
private const val PREF_STOP_TO = "pref_stop_to"
private const val PREF_STOP_FROM = "pref_stop_from"

private fun lineNrPrefKey(widgetId: Int) = "$PREF_LINE_NR$widgetId"
fun SharedPreferences.lineNr(widgetId: Int): Line = Line(getInt(lineNrPrefKey(widgetId), 185))
fun SharedPreferences.setLineNr(widgetId: Int, line: Line) = use { putInt(lineNrPrefKey(widgetId), line.nr) }

private fun revPrefKey(widgetId: Int, line: Line) = "$PREF_DIR_REVERSE${line.nr}$widgetId"
fun SharedPreferences.isReverse(widgetId: Int, line: Line) = getBoolean(revPrefKey(widgetId, line), false)
fun SharedPreferences.toggleIsReverse(widgetId: Int, line: Line) =
    use { putBoolean(revPrefKey(widgetId, line), !isReverse(widgetId, line)) }

private fun SharedPreferences.stopPrefKey(widgetId: Int, line: Line) =
    if (isReverse(widgetId, line)) "$PREF_STOP_FROM${line.nr}$widgetId" else "$PREF_STOP_TO${line.nr}$widgetId"
fun SharedPreferences.stopId(widgetId: Int, line: Line) = StopId(getInt(stopPrefKey(widgetId, line), 0))
fun SharedPreferences.setStopId(widgetId: Int, line: Line, stopId: StopId) =
    use { putInt(stopPrefKey(widgetId, line), stopId.id) }
