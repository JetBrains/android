/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.logcat

import com.android.ddmlib.Log.LogLevel
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.flags.StudioFlags
import com.google.common.annotations.VisibleForTesting
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.script.ScriptContext.GLOBAL_SCOPE
import javax.script.ScriptEngine
import javax.script.ScriptEngineManager
import javax.script.ScriptException
import javax.script.SimpleBindings

private const val KEY_MSG = "message"
private const val KEY_TAG = "tag"
private const val KEY_PKG = "packageName"
private const val KEY_PID = "pid"
private const val KEY_TID = "tid"
private const val KEY_LVL = "logLevel"
private const val KEY_TIME = "timestamp"
private const val KEY_LEVEL_PREFIX = "LOG_LEVEL_"
private const val KEY_NOW = "NOW_MILLIS"
private const val KEY_DAY = "MILLIS_PER_DAY"
private const val KEY_HOUR = "MILLIS_PER_HOUR"
private const val KEY_MIN = "MILLIS_PER_MIN"
private const val KEY_SEC = "MILLIS_PER_SEC"

class ExpressionFilterManager(private val scriptExt: String = "groovy", private val clock: Clock = Clock.systemDefaultZone()) {

  /**
   * A [ScriptEngine] for the specified scripting language (by extension) that contains global bindings for Log Level and time constants.
   */
  @VisibleForTesting
  val scriptEngine: ScriptEngine? by lazy {
    val engine = ScriptEngineManager().getEngineByExtension(scriptExt)
    engine?.let { it ->
      val map: MutableMap<String, Any> = mutableMapOf()
      LogLevel.values().associateByTo(map, { KEY_LEVEL_PREFIX + it.name }, { it.priority })
      map[KEY_SEC] = TimeUnit.SECONDS.toMillis(1)
      map[KEY_MIN] = TimeUnit.MINUTES.toMillis(1)
      map[KEY_HOUR] = TimeUnit.HOURS.toMillis(1)
      map[KEY_DAY] = TimeUnit.DAYS.toMillis(1)
      it.setBindings(SimpleBindings(map), GLOBAL_SCOPE)
    }
    engine
  }

  val languageName = scriptEngine?.factory?.languageName ?: ""

  val bindingKeys =
    scriptEngine?.getBindings(GLOBAL_SCOPE)?.keys ?: listOf<String>() + createMessageBindings(/* logCatMessage= */ null).keys

  /**
   * Evaluate a boolean expression against values extracted from a [com.android.ddmlib.logcat.LogCatMessage]
   *
   * @throws ExpressionException if the expression failed to evaluate.
   */
  @Throws(ExpressionException::class)
  fun eval(expression: String, logCatMessage: LogCatMessage): Boolean {
    scriptEngine?.let {
      val result: Any?
      try {
        result = scriptEngine!!.eval(expression, createMessageBindings(logCatMessage))
      } catch (e: ScriptException) {

        throw ExpressionException("Invalid expression: ${getInnerCause(e).message}", e)
      }
      if (result !is Boolean) {
        throw ExpressionException("Expression must evaluate to a boolean")
      }
      return result
    }?: throw ExpressionException("No appropriate scripting engine found")
  }

  /** Create bindings for the values of a Logcat message. If message is null, bind with trivial data (null/0) */
  @VisibleForTesting
  fun createMessageBindings(logCatMessage: LogCatMessage?): SimpleBindings {
    val bindings = SimpleBindings()
    val header = logCatMessage?.header

    bindings[KEY_MSG] = logCatMessage?.message
    bindings[KEY_TAG] = header?.tag
    bindings[KEY_PKG] = header?.appName
    bindings[KEY_PID] = header?.pid
    bindings[KEY_TID] = header?.tid
    bindings[KEY_LVL] = header?.logLevel?.priority
    bindings[KEY_TIME] = header?.timestamp?.toEpochMilli()
    bindings[KEY_NOW] = clock.millis()
    return bindings
  }

  /** Returns true if the expression evaluation is supported */
  fun isSupported(): Boolean = StudioFlags.LOGCAT_EXPRESSION_FILTER_ENABLE.get() && scriptEngine != null

  private fun getInnerCause(t: Throwable) : Throwable{
    var c: Throwable? = t
    while (c!!.cause != null) {
      c = c.cause
    }
    return c
  }

  class ExpressionException(message: String, cause: Throwable? = null) : Exception(message, cause)
}

