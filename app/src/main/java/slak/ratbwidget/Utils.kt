package slak.ratbwidget

import android.content.SharedPreferences
import kotlinx.coroutines.experimental.CoroutineScope
import kotlinx.coroutines.experimental.CoroutineStart
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.async
import kotlin.coroutines.experimental.CoroutineContext

/** Wraps [async], except it also rethrows exceptions synchronously. */
fun <T> async2(
    context: CoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): Deferred<T> {
  val c = async(context, start, null, block)
  c.invokeOnCompletion { e -> if (e != null) throw e }
  return c
}

/** Wraps [SharedPreferences]'s edit-change-apply boilerplate. */
fun SharedPreferences.use(block: SharedPreferences.Editor.() -> Unit) {
  val editor = edit()
  block(editor)
  editor.apply()
}
