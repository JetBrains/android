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
package com.android.tools.idea.common.error

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.EdtAndroidProjectRule
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintErrorType
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.testFramework.runInEdtAndWait
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignToolsIssueProviderTest {
  @JvmField
  @Rule
  val rule = EdtAndroidProjectRule(AndroidProjectRule.inMemory())

  @Test
  fun testProvideIssues() {
    val messageBus = rule.project.messageBus

    val provider = DesignToolsIssueProvider(rule.project, EmptyFilter)
    assertTrue(provider.getFilteredIssues().isEmpty())

    val source1 = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, listOf(TestIssue()))
    assertEquals(1, provider.getFilteredIssues().size)

    val source2 = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, listOf(TestIssue(), TestIssue()))
    assertEquals(3, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source1, emptyList())
    assertEquals(2, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source2, emptyList())
    assertTrue(provider.getFilteredIssues().isEmpty())
  }

  @Test
  fun testViewOptionFilter() {
    val messageBus = rule.project.messageBus

    val provider = DesignToolsIssueProvider(rule.project, EmptyFilter)
    assertTrue(provider.getFilteredIssues().isEmpty())

    provider.viewOptionFilter = DesignerCommonIssueProvider.Filter { issue -> issue.summary.contains("keyword") }

    val issue1 = TestIssue(summary = "I have keyword")
    val issue2 = TestIssue(summary = "I have something")
    val issue3 = TestIssue(summary = "I have some keywords")
    val issueList = listOf(issue1, issue2, issue3)
    val source = Any()
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, issueList)
    assertEquals(2, provider.getFilteredIssues().size)

    provider.viewOptionFilter = DesignerCommonIssueProvider.Filter { issue -> issue.summary.contains("have") }
    assertEquals(3, provider.getFilteredIssues().size)

    provider.viewOptionFilter = DesignerCommonIssueProvider.Filter { issue -> issue.summary.contains("something") }
    assertEquals(1, provider.getFilteredIssues().size)

    val anotherSource = Any()
    val anotherIssueList = listOf(TestIssue("a keyword"), TestIssue("something"))
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(anotherSource, anotherIssueList)
    assertEquals(2, provider.getFilteredIssues().size)

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(source, emptyList())
    assertEquals(1, provider.getFilteredIssues().size)
  }

  @Test
  fun testFileClosed() {
    val messageBus = rule.project.messageBus
    val provider = DesignToolsIssueProvider(rule.project, SelectedEditorFilter(rule.project))
    val fileEditorManager = FileEditorManager.getInstance(rule.project)

    val file = runInEdtAndGet {
      val layoutFile = rule.fixture.addFileToProject("/res/layout/layout.xml", "").virtualFile
      fileEditorManager.openFile(layoutFile, true)
      layoutFile
    }
    runInEdtAndWait {
      messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(Any(), listOf(TestIssue(source = IssueSourceWithFile(file))))
    }
    assertEquals(1, provider.getFilteredIssues().size)

    runInEdtAndWait { fileEditorManager.closeFile(file) }
    assertEquals(0, provider.getFilteredIssues().size)
  }

  @Test
  fun testDoNotShowVisualLintIssueWhenTheirSourceFilesAreNotSelected() {
    val messageBus = rule.project.messageBus
    val provider = DesignToolsIssueProvider(rule.project, SelectedEditorFilter(rule.project))
    val fileEditorManager = FileEditorManager.getInstance(rule.project)

    val ktFile = rule.fixture.addFileToProject("src/KtFile.kt", "").virtualFile
    val layoutFile = rule.fixture.addFileToProject("res/layout/my_layout.xml", "").virtualFile

    val fakeNlModel = mock<NlModel>()
    `when`(fakeNlModel.virtualFile).thenReturn(layoutFile)
    val fakeNlComponent = mock<NlComponent>()
    `when`(fakeNlComponent.model).thenReturn(fakeNlModel)

    val ktFileIssues = listOf(TestIssue(source = IssueSourceWithFile(ktFile, "")))
    val visualLintIssues = listOf(createTestVisualLintRenderIssue(VisualLintErrorType.BOUNDS, listOf(fakeNlComponent), ""))

    val ktSource = Any()
    val layoutSource = VisualLintService.getInstance(rule.project).issueModel

    runInEdtAndWait { fileEditorManager.openFile(ktFile, true) }
    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(ktSource, ktFileIssues)
    assertEquals(ktFileIssues, provider.getFilteredIssues())

    messageBus.syncPublisher(IssueProviderListener.TOPIC).issueUpdated(layoutSource, visualLintIssues)
    // Visual lint issue should not be displayed because the current selected file is Kotlin file.
    assertEquals(ktFileIssues, provider.getFilteredIssues())
  }
}
