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
package com.android.tools.idea.uibuilder.visual.visuallint

import com.android.testutils.TestUtils
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel.TagSnapshotTreeNode
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

class VisualLintAnalysisTest {
  private lateinit var myAnalyticsManager: VisualLintAnalyticsManager

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    val surface = Mockito.mock(DesignSurface::class.java)
    myAnalyticsManager = VisualLintAnalyticsManager(surface)
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
  }

  @Test
  fun visualLintAnalysis() {
    projectRule.load("projects/visualLintApplication")
    projectRule.requestSyncAndWait()

    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val activityLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val dashboardLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_dashboard.xml")!!
    val notificationsLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_notifications.xml")!!
    val filesToAnalyze = listOf(activityLayout, dashboardLayout, notificationsLayout)
    val issueProvider = VisualLintIssueProvider()

    val phoneConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_phone")
    phoneConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, phoneConfiguration, issueProvider)

    val foldableConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_foldable")
    foldableConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, foldableConfiguration, issueProvider)

    val tabletConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_tablet")
    tabletConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, tabletConfiguration, issueProvider)

    val desktopConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_desktop")
    desktopConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, desktopConfiguration, issueProvider)

    val issues = issueProvider.getIssues()
    assertEquals(4, issues.size)

    issues.forEach {
      assertEquals("Visual Lint Issue", it.category)
      assertEquals(HighlightSeverity.WARNING, it.severity)
      when ((it as VisualLintRenderIssue).type) {
        VisualLintErrorType.OVERLAP -> {
          assertEquals(3, it.models.size)
          assertEquals("TextView is covered by ImageView", it.summary)
          assertEquals(
            "The content of TextView is partially hidden.<BR/>This may pose a problem for the readability of the text it contains.",
            it.description)
          assertNull(it.hyperlinkListener)
        }
        VisualLintErrorType.BOTTOM_NAV -> {
          assertEquals(3, it.models.size)
          assertEquals("Bottom navigation bar is not recommended for breakpoints over 600dp", it.summary)
          assertEquals(
            "Bottom navigation bar is not recommended for breakpoints over 600dp, which affects 3 preview configurations." +
            "<BR/>Material Design recommends replacing bottom navigation bar with " +
            "<A HREF=\"https://material.io/components/navigation-rail/android\">navigation rail</A> or " +
            "<A HREF=\"https://material.io/components/navigation-drawer/android\">navigation drawer</A> for breakpoints over 600dp.",
            it.description)
          assertNotNull(it.hyperlinkListener)
        }
        VisualLintErrorType.LONG_TEXT -> {
          assertEquals(2, it.models.size)
          assertEquals("TextView has lines containing more than 120 characters", it.summary)
          assertEquals(
            "TextView has lines containing more than 120 characters in 2 preview configurations.<BR/>Material Design recommends " +
            "reducing the width of TextView or switching to a " +
            "<A HREF=\"https://material.io/design/layout/responsive-layout-grid.html#breakpoints\">multi-column layout</A> for " +
            "breakpoints over 600dp.",
            it.description)
          assertNotNull(it.hyperlinkListener)
        }
        VisualLintErrorType.BOUNDS -> {
          assertEquals(2, it.models.size)
          assertEquals("ImageView is partially hidden in layout", it.summary)
          assertEquals(
            "ImageView is partially hidden in layout because it is not contained within the bounds of its parent in 2 preview " +
            "configurations.<BR/>Fix this issue by adjusting the size or position of ImageView.",
            it.description)
          assertNull(it.hyperlinkListener)
        }
        else -> fail("Unexpected visual lint error")
      }
    }

  }

  private fun analyzeFile(
    facet: AndroidFacet,
    files: List<VirtualFile>,
    configuration: Configuration,
    issueProvider: VisualLintIssueProvider
  ) {
    files.forEach { file ->
      val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, null, facet, file, configuration)
      val psiFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, file) as XmlFile
      nlModel.syncWithPsi(AndroidPsiUtils.getRootTagSafely(psiFile)!!, emptyList<TagSnapshotTreeNode>())
      RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
        task.setDecorations(false)
        try {
          val result = task.render().get()
          analyzeAfterModelUpdate(result, nlModel, issueProvider, VisualLintBaseConfigIssues(), myAnalyticsManager)
        }
        catch (ex: Exception) {
          throw RuntimeException(ex)
        }
      }
    }
  }
}