/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock

class BuildToolsTooLowReporterTest {
  @get:Rule
  val projectRule = ProjectRule()

  @Test
  fun testModuleLink() {
    val module = projectRule.module
    val virtualFile = projectRule.project.baseDir!!
    val syncIssues = listOf(mock(IdeSyncIssue::class.java))

    val link = BuildToolsTooLowReporter().createModuleLink(projectRule.project, module, syncIssues, virtualFile)
    assertThat(link.lineNumber).isEqualTo(-1)
    assertThat(link.filePath).isEqualTo(virtualFile.path)
  }
}