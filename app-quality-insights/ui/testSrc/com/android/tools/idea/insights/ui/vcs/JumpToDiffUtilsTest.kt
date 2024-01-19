/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.ui.vcs

import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.vcs.FakeVcsForAppInsights
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.insights.vcs.VcsForAppInsights
import com.android.tools.idea.insights.vcs.toVcsFilePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.diff.chains.DiffRequestProducerException
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.ErrorDiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.diff.util.Side
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunsInEdt
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBLabel
import com.intellij.util.PathUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class JumpToDiffUtilsTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)

  @get:Rule val rule = RuleChain.outerRule(projectRule).around(EdtRule()).around(vcsInsightsRule)

  @Test
  fun testCheckDiffRequests() {
    // Prepare
    vcsInsightsRule.createChangeForPath("Foo.kt", BEFORE_REVISION, AFTER_REVISION)

    val context =
      ContextDataForDiff(
        vcsKey = VCS_CATEGORY.TEST_VCS,
        revision = BEFORE_REVISION,
        filePath = vcsInsightsRule.projectBaseDir.findChild("Foo.kt")!!.toVcsFilePath(),
        lineNumber = LINE_NUMBER,
        origin = null,
      )

    // Act
    val diffRequest = makeDiffRequest(context)

    // Assert
    with(diffRequest as SimpleDiffRequest) {
      assertThat(title)
        .isEqualTo(
          "Foo.kt (${PathUtil.toSystemDependentName(vcsInsightsRule.projectBaseDir.path)})"
        )

      val customTitles = getUserData(DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER)
      assertThat(customTitles).isNotNull()
      assertThat(customTitles!!.size).isEqualTo(2)
      assertThat(
          customTitles.mapNotNull {
            when (val label = it.label) {
              is JBLabel -> label.text
              is HyperlinkLabel -> label.text
              else -> null
            }
          }
        )
        .containsExactly(
          "<html>Historical source at commit: <a href=''>$BEFORE_REVISION</a> " +
            "<i>(Source at the app version referenced in the issue)</i></html>",
          "Current source",
        )
        .inOrder()

      val scrollToLine = getUserData(DiffUserDataKeysEx.SCROLL_TO_LINE)
      assertThat(scrollToLine).isEqualTo(Pair.create(Side.LEFT, LINE_NUMBER - 1))

      val isAlignTwoSideDiff = getUserData(DiffUserDataKeysEx.ALIGNED_TWO_SIDED_DIFF)
      assertThat(isAlignTwoSideDiff).isTrue()
    }
  }

  @Test
  fun testErrorMessageShownWhenNoHistoricalContent() {
    // Prepare
    vcsInsightsRule.createChangeForPath("Foo.kt", BEFORE_REVISION, AFTER_REVISION)

    val context =
      ContextDataForDiff(
        vcsKey = VCS_CATEGORY.TEST_VCS,
        revision = BEFORE_REVISION,
        filePath = vcsInsightsRule.projectBaseDir.findChild("Foo.kt")!!.toVcsFilePath(),
        lineNumber = LINE_NUMBER,
        origin = null,
      )

    // Note in our test infra, we don't really support real historical content.
    val extension =
      VcsForAppInsights.getExtensionByKey(VCS_CATEGORY.TEST_VCS) as FakeVcsForAppInsights
    extension.vcsContentProvider = {
      throw DiffRequestProducerException("Cannot show diff: fatal: invalid object name: '123'.")
    }

    // Act
    val diffRequest = makeDiffRequest(context)

    // Assert
    assertThat(diffRequest).isInstanceOf(ErrorDiffRequest::class.java)
    assertThat((diffRequest as ErrorDiffRequest).message)
      .isEqualTo("Source revision is not available. Update your working tree and try again.")
  }

  private fun makeDiffRequest(context: ContextDataForDiff): DiffRequest {
    val provider = InsightsDiffViewProvider(context, projectRule.project)
    val diffRequestProcessor =
      provider.createDiffRequestProcessor().apply {
        Disposer.register(projectRule.testRootDisposable, this)
      }

    return diffRequestProcessor
      .apply { updateRequest() }
      .also { PlatformTestUtil.dispatchAllEventsInIdeEventQueue() }
      .activeRequest!!
  }
}

private const val BEFORE_REVISION = "1"
private const val AFTER_REVISION = "2"
private const val LINE_NUMBER = 4
