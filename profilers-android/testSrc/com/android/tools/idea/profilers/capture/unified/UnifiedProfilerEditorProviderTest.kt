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
package com.android.tools.idea.profilers.capture.unified

import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.ProjectRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class UnifiedProfilerEditorProviderTest {

  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val projectRule = ProjectRule()

  private lateinit var provider: UnifiedProfilerEditorProvider

  @Before
  fun setUp() {
    provider = UnifiedProfilerEditorProvider()
  }

  @Test
  fun accept_profilerVirtualFile_returnsTrue() {
    val liveVirtualFile =
      ProfilerVirtualFile(sessionId = 1L, taskType = ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, taskName = "Java/Kotlin Allocations")

    assertThat(provider.accept(projectRule.project, liveVirtualFile)).isTrue()
  }

  @Test
  fun createEditor_profilerVirtualFile_createsUnifiedProfilerFileEditor() {
    val liveVirtualFile =
      ProfilerVirtualFile(sessionId = 1L, taskType = ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, taskName = "Java/Kotlin Allocations")

    val editor = provider.createEditor(projectRule.project, liveVirtualFile)
    assertThat(editor).isInstanceOf(UnifiedProfilerFileEditor::class.java)
    assertThat(editor.name).isEqualTo("Java/Kotlin Allocations")
  }
}
