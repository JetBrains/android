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
package com.android.tools.idea.logcat.folding

import com.android.testutils.MockitoKt.mock
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Test

/** Tests for [ExceptionFolding] */
class ExceptionFoldingTest {
  private val exceptionFolding = ExceptionFolding()
  private val project = mock<Project>()

  @Test
  fun shouldFoldLine_withSpace() {
    assertThat(exceptionFolding.shouldFoldLine(project, " at Junk.b(Junk.java:17) ")).isTrue()
  }

  @Test
  fun shouldFoldLine_withTab() {
    assertThat(exceptionFolding.shouldFoldLine(project, "\tat Junk.b(Junk.java:17) ")).isTrue()
  }

  @Test
  fun shouldFoldLine_normalStackFrame() {
    assertThat(exceptionFolding.shouldFoldLine(project, " at Junk.b(Junk.java:17)")).isFalse()
  }

  @Test
  fun shouldFoldLine_notStackFrameButWithNBSB() {
    assertThat(exceptionFolding.shouldFoldLine(project, " some random text  ")).isFalse()
  }

  @Test
  fun getPlaceholderText() {
    assertThat(exceptionFolding.getPlaceholderText(project, List(3) { "line" }))
      .isEqualTo("<3 more...>")
  }
}
