package slak.ratbwidget

import android.content.SharedPreferences

/** Wraps [SharedPreferences]'s edit-change-apply boilerplate. */
fun SharedPreferences.use(block: SharedPreferences.Editor.() -> Unit) {
  val editor = edit()
  block(editor)
  editor.apply()
}
