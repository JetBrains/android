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

import com.android.ddmlib.IDevice
import com.android.ddmlib.Log.LogLevel.DEBUG
import com.android.ddmlib.logcat.LogCatHeader
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.Disposer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS

class AndroidLogcatReceiverTest {
  @get:Rule
  val projectRule = AndroidProjectRule.inMemory()

  @get:Rule
  var mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  private lateinit var mockDevice: IDevice

  private val logcatListener = TestFormattedLogcatReceiver()

  private val androidLogcatReceiver by lazy { AndroidLogcatReceiver(mockDevice, logcatListener) }

  @Before
  fun setUp() {
    Disposer.register(projectRule.fixture.projectDisposable, androidLogcatReceiver)
  }

  @After
  fun dispose() {
    Disposer.dispose(projectRule.fixture.projectDisposable)
  }

  @Test
  fun parseNewLines_singleCompleteLogMessage() {
    val batch = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000: 2000 D/Tag  ]
        Message 1

      """.replaceIndent().split("\n").toList())

    assertThat(batch.myMessages).isEmpty()
    assertThat(batch.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch.myLastLines).containsExactly(
      "Message 1",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_multipleCompleteLogMessage() {
    val batch = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000:2000 D/Tag2  ]
        Message 2

        [          1619900000.123  1000:2000 D/Tag3  ]
        Message 3

      """.replaceIndent().split("\n").toList())

    assertThat(batch.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2"),
    ).inOrder()
    assertThat(batch.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag3", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch.myLastLines).containsExactly(
      "Message 3",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_twoBatches_messageSplit() {
    val batch1 = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000:2000 D/Tag2  ]
        Message 2 Line 1
      """.replaceIndent().split("\n").toList())

    val batch2 = androidLogcatReceiver.parseNewLines(
      batch1.myLastHeader,
      batch1.myLastLines,
      """
        Message 2 Line 2

        [          1619900000.123  1000:2000 D/Tag3  ]
        Message 3

      """.replaceIndent().split("\n").toList())

    assertThat(batch1.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
    ).inOrder()
    assertThat(batch2.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2 Line 1\nMessage 2 Line 2"),
    ).inOrder()
    assertThat(batch2.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag3", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch2.myLastLines).containsExactly(
      "Message 3",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_twoBatches_messageNotSplit() {
    val batch1 = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000:2000 D/Tag2  ]
        Message 2

      """.replaceIndent().split("\n").toList())

    val batch2 = androidLogcatReceiver.parseNewLines(
      batch1.myLastHeader,
      batch1.myLastLines,
      """
        [          1619900000.123  1000:2000 D/Tag3  ]
        Message 3

        [          1619900000.123  1000:2000 D/Tag4  ]
        Message 4

      """.replaceIndent().split("\n").toList())

    assertThat(batch1.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
    ).inOrder()
    assertThat(batch2.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag3", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 3"),
    ).inOrder()
    assertThat(batch2.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag4", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch2.myLastLines).containsExactly(
      "Message 4",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_twoBatchesSplitOnUserEmittedEmptyLine() {
    val batch1 = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000:2000 D/Tag2  ]
        Message 2 Line 1

      """.replaceIndent().split("\n").toList())
    val batch2 = androidLogcatReceiver.parseNewLines(
      batch1.myLastHeader,
      batch1.myLastLines,
      """
        Message 2 Line 3

        [          1619900000.123  1000:2000 D/Tag3  ]
        Message 3

      """.replaceIndent().split("\n").toList())

    assertThat(batch1.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
    ).inOrder()
    assertThat(batch2.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2 Line 1\n" +
        "\n" +
        "Message 2 Line 3"),
    ).inOrder()
    assertThat(batch2.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag3", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch2.myLastLines).containsExactly(
      "Message 3",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_messageSplitAcrossThreeBatches() {
    val batch1 = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1 Line 1
      """.replaceIndent().split("\n").toList())
    val batch2 = androidLogcatReceiver.parseNewLines(
      batch1.myLastHeader,
      batch1.myLastLines,
      """
        Message 1 Line 2
      """.replaceIndent().split("\n").toList())
    val batch3 = androidLogcatReceiver.parseNewLines(
      batch2.myLastHeader,
      batch2.myLastLines,
      """
        Message 1 Line 3

        [          1619900000.123  1000:2000 D/Tag2  ]
        Message 2

      """.replaceIndent().split("\n").toList())

    assertThat(batch1.myMessages).isEmpty()
    assertThat(batch2.myMessages).isEmpty()
    assertThat(batch3.myMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1 Line 1\n" +
        "Message 1 Line 2\n" +
        "Message 1 Line 3"),
    ).inOrder()
    assertThat(batch3.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch2.myLastLines).containsExactly(
      "Message 2",
      ""
    ).inOrder()
  }

  @Test
  fun parseNewLines_linesWithoutHeader_dropped() {
    val batch = androidLogcatReceiver.parseNewLines(
      null,
      mutableListOf(),
      """
        --------- beginning of crash
        [          1619900000.123  1000:2000 D/Tag1  ]
        Message 1

      """.replaceIndent().split("\n").toList())

    assertThat(batch.myMessages).isEmpty()
    assertThat(batch.myLastHeader).isEqualTo(
      LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))))
    assertThat(batch.myLastLines).containsExactly(
      "Message 1",
      ""
    ).inOrder()
  }

  @Test
  fun processNewLines_singleBatch() {
    androidLogcatReceiver.processNewLines(
      """
        [          1619900000.123  1000: 2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000: 2000 D/Tag2  ]
        Message 2

      """.replaceIndent().split("\n"))

    androidLogcatReceiver.waitForIdle()
    assertThat(logcatListener.logCatMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2"),
    ).inOrder()
  }

  @Test
  fun processNewLines_multipleBatches() {
    androidLogcatReceiver.processNewLines(
      """
        [          1619900000.123  1000: 2000 D/Tag1  ]
        Message 1

        [          1619900000.123  1000: 2000 D/Tag2  ]
        Message 2 Line 1
      """.replaceIndent().split("\n"))
    androidLogcatReceiver.processNewLines(
      """
        Message 2 Line 2

        [          1619900000.123  1000: 2000 D/Tag3  ]
      """.replaceIndent().split("\n"))
    androidLogcatReceiver.processNewLines(
      """
        Message 3

        [          1619900000.123  1000: 2000 D/Tag4  ]
        Message 4

      """.replaceIndent().split("\n"))

    androidLogcatReceiver.waitForIdle()
    assertThat(logcatListener.logCatMessages).containsExactly(
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag1", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 1"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag2", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 2 Line 1\nMessage 2 Line 2"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag3", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 3"),
      LogCatMessage(
        LogCatHeader(DEBUG, 1000, 2000, "?", "Tag4", Instant.ofEpochSecond(1619900000, MILLISECONDS.toNanos(123))),
        "Message 4"),
    ).inOrder()
  }

}