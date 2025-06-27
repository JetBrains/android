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
package com.android.tools.idea.log

import com.intellij.openapi.diagnostic.DefaultLogger
import com.intellij.util.ExceptionUtil
import org.junit.Assert.assertEquals
import org.junit.Test

private class StringLogger : DefaultLogger("") {
  private val _content = StringBuilder()
  val contents: String get() = _content.toString().trim()

  private fun log(message: String, t: Throwable?) {
    _content.append(message).appendLine()

    t?.let { ExceptionUtil.getThrowableText(it) }?.let {
      _content.append(it).appendLine()
    }
  }

  override fun debug(message: String, t: Throwable?) {
    log(message, t)
  }

  override fun error(message: String, t: Throwable?, vararg details: String?) {
    log(message, t)
  }

  override fun warn(message: String, t: Throwable?) {
    log(message, t)
  }

  override fun info(message: String, t: Throwable?) {
    log(message, t)
  }
}

class LoggerWithFixedInfoTest {
  @Test
  fun `verify logging without any additional info`() {
    val stringLogger = StringLogger()
    val log = LoggerWithFixedInfo(stringLogger, emptyMap())
    log.debug("TestA")
    log.debug("TestB")
    log.warn("TestC")
    log.info("TestD")

    assertEquals(
      """
        TestA
        TestB
        TestC
        TestD
      """.trimIndent(),
      stringLogger.contents
    )
  }


  @Test
  fun `verify logging with additional info`() {
    val stringLogger = StringLogger()
    val log = LoggerWithFixedInfo(stringLogger, mapOf(
      "key1" to "value1",
      "key2" to "value2"
    ))
    log.debug("TestA")
    log.debug("TestB")
    log.warn("TestC")
    log.info("TestD")

    assertEquals(
      """
        [key1=value1 key2=value2] TestA
        [key1=value1 key2=value2] TestB
        [key1=value1 key2=value2] TestC
        [key1=value1 key2=value2] TestD
      """.trimIndent(),
      stringLogger.contents
    )
  }
}