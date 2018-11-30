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

var SharedPreferences.lineNr: Int
  get() = getInt(PREF_LINE_NR, 185)
  set(value) = use { putInt(PREF_LINE_NR, value) }

fun SharedPreferences.isReverse(lineNr: Int) = getBoolean("$PREF_DIR_REVERSE$lineNr", false)
fun SharedPreferences.toggleIsReverse(lineNr: Int) = use { putBoolean("$PREF_DIR_REVERSE$lineNr", !isReverse(lineNr)) }

private fun SharedPreferences.stopPrefKey(lineNr: Int) =
    if (isReverse(lineNr)) "$PREF_STOP_FROM$lineNr" else "$PREF_STOP_TO$lineNr"
fun SharedPreferences.stopId(lineNr: Int) = getInt(stopPrefKey(lineNr), 0)
fun SharedPreferences.setStopId(lineNr: Int, stopId: Int) = use { putInt(stopPrefKey(lineNr), stopId) }
