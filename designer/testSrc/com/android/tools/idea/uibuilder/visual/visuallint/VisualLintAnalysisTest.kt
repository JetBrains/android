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
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.WearMarginAnalyzerInspection
import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class VisualLintAnalysisTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  private val issueProvider by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    VisualLintIssueProvider(projectRule.fixture.testRootDisposable)
  }
  
  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    val visualLintInspections = arrayOf(BoundsAnalyzerInspection, BottomNavAnalyzerInspection, BottomAppBarAnalyzerInspection,
                                        TextFieldSizeAnalyzerInspection, OverlapAnalyzerInspection, LongTextAnalyzerInspection,
                                        ButtonSizeAnalyzerInspection, WearMarginAnalyzerInspection)
    projectRule.fixture.enableInspections(*visualLintInspections)
    InspectionProfileManager.getInstance(projectRule.project).currentProfile.setErrorLevel(
      HighlightDisplayKey.find(VisualLintErrorType.BOUNDS.shortName), HighlightDisplayLevel.ERROR, projectRule.project)
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

    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val activityLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/activity_main.xml")!!
    val dashboardLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_dashboard.xml")!!
    val notificationsLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_notifications.xml")!!
    val homeLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_home.xml")!!
    var filesToAnalyze = listOf(activityLayout, dashboardLayout, notificationsLayout, homeLayout)

    val phoneConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_phone")
    phoneConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, phoneConfiguration)

    val foldableConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_foldable")
    foldableConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, foldableConfiguration)

    val tabletConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_tablet")
    tabletConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, tabletConfiguration)

    val desktopConfiguration = RenderTestUtil.getConfiguration(module, activityLayout, "_device_class_desktop")
    desktopConfiguration.setTheme("Theme.MaterialComponents.DayNight.DarkActionBar")
    analyzeFile(facet, filesToAnalyze, desktopConfiguration)

    val issues = issueProvider.getIssues()
    assertEquals(6, issues.size)

    issues.forEach {
      assertEquals("Visual Lint Issue", it.category)
      when ((it as VisualLintRenderIssue).type) {
        VisualLintErrorType.OVERLAP -> {
          assertEquals(3, it.models.size)
          assertEquals("text_dashboard <TextView> is covered by imageView <ImageView>", it.summary)
          assertEquals(
            "Content of text_dashboard &lt;TextView> is partially covered by imageView &lt;ImageView> in 3 preview configurations." +
            "<BR/>This may affect text readability. Fix this issue by adjusting widget positioning.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        VisualLintErrorType.BOTTOM_NAV -> {
          assertEquals(3, it.models.size)
          assertEquals("Bottom navigation bar is not recommended for breakpoints over 600dp", it.summary)
          assertEquals(
            "Bottom navigation bar is not recommended for breakpoints >= 600dp, which affects 3 preview configurations." +
            "<BR/>Material Design recommends replacing bottom navigation bar with " +
            "<A HREF=\"https://material.io/components/navigation-rail/android\">navigation rail</A> or " +
            "<A HREF=\"https://material.io/components/navigation-drawer/android\">navigation drawer</A> for breakpoints >= 600dp.",
            it.description)
          assertNotNull(it.hyperlinkListener)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        VisualLintErrorType.LONG_TEXT -> {
          assertEquals(2, it.models.size)
          assertEquals("text_notifications <TextView> has lines containing more than 120 characters", it.summary)
          assertEquals(
            "TextView has lines containing more than 120 characters in 2 preview configurations.<BR/>Material Design recommends " +
            "reducing the width of TextView or switching to a " +
            "<A HREF=\"https://material.io/design/layout/responsive-layout-grid.html#breakpoints\">multi-column layout</A> for " +
            "breakpoints >= 600dp.",
            it.description)
          assertNotNull(it.hyperlinkListener)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        VisualLintErrorType.BOUNDS -> {
          assertEquals(2, it.models.size)
          assertEquals("imageView <ImageView> is partially hidden in layout", it.summary)
          assertEquals(
            "ImageView is partially hidden in layout because it is not contained within the bounds of its parent in 2 preview " +
            "configurations.<BR/>Fix this issue by adjusting the size or position of ImageView.",
            it.description)
          assertEquals(HighlightSeverity.ERROR, it.severity)
        }
        VisualLintErrorType.BUTTON_SIZE -> {
          assertEquals(4, it.models.size)
          assertEquals("The button button <Button> is too wide", it.summary)
          assertEquals(
            "The button Button is wider than 320dp in 4 preview configurations." +
            "<BR/>Material Design recommends buttons to be no wider than 320dp",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        VisualLintErrorType.TEXT_FIELD_SIZE -> {
          assertEquals(3, it.models.size)
          assertEquals("The text field text_field <EditText> is too wide", it.summary)
          assertEquals(
            "The text field EditText is wider than 488dp in 3 preview configurations." +
            "<BR/>Material Design recommends text fields to be no wider than 488dp",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        else -> fail("Unexpected visual lint error")
      }
    }

    projectRule.fixture.disableInspections(BoundsAnalyzerInspection, TextFieldSizeAnalyzerInspection)
    issueProvider.clear()
    analyzeFile(facet, filesToAnalyze, phoneConfiguration)
    analyzeFile(facet, filesToAnalyze, tabletConfiguration)
    analyzeFile(facet, filesToAnalyze, foldableConfiguration)
    analyzeFile(facet, filesToAnalyze, desktopConfiguration)
    assertEquals(4, issues.size)
    issues.map {it as VisualLintRenderIssue }.forEach {
      assertNotEquals(VisualLintErrorType.BOUNDS, it.type)
      assertNotEquals(VisualLintErrorType.TEXT_FIELD_SIZE, it.type)
    }

    val wearLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/wear_layout.xml")!!
    filesToAnalyze = listOf(wearLayout)
    issueProvider.clear()
    analyzeFile(facet, filesToAnalyze, phoneConfiguration)
    assertEquals(7, issues.size)
    issues.map {it as VisualLintRenderIssue }.forEach {
      assertNotEquals(VisualLintErrorType.WEAR_MARGIN, it.type)
    }

    issueProvider.clear()
    val wearSquareConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_square")
    analyzeFile(facet, filesToAnalyze, wearSquareConfiguration)
    val wearRectConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_rect")
    analyzeFile(facet, filesToAnalyze, wearRectConfiguration)
    val wearSmallRoundConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_small_round")
    analyzeFile(facet, filesToAnalyze, wearSmallRoundConfiguration)
    val wearLargeRoundConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_large_round")
    analyzeFile(facet, filesToAnalyze, wearLargeRoundConfiguration)
    assertEquals(12, issues.size)
    val wearIssues = issues.filterIsInstance<VisualLintRenderIssue>().filter { it.type == VisualLintErrorType.WEAR_MARGIN }
    assertEquals(5, wearIssues.size)
    wearIssues.forEach {
      assertEquals("Visual Lint Issue", it.category)
      when (it.components.first().id) {
        "image_view" -> {
          assertEquals(3, it.models.size)
          assertEquals("The view image_view <ImageView> is too close to the side of the device", it.summary)
          assertEquals(
            "In 3 preview configurations, the view ImageView is closer to the side of the device than the recommended amount.<BR/>" +
            "It is recommended that, for Wear OS layouts, margins should be at least 2.5% for square devices, and 5.2% for round devices.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        "textview1" -> {
          assertEquals(4, it.models.size)
          assertEquals("The view textview1 <TextView> is too close to the side of the device", it.summary)
          assertEquals(
            "In 4 preview configurations, the view TextView is closer to the side of the device than the recommended amount.<BR/>" +
            "It is recommended that, for Wear OS layouts, margins should be at least 2.5% for square devices, and 5.2% for round devices.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        "textview2" -> {
          assertEquals(1, it.models.size)
          assertEquals("The view textview2 <TextView> is too close to the side of the device", it.summary)
          assertEquals(
            "In a preview configuration, the view TextView is closer to the side of the device than the recommended amount.<BR/>" +
            "It is recommended that, for Wear OS layouts, margins should be at least 2.5% for square devices, and 5.2% for round devices.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        "textview3" -> {
          assertEquals(3, it.models.size)
          assertEquals("The view textview3 <TextView> is too close to the side of the device", it.summary)
          assertEquals(
            "In 3 preview configurations, the view TextView is closer to the side of the device than the recommended amount.<BR/>" +
            "It is recommended that, for Wear OS layouts, margins should be at least 2.5% for square devices, and 5.2% for round devices.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
        "textview4" -> {
          assertEquals(1, it.models.size)
          assertEquals("The view textview4 <TextView> is too close to the side of the device", it.summary)
          assertEquals(
            "In a preview configuration, the view TextView is closer to the side of the device than the recommended amount.<BR/>" +
            "It is recommended that, for Wear OS layouts, margins should be at least 2.5% for square devices, and 5.2% for round devices.",
            it.description)
          assertEquals(HighlightSeverity.WARNING, it.severity)
        }
      }
    }
  }

  private fun analyzeFile(
    facet: AndroidFacet,
    files: List<VirtualFile>,
    configuration: Configuration,
  ) {
    files.forEach { file ->
      val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, null, facet, file, configuration)
      val psiFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, file) as XmlFile
      nlModel.syncWithPsi(AndroidPsiUtils.getRootTagSafely(psiFile)!!, emptyList<TagSnapshotTreeNode>())
      RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
        task.setDecorations(false)
        try {
          val result = task.render().get()
          val service = VisualLintService.getInstance(projectRule.project)
          service.analyzeAfterModelUpdate(issueProvider, result, nlModel, VisualLintBaseConfigIssues())
        }
        catch (ex: Exception) {
          throw RuntimeException(ex)
        }
      }
    }
  }
}