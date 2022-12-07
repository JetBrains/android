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
import com.android.ddmlib.Log.LogLevel.INFO
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.ExpressionFilterManager.ExpressionException
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.UsefulTestCase.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

private const val MSG = "A message"
private const val TAG = "Tag"
private const val PKG = "com.app"
private const val PID = 123
private const val TID = 321
private val LVL = INFO
private val TIME = Instant.ofEpochSecond(1234567)

@RunWith(JUnit4::class)
class ExpressionFilterManagerTest {
  private val messageBuilder = LogCatMessageBuilder()
  private val expressionFilterManager = ExpressionFilterManager()

  @get:Rule
  val flagRule = FlagRule(StudioFlags.LOGCAT_EXPRESSION_FILTER_ENABLE, true)

  @Test
  fun isSupported_returnsTrue() {

    assertThat(expressionFilterManager.isSupported()).isTrue()
  }

  @Test
  fun isSupported_languageNotSupported_returnsFalse() {
    assertThat(ExpressionFilterManager("invalid language").isSupported()).isFalse()
  }

  @Test
  fun eval_noEngine_throws() {
    assertThrows(ExpressionException::class.java, "No appropriate scripting engine found") {
      ExpressionFilterManager("invalid language").eval(expression = "true", logCatMessage = messageBuilder.build())
    }
  }

  @Test
  fun eval_invalidExpression_throws() {
    assertThrows(ExpressionException::class.java, "Invalid expression: ") {
      expressionFilterManager.eval(expression = "invalid expression", logCatMessage = messageBuilder.build())
    }
  }

  @Test
  fun eval_nonBooleanExpression_throws() {
    assertThrows(ExpressionException::class.java, "Expression must evaluate to a boolean") {
      expressionFilterManager.eval(expression = "\"not a boolean\"", logCatMessage = messageBuilder.build())
    }
  }

  @Test
  fun eval_withMessage() {
    assertThat(expressionFilterManager.eval("message == \"$MSG\"", messageBuilder.setMessage(MSG).build())).isTrue()
  }

  @Test
  fun eval_withTag() {
    assertThat(expressionFilterManager.eval("tag == \"$TAG\"", messageBuilder.setTag(TAG).build())).isTrue()
  }

  @Test
  fun eval_withPkg() {
    assertThat(expressionFilterManager.eval("packageName == \"$PKG\"", messageBuilder.setAppName(PKG).build())).isTrue()
  }

  @Test
  fun eval_withPid() {
    assertThat(expressionFilterManager.eval("pid == $PID", messageBuilder.setPid(PID).build())).isTrue()
  }

  @Test
  fun eval_withTid() {
    assertThat(expressionFilterManager.eval("tid == $TID", messageBuilder.setTid(TID).build())).isTrue()
  }

  @Test
  fun eval_withLvl() {
    assertThat(expressionFilterManager.eval("logLevel >= ${LVL.priority}", messageBuilder.setLogLevel(LVL).build())).isTrue()
  }

  @Test
  fun eval_withTimestamp() {
    assertThat(expressionFilterManager.eval("timestamp == ${TIME.toEpochMilli()}", messageBuilder.setTimestamp(TIME).build())).isTrue()
  }

  @Test
  fun eval_withNow() {
    val clock = Clock.fixed(TIME, ZoneId.of("UTC"))
    assertThat(
      ExpressionFilterManager(clock = clock).eval("NOW_MILLIS == ${TIME.toEpochMilli()}",
                                                  messageBuilder.setTimestamp(TIME).build())).isTrue()
  }

  @Test
  fun eval_withLogLevelBindings() {
    for (lvl in LogLevel.values()) {
      assertThat(expressionFilterManager.eval("logLevel == LOG_LEVEL_${lvl.name}", messageBuilder.setLogLevel(lvl).build())).isTrue()
    }
  }

  @Test
  fun eval_withDay() {
    assertThat(expressionFilterManager.eval("MILLIS_PER_DAY == 86400000", messageBuilder.build())).isTrue()
  }

  @Test
  fun eval_withHour() {
    assertThat(expressionFilterManager.eval("MILLIS_PER_HOUR == 3600000", messageBuilder.build())).isTrue()
  }

  @Test
  fun eval_withMin() {
    assertThat(expressionFilterManager.eval("MILLIS_PER_MIN == 60000", messageBuilder.build())).isTrue()
  }

  @Test
  fun eval_withSec() {
    assertThat(expressionFilterManager.eval("MILLIS_PER_SEC == 1000", messageBuilder.build())).isTrue()
  }

  @Test
  fun eval_falseExpression() {
    assertThat(expressionFilterManager.eval("pid == 1", messageBuilder.setPid(2).build())).isFalse()
  }

  @Test
  fun eval_compoundExpression() {
    assertThat(expressionFilterManager.eval("pid == $PID && tid == $TID", messageBuilder.setPid(PID).setTid(TID).build())).isTrue()
  }

  @Test
  fun eval_allKeysAreBound() {
    for (key in expressionFilterManager.bindingKeys) {
      // Just make sure it doesn't throw if we reference key
      expressionFilterManager.eval("$key == null || true", messageBuilder.build())
    }
  }

  @Test
  fun bindingKeys() {
    assertThat(expressionFilterManager.bindingKeys).containsExactly(
      "LOG_LEVEL_VERBOSE", "LOG_LEVEL_DEBUG", "LOG_LEVEL_INFO", "LOG_LEVEL_WARN", "LOG_LEVEL_ERROR", "LOG_LEVEL_ASSERT",
      "MILLIS_PER_SEC", "MILLIS_PER_MIN", "MILLIS_PER_HOUR", "MILLIS_PER_DAY", "logLevel", "pid", "tag", "packageName", "message",
      "NOW_MILLIS", "tid", "timestamp"
    )
  }

  /**
   * A helper builder class that allows us to create a [LogCatMessage] with only the fields the test is interested in. All other fields are
   * assigned default values.
   */
  private class LogCatMessageBuilder(
    private var message: String = "",
    private var logLevel: LogLevel = INFO,
    private var pid: Int = 0,
    private var tid: Int = 0,
    private var appName: String = "",
    private var tag: String = "",
    private var timestamp: Instant = Instant.EPOCH,
  ) {

    fun build() = LogCatMessage(LogCatHeader(logLevel, pid, tid, appName, tag, timestamp), message)

    fun setMessage(value: String): LogCatMessageBuilder {
      message = value
      return this
    }

    fun setTag(value: String): LogCatMessageBuilder {
      tag = value
      return this
    }

    fun setAppName(value: String): LogCatMessageBuilder {
      appName = value
      return this
    }

    fun setPid(value: Int): LogCatMessageBuilder {
      pid = value
      return this
    }

    fun setTid(value: Int): LogCatMessageBuilder {
      tid = value
      return this
    }

    fun setLogLevel(value: LogLevel): LogCatMessageBuilder {
      logLevel = value
      return this
    }

    fun setTimestamp(value: Instant): LogCatMessageBuilder {
      timestamp = value
      return this
    }
  }
}