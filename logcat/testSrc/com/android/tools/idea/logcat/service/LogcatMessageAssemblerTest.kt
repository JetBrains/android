/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.service

import com.android.processmonitor.monitor.ProcessNameMonitor
import com.android.processmonitor.monitor.testing.FakeProcessNameMonitor
import com.android.testutils.TestResources
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogLevel
import com.android.tools.idea.logcat.message.LogLevel.DEBUG
import com.android.tools.idea.logcat.message.LogLevel.INFO
import com.android.tools.idea.logcat.message.LogcatHeader
import com.android.tools.idea.logcat.message.LogcatHeaderParser.LogcatFormat.EPOCH_FORMAT
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.testing.WaitForIndexRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.CharBuffer
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.text.Charsets.UTF_8
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** Tests for [LogcatMessageAssembler] */
@Suppress("OPT_IN_USAGE") // runTest is experimental
class LogcatMessageAssemblerTest {
  private val disposableRule = DisposableRule()

  private val projectRule = ProjectRule()
  @get:Rule val rule = RuleChain(projectRule, WaitForIndexRule(projectRule),
                                 disposableRule)

  private val processNameMonitor = FakeProcessNameMonitor()

  private val channel = Channel<List<LogcatMessage>>(UNLIMITED)

  @Before
  fun setUp() {
    processNameMonitor.addProcessName("device1", 1, "app-1.1", "process-1.1")
    processNameMonitor.addProcessName("device1", 2, "app-1.2", "process-1.2")
    processNameMonitor.addProcessName("device2", 1, "app-2.1", "process-2.1")
    processNameMonitor.addProcessName("device2", 2, "app-2.2", "process-2.2")
  }

  @Test
  fun singleCompleteLogMessage() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  1: 2000 D/Tag  ]
        Message 1

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag",
              1619900000L,
              123L,
              "Message 1",
            )
          )
        )
    }

  @Test
  fun multipleCompleteLogMessage() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.101  1: 2000 D/Tag  ]
        Message 1

        [          1619900000.102  1: 2000 D/Tag  ]
        Message 2

        [          1619900000.103  1: 2000 D/Tag  ]
        Message 3

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag",
              1619900000L,
              101L,
              "Message 1",
            ),
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag",
              1619900000L,
              102L,
              "Message 2",
            ),
          ),
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag",
              1619900000L,
              103L,
              "Message 3",
            )
          ),
        )
    }

  @Test
  fun twoBatches_messageSplit() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device2", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  2:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  2:2000 D/Tag2  ]
        Message 2 Line 1
      """
      )
      assembler.processNewLines(
        """
        Message 2 Line 2

        [          1619900000.123  1:2000 D/Tag3  ]
        Message 3

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              DEBUG,
              2,
              2000,
              "app-2.2",
              "process-2.2",
              "Tag1",
              1619900000L,
              123L,
              "Message 1",
            )
          ),
          listOf(
            logcatMessage(
              DEBUG,
              2,
              2000,
              "app-2.2",
              "process-2.2",
              "Tag2",
              1619900000L,
              123L,
              "Message 2 Line 1\nMessage 2 Line 2",
            )
          ),
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-2.1",
              "process-2.1",
              "Tag3",
              1619900000L,
              123L,
              "Message 3",
            )
          ),
        )
    }

  @Test
  fun twoBatches_messageNotSplit() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device2", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  2:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  2:2000 D/Tag2  ]
        Message 2
        
      """
      )
      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 D/Tag3  ]
        Message 3

        [          1619900000.123  1:2000 D/Tag4  ]
        Message 4

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              DEBUG,
              2,
              2000,
              "app-2.2",
              "process-2.2",
              "Tag1",
              1619900000L,
              123L,
              "Message 1",
            )
          ),
          listOf(
            logcatMessage(
              DEBUG,
              2,
              2000,
              "app-2.2",
              "process-2.2",
              "Tag2",
              1619900000L,
              123L,
              "Message 2",
            ),
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-2.1",
              "process-2.1",
              "Tag3",
              1619900000L,
              123L,
              "Message 3",
            ),
          ),
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-2.1",
              "process-2.1",
              "Tag4",
              1619900000L,
              123L,
              "Message 4",
            )
          ),
        )
    }

  @Test
  fun twoBatchesSplitOnUserEmittedEmptyLine() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1:2000 D/Tag2  ]
        Message 2 Line 1

      """
      )
      assembler.processNewLines(
        """
        Message 2 Line 3

        [          1619900000.123  1:2000 D/Tag3  ]
        Message 3

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag1",
              1619900000L,
              123L,
              "Message 1",
            )
          ),
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag2",
              1619900000L,
              123L,
              "Message 2 Line 1\n\nMessage 2 Line 3",
            )
          ),
          listOf(
            logcatMessage(
              DEBUG,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag3",
              1619900000L,
              123L,
              "Message 3",
            )
          ),
        )
    }

  @Test
  fun messageSplitAcrossThreeBatches() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 I/Tag1  ]
        Message 1 Line 1
      """
      )
      assembler.processNewLines(
        """
        Message 1 Line 2
      """
      )

      assembler.processNewLines(
        """
        Message 1 Line 3

        [          1619900000.123  1:2000 I/Tag2  ]
        Message 2

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              INFO,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag1",
              1619900000L,
              123L,
              "Message 1 Line 1\nMessage 1 Line 2\nMessage 1 Line 3",
            )
          ),
          listOf(
            logcatMessage(
              INFO,
              1,
              2000,
              "app-1.1",
              "process-1.1",
              "Tag2",
              1619900000L,
              123L,
              "Message 2",
            )
          ),
        )
    }

  @Test
  fun systemLines() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        --------- beginning of crash
        [          1619900001.123  1:1000 I/Tag1  ]
        Message 1
        
        --------- beginning of system
        [          1619900001.123  1:1000 I/Tag2  ]
        Message 2
        
      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            LogcatMessage(SYSTEM_HEADER, "--------- beginning of crash"),
            LogcatMessage(SYSTEM_HEADER, "--------- beginning of system"),
            logcatMessage(
              INFO,
              1,
              1000,
              "app-1.1",
              "process-1.1",
              "Tag1",
              1619900001L,
              123L,
              "Message 1",
            ),
          ),
          listOf(
            logcatMessage(
              INFO,
              1,
              1000,
              "app-1.1",
              "process-1.1",
              "Tag2",
              1619900001L,
              123L,
              "Message 2",
            )
          ),
        )
    }

  @Test
  fun linesWithoutHeader_dropped() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        Message 1
        
        [          1619900001.123  1:1000 I/Tag2  ]
        Message 2
        
      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(
              INFO,
              1,
              1000,
              "app-1.1",
              "process-1.1",
              "Tag2",
              1619900001L,
              123L,
              "Message 2",
            )
          )
        )
    }

  /**
   * This test sends 3 small batches with a small interval between them simulating a running Logcat
   * process that emits data periodically.
   *
   * In contrast to the other tests in this file, it asserts the state of the channel after each
   * batch rather than at the end.
   */
  @Test
  fun multipleBatchesWithIntervals() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1:2000 D/Tag2  ]
        Message 2

      """
      )
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag1",
            1619900000L,
            123L,
            "Message 1",
          )
        )
      assertThat(channel.isEmpty).isTrue()
      testScheduler.advanceTimeBy(100)
      testScheduler.runCurrent()
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag2",
            1619900000L,
            123L,
            "Message 2",
          )
        )

      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 D/Tag3  ]
        Message 3

        [          1619900000.123  1:2000 D/Tag4  ]
        Message 4

      """
      )
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag3",
            1619900000L,
            123L,
            "Message 3",
          )
        )
      assertThat(channel.isEmpty).isTrue()
      testScheduler.advanceTimeBy(100)
      testScheduler.runCurrent()
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag4",
            1619900000L,
            123L,
            "Message 4",
          )
        )

      assembler.processNewLines(
        """
        [          1619900000.123  1:2000 D/Tag5  ]
        Message 5

        [          1619900000.123  1:2000 D/Tag6  ]
        Message 6

      """
      )
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag5",
            1619900000L,
            123L,
            "Message 5",
          )
        )
      assertThat(channel.isEmpty).isTrue()
      testScheduler.advanceTimeBy(100)
      testScheduler.runCurrent()
      assertThat(channel.receive())
        .containsExactly(
          logcatMessage(
            DEBUG,
            1,
            2000,
            "app-1.1",
            "process-1.1",
            "Tag6",
            1619900000L,
            123L,
            "Message 6",
          )
        )
      channel.close()
      advanceUntilIdle()
      // TODO(b/347771901) Uncomment and fix
      // assertThat(channel.isEmpty).isTrue()
    }

  @Test
  fun realLogcat_oneBatch() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        TestResources.getFile("/logcatFiles/real-logcat-from-device.txt").readLines()
      )

      advanceUntilIdle()
      channel.close()
      val actualLines = channel.toList().flatten().joinToString("\n") { it.toString() }.split('\n')
      val expectedLines =
        TestResources.getFile("/logcatFiles/real-logcat-from-device-expected.txt").readLines()
      assertThat(actualLines).hasSize(expectedLines.size)
      actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
        assertThat(actual).named("Line $index").isEqualTo(expected)
      }
    }

  @Test
  fun realLogcat_smallBatches() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      TestResources.getFile("/logcatFiles/real-logcat-from-device.txt")
        .readLinesInBatches(50)
        .forEach { assembler.processNewLines(it) }

      advanceUntilIdle()
      channel.close()
      val actualLines = channel.toList().flatten().joinToString("\n") { it.toString() }.split('\n')
      val expectedLines =
        TestResources.getFile("/logcatFiles/real-logcat-from-device-expected.txt").readLines()
      assertThat(actualLines).hasSize(expectedLines.size)
      actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
        assertThat(actual).named("Line $index").isEqualTo(expected)
      }
    }

  @Test
  fun realLogcat_largeBatches() =
    runTest(timeout = 5.seconds) {
      val assembler = logcatMessageAssembler("device1", channel)

      TestResources.getFile("/logcatFiles/real-logcat-from-device.txt")
        .readLinesInBatches(8192)
        .forEach { assembler.processNewLines(it) }

      advanceUntilIdle()
      channel.close()
      val actualLines = channel.toList().flatten().joinToString("\n") { it.toString() }.split('\n')
      val expectedLines =
        TestResources.getFile("/logcatFiles/real-logcat-from-device-expected.txt").readLines()
      assertThat(actualLines).hasSize(expectedLines.size)
      actualLines.zip(expectedLines).forEachIndexed { index, (actual, expected) ->
        assertThat(actual).named("Line $index").isEqualTo(expected)
      }
    }

  @Test
  fun missingApplicationId_usesProcessName() =
    runTest(timeout = 5.seconds) {
      processNameMonitor.addProcessName("device1", 5, "", "processName")

      val assembler = logcatMessageAssembler("device1", channel)

      assembler.processNewLines(
        """
        [          1619900000.123  5: 2000 D/Tag  ]
        Message 1

      """
      )

      advanceUntilIdle()
      channel.close()
      assertThat(channel.toList())
        .containsExactly(
          listOf(
            logcatMessage(DEBUG, 5, 2000, "", "processName", "Tag", 1619900000L, 123L, "Message 1")
          )
        )
    }

  private fun TestScope.logcatMessageAssembler(
    serialNumber: String,
    channel: SendChannel<List<LogcatMessage>>,
    processNameMonitor: ProcessNameMonitor = this@LogcatMessageAssemblerTest.processNameMonitor,
  ): LogcatMessageAssembler {
    val logcatMessageAssembler =
      LogcatMessageAssembler(
        serialNumber,
        EPOCH_FORMAT,
        channel,
        processNameMonitor,
        coroutineContext,
        lastMessageDelayMs = 100,
      )
    Disposer.register(disposableRule.disposable, logcatMessageAssembler)
    return logcatMessageAssembler
  }
}

private fun logcatMessage(
  level: LogLevel,
  pid: Int,
  tid: Int,
  appId: String,
  processName: String,
  tag: String,
  seconds: Long,
  millis: Long,
  message: String,
) =
  LogcatMessage(
    LogcatHeader(
      level,
      pid,
      tid,
      appId,
      processName,
      tag,
      Instant.ofEpochSecond(seconds, MILLISECONDS.toNanos(millis)),
    ),
    message,
  )

private suspend fun LogcatMessageAssembler.processNewLines(lines: String) =
  processNewLines(lines.replaceIndent().split("\n").toList())

/**
 * Reads lines from a file in batches.
 *
 * Inspired by com.android.adblib.utils.MultiLineShellCollector
 */
private fun File.readLinesInBatches(bufferSize: Int): List<List<String>> {
  val stream = BufferedReader(InputStreamReader(inputStream(), UTF_8))
  val batches = mutableListOf<List<String>>()
  val previousString = StringBuilder()

  while (true) {
    val charBuffer = CharBuffer.allocate(bufferSize)
    val len = stream.read(charBuffer)
    if (len < 0) {
      break
    }
    charBuffer.rewind()
    val lines = mutableListOf<String>()
    var currentOffset = 0
    while (currentOffset < len) {
      val index = charBuffer.indexOf("\n", currentOffset)
      if (index < 0) {
        previousString.append(charBuffer.substring(currentOffset))
        break
      }
      previousString.append(charBuffer.substring(currentOffset, index))
      lines.add(previousString.toString())
      previousString.clear()
      currentOffset = index + 1
    }
    batches.add(lines)
  }
  if (previousString.isNotEmpty()) {
    batches.add(listOf(previousString.toString()))
  }
  return batches
}
