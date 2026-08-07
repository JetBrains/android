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

import com.android.tools.profilers.taskbased.common.icons.TaskIconUtils
import com.android.tools.profilers.tasks.ProfilerTaskType
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem
import com.intellij.testFramework.ApplicationRule
import org.junit.Rule
import org.junit.Test

class ProfilerVirtualFileTest {

  @get:Rule val applicationRule = ApplicationRule()

  @Test
  fun virtualFile_returnsExpectedPropertiesAndIcon() {
    val virtualFile =
      ProfilerVirtualFile(sessionId = 12345L, taskType = ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS, taskName = "Java/Kotlin Allocations")

    assertThat(virtualFile.sessionId).isEqualTo(12345L)
    assertThat(virtualFile.taskType).isEqualTo(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS)
    assertThat(virtualFile.name).isEqualTo("Java/Kotlin Allocations")
    assertThat(virtualFile.path).isEqualTo("Java/Kotlin Allocations")
    assertThat(virtualFile.isValid).isTrue()
    assertThat(virtualFile.fileSystem).isInstanceOf(DummyFileSystem::class.java)

    val fileType = virtualFile.fileType
    assertThat(fileType.isMyFileType(virtualFile)).isTrue()
    assertThat(fileType.name).isEqualTo("ProfilerCapture")
    assertThat(fileType.icon).isEqualTo(TaskIconUtils.getTaskIcon(ProfilerTaskType.JAVA_KOTLIN_ALLOCATIONS))
  }
}
