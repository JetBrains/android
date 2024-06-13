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
package com.android.tools.idea.insights.inspection

import com.android.flags.junit.FlagRule
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.Frame
import com.android.tools.idea.insights.REPO_INFO
import com.android.tools.idea.insights.ui.AppInsightsGutterRenderer
import com.android.tools.idea.insights.vcs.InsightsVcsTestRule
import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.codeInsight.daemon.LineMarkerSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class AppInsightsExternalAnnotatorTest(private val enableChangeAwareAnnotation: Boolean) {
  private val projectRule = AndroidProjectRule.onDisk()
  private val vcsInsightsRule = InsightsVcsTestRule(projectRule)
  private val changeAwareFlagRule =
    FlagRule(StudioFlags.APP_INSIGHTS_CHANGE_AWARE_ANNOTATION_SUPPORT, enableChangeAwareAnnotation)

  @get:Rule
  val rule: RuleChain =
    RuleChain.outerRule(projectRule).around(vcsInsightsRule).around(changeAwareFlagRule)

  companion object {
    @JvmStatic @Parameterized.Parameters(name = "{0}") fun data() = listOf(true, false)
  }

  private lateinit var appVcsInfo: AppVcsInfo

  private val document
    get() = projectRule.fixture.editor.document

  @Before
  fun setUp() {
    appVcsInfo = AppVcsInfo.ValidInfo(listOf(REPO_INFO))
  }

  @Test
  fun `disable annotations`() {
    val expected = listOf(buildAppInsight(Frame(line = 4), buildIssue(appVcsInfo)))

    withFakedInsights(expected)

    val fakeLineMarkerSettings =
      object : LineMarkerSettings() {
        override fun isEnabled(descriptor: GutterIconDescriptor) = false

        override fun setEnabled(descriptor: GutterIconDescriptor, selected: Boolean) {}
      }

    ApplicationManager.getApplication()
      .replaceService(
        LineMarkerSettings::class.java,
        fakeLineMarkerSettings,
        projectRule.testRootDisposable,
      )

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights = emptyList(),
    )
  }

  @Test
  fun `no annotations`() {
    withFakedInsights(emptyList())

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights = emptyList(),
    )
  }

  @Test
  fun `out of scope issues are filtered out`() {
    val expected =
      listOf(
        buildAppInsight(Frame(line = 4), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 100), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 200), buildIssue(appVcsInfo)),
      )

    withFakedInsights(expected)

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights = listOf(LineToInsights(3, listOf(expected[0]))),
    )
  }

  @Test
  fun `duplicate issues are removed`() {
    val same = buildIssue(appVcsInfo)
    val expected =
      listOf(
        buildAppInsight(Frame(line = 1), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 4), same),
        buildAppInsight(Frame(line = 4), same),
      )

    withFakedInsights(expected)

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights =
        listOf(LineToInsights(0, listOf(expected[0])), LineToInsights(3, listOf(expected[1]))),
    )
  }

  @Test
  fun `annotations from single insights source`() {
    val expected =
      listOf(
        buildAppInsight(Frame(line = 1), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 4), buildIssue(appVcsInfo)),
      )

    withFakedInsights(expected)

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights =
        listOf(LineToInsights(0, listOf(expected[0])), LineToInsights(3, listOf(expected[1]))),
    )
  }

  @Test
  fun `annotations from two insights sources`() {
    val expected1 =
      listOf(
        buildAppInsight(Frame(line = 1), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 4), buildIssue(appVcsInfo)),
      )
    val expected2 =
      listOf(
        buildAppInsight(Frame(line = 2), buildIssue(appVcsInfo)),
        buildAppInsight(Frame(line = 4), buildIssue(appVcsInfo)),
      )

    withFakedInsights(expected1, expected2)

    checkAnnotations(
      fileName = "MainActivity.kt",
      source =
        """
          package test.simple

          class MainActivity {
              fun onCreate() {
              }
          }
      """
          .trimIndent(),
      lineToInsights =
        listOf(
          LineToInsights(0, listOf(expected1[0])),
          LineToInsights(1, listOf(expected2[0])),
          LineToInsights(3, listOf(expected1[1], expected2[1])),
        ),
    )
  }

  private fun checkAnnotations(
    fileName: String,
    source: String,
    lineToInsights: List<LineToInsights>,
  ) {
    val psiFile = projectRule.fixture.addFileToProject("src/$fileName", source)
    projectRule.fixture.configureFromExistingVirtualFile(psiFile.virtualFile)

    val results =
      projectRule.fixture.doHighlighting().filter {
        it.gutterIconRenderer is AppInsightsGutterRenderer
      }

    document.assertHighlightResults(results, lineToInsights)
  }
}
