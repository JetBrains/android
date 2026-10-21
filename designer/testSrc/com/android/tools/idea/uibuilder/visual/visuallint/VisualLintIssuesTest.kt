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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.visuallint.VisualLintErrorType
import com.android.utils.HtmlBuilder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.vfs.VirtualFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class VisualLintIssuesTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testClearFreezesIssues() {
    val mockModel = Mockito.mock(NlModel::class.java)
    `when`(mockModel.project).thenReturn(projectRule.project)
    val mockFile = Mockito.mock(VirtualFile::class.java)
    `when`(mockModel.virtualFile).thenReturn(mockFile)
    val mockComponent = Mockito.mock(NlComponent::class.java)
    `when`(mockComponent.model).thenReturn(mockModel)

    val issue =
      VisualLintRenderIssue.builder()
        .summary("summary")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(mockModel)
        .components(mutableListOf(mockComponent))
        .type(VisualLintErrorType.BOUNDS)
        .build()

    val issues = VisualLintIssues()
    issues.add(issue)

    assertEquals(1, issues.list.size)
    assertEquals(setOf(mockModel), issue.models)
    assertEquals(listOf(mockComponent), issue.components)

    issues.clear()

    assertTrue(issues.list.isEmpty())
    // The issue should now be frozen, meaning its models and components are cleared.
    assertTrue(issue.models.isEmpty())
    assertTrue(issue.components.isEmpty())
  }

  @Test
  fun testFreezeIdempotency() {
    val mockModel = Mockito.mock(NlModel::class.java)
    `when`(mockModel.project).thenReturn(projectRule.project)
    val mockFile = Mockito.mock(VirtualFile::class.java)
    `when`(mockModel.virtualFile).thenReturn(mockFile)
    val mockComponent = Mockito.mock(NlComponent::class.java)
    `when`(mockComponent.model).thenReturn(mockModel)

    val issue =
      VisualLintRenderIssue.builder()
        .summary("summary")
        .severity(HighlightSeverity.WARNING)
        .contentDescriptionProvider { HtmlBuilder() }
        .model(mockModel)
        .components(mutableListOf(mockComponent))
        .type(VisualLintErrorType.BOUNDS)
        .build()

    // First freeze
    issue.freeze()
    assertTrue(issue.models.isEmpty())
    assertTrue(issue.components.isEmpty())

    // Second freeze should not throw or change state in a bad way
    issue.freeze()
    assertTrue(issue.models.isEmpty())
    assertTrue(issue.components.isEmpty())
  }
}
