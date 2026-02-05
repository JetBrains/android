/*
 * Copyright (C) 2026 The Android Open Source Project
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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock

/** Unit tests for [UtpEventMessageFolding]. */
@RunWith(JUnit4::class)
class UtpEventMessageFoldingTest {
  private val project: Project = mock()
  private val folding = UtpEventMessageFolding()

  @Test
  fun shouldFoldUtpEventLine() {
    val line = "<UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>base64data</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>"
    assertThat(folding.shouldFoldLine(project, line)).isTrue()
  }

  @Test
  fun shouldFoldUtpEventLineWithWhitespace() {
    val line = "  <UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>base64data</UTP_TEST_RESULT_ON_TEST_RESULT_EVENT>  "
    assertThat(folding.shouldFoldLine(project, line)).isTrue()
  }

  @Test
  fun shouldNotFoldRegularLine() {
    val line = "Regular output line"
    assertThat(folding.shouldFoldLine(project, line)).isFalse()
  }

  @Test
  fun getPlaceholderText() {
    assertThat(folding.getPlaceholderText(project, mutableListOf())).isEqualTo("<UTP Event Message>")
  }
}
