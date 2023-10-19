/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.logcat.folding

import com.google.common.base.Joiner
import com.google.common.base.Splitter
import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val NBSP = "\u00A0"

class StackTraceExpanderTest {

  // From http://docs.oracle.com/javase/7/docs/api/java/lang/Throwable.html#printStackTrace%28%29
  @Test
  fun sampleStacktraceExpandsCorrectly() {
    val input =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk.a(Junk.java:13)
         at Junk.main(Junk.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk.c(Junk.java:23)
         at Junk.b(Junk.java:17)
         at Junk.a(Junk.java:11)
         ... 1 more
      Caused by: LowLevelException
         at Junk.e(Junk.java:30)
         at Junk.d(Junk.java:27)
         at Junk.c(Junk.java:21)
         ... 3 more
        """
        .trimIndent()
    val expected =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk.a(Junk.java:13)
         at Junk.main(Junk.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk.c(Junk.java:23)
         at Junk.b(Junk.java:17)
         at Junk.a(Junk.java:11)
         at Junk.main(Junk.java:4)$NBSP
      Caused by: LowLevelException
         at Junk.e(Junk.java:30)
         at Junk.d(Junk.java:27)
         at Junk.c(Junk.java:21)
         at Junk.b(Junk.java:17)$NBSP
         at Junk.a(Junk.java:11)$NBSP
         at Junk.main(Junk.java:4)$NBSP
      """
        .trimIndent()

    assertThat(StackTraceExpander.processMultilineText(input)).isEqualTo(expected)
  }

  @Test
  fun realLogcatOutputExpandsCorrectly() {
    val input =
      """
      10-02 15:22:48.811  24985-24998/com.example.t1 E/e1﹕ error
      java.lang.SecurityException: Permission denied (missing INTERNET permission?)
      ${'\t'}at java.net.InetAddress.lookupHostByName(InetAddress.java:418)
      ${'\t'}at java.net.InetAddress.getAllByNameImpl(InetAddress.java:236)
      ${'\t'}at java.net.InetAddress.getAllByName(InetAddress.java:214)
      ${'\t'}at java.lang.Thread.run(Thread.java:841)
      Caused by: libcore.io.GaiException: getaddrinfo failed: EAI_NODATA (No address associated with hostname)
      ${'\t'}at java.net.InetAddress.lookupHostByName(InetAddress.java:405)
         ... 2 more
      Caused by: libcore.io.ErrnoException: getaddrinfo failed: EACCES (Permission denied)
         ... 3 more
      """
        .trimIndent()
    val expected =
      """
      10-02 15:22:48.811  24985-24998/com.example.t1 E/e1﹕ error
      java.lang.SecurityException: Permission denied (missing INTERNET permission?)
      ${'\t'}at java.net.InetAddress.lookupHostByName(InetAddress.java:418)
      ${'\t'}at java.net.InetAddress.getAllByNameImpl(InetAddress.java:236)
      ${'\t'}at java.net.InetAddress.getAllByName(InetAddress.java:214)
      ${'\t'}at java.lang.Thread.run(Thread.java:841)
      Caused by: libcore.io.GaiException: getaddrinfo failed: EAI_NODATA (No address associated with hostname)
      ${'\t'}at java.net.InetAddress.lookupHostByName(InetAddress.java:405)
      ${'\t'}at java.net.InetAddress.getAllByName(InetAddress.java:214)$NBSP
      ${'\t'}at java.lang.Thread.run(Thread.java:841)$NBSP
      Caused by: libcore.io.ErrnoException: getaddrinfo failed: EACCES (Permission denied)
      ${'\t'}at java.net.InetAddress.lookupHostByName(InetAddress.java:405)$NBSP
      ${'\t'}at java.net.InetAddress.getAllByName(InetAddress.java:214)$NBSP
      ${'\t'}at java.lang.Thread.run(Thread.java:841)$NBSP
      """
        .trimIndent()

    assertThat(StackTraceExpander.processMultilineText(input)).isEqualTo(expected)
  }

  @Test
  fun multipleExceptions() {
    val input =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk1.a(Junk1.java:13)
         at Junk1.main(Junk1.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk1.c(Junk1.java:23)
         at Junk1.b(Junk1.java:17)
         at Junk1.a(Junk1.java:11)
         ... 1 more
      Caused by: LowLevelException
         at Junk1.e(Junk1.java:30)
         at Junk1.d(Junk1.java:27)
         at Junk1.c(Junk1.java:21)
         ... 3 more

      Some text in between

      HighLevelException: MidLevelException: LowLevelException
         at Junk2.a(Junk2.java:13)
         at Junk2.main(Junk2.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk2.c(Junk2.java:23)
         at Junk2.b(Junk2.java:17)
         at Junk2.a(Junk2.java:11)
         ... 1 more
        """
        .trimIndent()
    val expected =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk1.a(Junk1.java:13)
         at Junk1.main(Junk1.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk1.c(Junk1.java:23)
         at Junk1.b(Junk1.java:17)
         at Junk1.a(Junk1.java:11)
         at Junk1.main(Junk1.java:4)$NBSP
      Caused by: LowLevelException
         at Junk1.e(Junk1.java:30)
         at Junk1.d(Junk1.java:27)
         at Junk1.c(Junk1.java:21)
         at Junk1.b(Junk1.java:17)$NBSP
         at Junk1.a(Junk1.java:11)$NBSP
         at Junk1.main(Junk1.java:4)$NBSP

      Some text in between

      HighLevelException: MidLevelException: LowLevelException
         at Junk2.a(Junk2.java:13)
         at Junk2.main(Junk2.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk2.c(Junk2.java:23)
         at Junk2.b(Junk2.java:17)
         at Junk2.a(Junk2.java:11)
         at Junk2.main(Junk2.java:4)$NBSP
      """
        .trimIndent()

    assertThat(StackTraceExpander.processMultilineText(input)).isEqualTo(expected)
  }

  @Test
  fun notEnoughFrames() {
    val input =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk.a(Junk.java:13)
         at Junk.main(Junk.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk.c(Junk.java:23)
         at Junk.b(Junk.java:17)
         at Junk.a(Junk.java:11)
         ... 12 more
        """
        .trimIndent()
    val expected =
      """
      HighLevelException: MidLevelException: LowLevelException
         at Junk.a(Junk.java:13)
         at Junk.main(Junk.java:4)
      Caused by: MidLevelException: LowLevelException
         at Junk.c(Junk.java:23)
         at Junk.b(Junk.java:17)
         at Junk.a(Junk.java:11)
         ... 12 more
      """
        .trimIndent()

    assertThat(StackTraceExpander.processMultilineText(input)).isEqualTo(expected)
  }
}

private fun StackTraceExpander.processMultilineText(text: String): String? =
  Joiner.on('\n').join(process(Splitter.on('\n').split(text).toList()))
