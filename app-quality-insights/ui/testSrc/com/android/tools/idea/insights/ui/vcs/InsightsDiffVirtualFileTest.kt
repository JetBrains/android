package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.VCS_CATEGORY
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.vcs.FilePath
import com.intellij.testFramework.ProjectRule
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class InsightsDiffVirtualFileTest {
  @get:Rule val projectRule = ProjectRule()

  @Test
  fun `check equal`() {
    val filePath = mock<FilePath>().apply { whenever(this.name).thenReturn("filename") }

    val context1 =
      ContextDataForDiff(
        vcsKey = VCS_CATEGORY.TEST_VCS,
        revision = "123",
        filePath = filePath,
        lineNumber = 1,
        origin = null,
      )
    val context2 =
      ContextDataForDiff(
        vcsKey = VCS_CATEGORY.TEST_VCS,
        revision = "123",
        filePath = filePath,
        lineNumber = 3,
        origin = null,
      )

    val provider1 = InsightsDiffViewProvider(context1, projectRule.project)
    val provider2 = InsightsDiffViewProvider(context2, projectRule.project)

    val file1 = InsightsDiffVirtualFile(provider1)
    val file2 = InsightsDiffVirtualFile(provider2)

    assertThat(file1).isEqualTo(file2)
  }
}
