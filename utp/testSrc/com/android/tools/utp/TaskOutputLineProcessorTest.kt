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
package com.android.tools.utp

import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule

/**
 * Unit tests for [TaskOutputLineProcessor].
 */
@RunWith(JUnit4::class)
class TaskOutputLineProcessorTest {
  @get:Rule
  val rule: MockitoRule = MockitoJUnit.rule()

  @Mock
  lateinit var mockLineProcessor: TaskOutputLineProcessor.LineProcessor

  @Test
  fun testAppendOneLineOfText() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("test ")
      taskOutputLineProcessor.append("line\n")

      verify(mockLineProcessor).processLine("test line")
    }
  }

  @Test
  fun testAppendTwoLinesOfText() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("test ")
      taskOutputLineProcessor.append("line1\n")
      taskOutputLineProcessor.append("test ")
      taskOutputLineProcessor.append("line2\n")

      inOrder(mockLineProcessor).apply {
        verify(mockLineProcessor).processLine("test line1")
        verify(mockLineProcessor).processLine("test line2")
      }
    }
  }

  @Test
  fun testAppendThreeLinesOfTextWithNoEscapeCharacterOnLastLine() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("line1\n")
      taskOutputLineProcessor.append("line2\n")
      taskOutputLineProcessor.append("line3")

      inOrder(mockLineProcessor).apply {
        verify(mockLineProcessor).processLine("line1")
        verify(mockLineProcessor).processLine("line2")
        verify(mockLineProcessor, never()).processLine("line3")
      }
    }

    // line3 should be processed without "\n" when the processor is closed.
    verify(mockLineProcessor).processLine("line3")
  }

  @Test
  fun testAppendMultipleLinesOfText() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("line1\nline2\nline3\n")

      inOrder(mockLineProcessor).apply {
        verify(mockLineProcessor).processLine("line1")
        verify(mockLineProcessor).processLine("line2")
        verify(mockLineProcessor).processLine("line3")
      }
    }
  }

  @Test
  fun testAppendTextWithREscapeCharacter() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("line1\r")

      verify(mockLineProcessor).processLine("line1")
    }
  }

  @Test
  fun testAppendTextWithRAndNEscapeCharacters() {
    TaskOutputLineProcessor(mockLineProcessor).use { taskOutputLineProcessor ->
      taskOutputLineProcessor.append("line1\r\n")

      verify(mockLineProcessor).processLine("line1")
    }
  }
}