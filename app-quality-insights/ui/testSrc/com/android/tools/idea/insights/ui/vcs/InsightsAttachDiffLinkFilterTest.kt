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

import com.android.testutils.MockitoKt.mock
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.REPO_INFO
import com.android.tools.idea.insights.REVISION_74081e5f
import com.android.tools.idea.insights.VCS_CATEGORY
import com.android.tools.idea.insights.analytics.AppInsightsTracker
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.insights.vcs.toVcsFilePath
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.intellij.execution.impl.ConsoleViewImpl
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

@RunsInEdt
class InsightsAttachDiffLinkFilterTest {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)

  @get:Rule val rule = RuleChain.outerRule(projectRule).around(EdtRule()).around(vcsInsightsRule)

  private lateinit var exceptionInfoCache: InsightsExceptionInfoCache
  private lateinit var console: ConsoleViewImpl
  private lateinit var tracker: AppInsightsTracker

  @Before
  fun setUp() {
    tracker = mock()
    exceptionInfoCache =
      InsightsExceptionInfoCache(
        projectRule.project,
        GlobalSearchScope.allScope(projectRule.project),
      )
    console =
      ConsoleViewImpl(projectRule.project, true).also {
        Disposer.register(projectRule.testRootDisposable, it)
      }
  }

  @Test
  fun `no attached inlay when trace is not resolvable`() {
    val filter = InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker)

    // Prepare
    val appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO))
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, appVcsInfo)

    // Act
    val result = filter.applyFilter(SAMPLE_JAVA_TRACE, 93)

    // Assert
    Truth.assertThat(result).isNull()
  }

  @Test
  fun `no attached inlay when no vcs info`() {
    val filter = InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker)

    // Prepare
    projectRule.fixture.addFileToProject("src/MainActivity.java", SAMPLE_JAVA_SOURCE)

    // Act
    val result = filter.applyFilter(SAMPLE_JAVA_TRACE, 93)

    // Assert
    Truth.assertThat(result).isNull()
  }

  @Test
  fun `no attached inlay when misMatched Vcs configured`() {
    val filter = InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker)

    // Prepare
    val appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO))
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, appVcsInfo)

    projectRule.fixture.addFileToProject("src/MainActivity.java", SAMPLE_JAVA_SOURCE)

    vcsInsightsRule.clearUpMappingToRootStructure()
    vcsInsightsRule.addNewMappingToRootStructure("feature", vcsInsightsRule.vcs)

    // Act
    val result = filter.applyFilter(SAMPLE_JAVA_TRACE, 93)

    // Assert
    Truth.assertThat(result).isNull()
  }

  @Test
  fun `has attached inlay for java trace`() {
    val filter = InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker)

    // Prepare
    val appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO))
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, appVcsInfo)

    val targetPsiFile =
      projectRule.fixture.addFileToProject("src/MainActivity.java", SAMPLE_JAVA_SOURCE)

    // Act
    val result = filter.applyFilter(SAMPLE_JAVA_TRACE, 93)

    // Assert
    Truth.assertThat(result).isNotNull()
    val resultItems = result!!.resultItems
    Truth.assertThat(resultItems.size).isEqualTo(1)
    Truth.assertThat(resultItems.single())
      .isEqualTo(
        InsightsAttachInlayDiffLinkFilter.DiffLinkInlayResult(
          diffContextData =
            ContextDataForDiff(
              vcsKey = VCS_CATEGORY.TEST_VCS,
              revision = REVISION_74081e5f,
              filePath = targetPsiFile.virtualFile.toVcsFilePath(),
              lineNumber = 4,
              origin = null,
            ),
          highlightStartOffset = 73,
          highlightEndOffset = 92,
          tracker,
        )
      )
  }

  @Test
  fun `has attached inlay for kotlin trace`() {
    val filter = InsightsAttachInlayDiffLinkFilter(exceptionInfoCache, console, tracker)

    // Prepare
    val appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO))
    console.putClientProperty(VCS_INFO_OF_SELECTED_CRASH, appVcsInfo)

    val targetPsiFile =
      projectRule.fixture.addFileToProject("src/MainActivity.kt", SAMPLE_KOTLIN_SOURCE)

    // Act
    val result = filter.applyFilter(SAMPLE_KOTLIN_TRACE, 98)

    // Assert
    Truth.assertThat(result).isNotNull()
    val resultItems = result!!.resultItems
    Truth.assertThat(resultItems.size).isEqualTo(1)
    Truth.assertThat(resultItems.single())
      .isEqualTo(
        InsightsAttachInlayDiffLinkFilter.DiffLinkInlayResult(
          diffContextData =
            ContextDataForDiff(
              vcsKey = VCS_CATEGORY.TEST_VCS,
              revision = REVISION_74081e5f,
              filePath = targetPsiFile.virtualFile.toVcsFilePath(),
              lineNumber = 4,
              origin = null,
            ),
          highlightStartOffset = 80,
          highlightEndOffset = 97,
          tracker,
        )
      )
  }
}

private val SAMPLE_JAVA_SOURCE =
  """
        package test.simple;

        public class MainActivity {
            public void onCreate() {
              //TODO
            }
        }
"""
    .trimIndent()

private val SAMPLE_KOTLIN_SOURCE =
  """
          package test.simple

          class MainActivity {
              fun onCreate() {
                //TODO
              }
          }
"""
    .trimIndent()

private const val SAMPLE_JAVA_TRACE = "    test.simple.MainActivity.onCreate(MainActivity.java:4)"
private const val SAMPLE_KOTLIN_TRACE = "    test.simple.MainActivity.onCreate(MainActivity.kt:4)"
