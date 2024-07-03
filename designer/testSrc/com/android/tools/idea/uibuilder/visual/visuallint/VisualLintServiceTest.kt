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

import com.android.test.testutils.TestUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createNoSecurityRenderService
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater
import com.android.tools.idea.uibuilder.type.LayoutFileType
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzerInspection
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.WearMarginAnalyzerInspection
import com.android.tools.rendering.RenderTask
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class VisualLintServiceTest {

  @get:Rule val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath =
      TestUtils.resolveWorkspacePath("tools/adt/idea/designer/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    StudioRenderService.setForTesting(projectRule.project, createNoSecurityRenderService())
    DesignerTypeRegistrar.register(LayoutFileType)
    val visualLintInspections =
      arrayOf(
        BoundsAnalyzerInspection(),
        BottomNavAnalyzerInspection(),
        BottomAppBarAnalyzerInspection(),
        TextFieldSizeAnalyzerInspection(),
        OverlapAnalyzerInspection(),
        LongTextAnalyzerInspection(),
        ButtonSizeAnalyzerInspection(),
        WearMarginAnalyzerInspection(),
        AtfAnalyzerInspection(),
      )
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait { RenderTestUtil.afterRenderTestCase() }
    StudioRenderService.setForTesting(projectRule.project, null)
  }

  @Test
  fun runBackgroundVisualLintAnalysis() {
    projectRule.load("projects/visualLintApplication")

    val visualLintService = VisualLintService.getInstance(projectRule.project)
    val visualLintIssueModel = visualLintService.issueModel

    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val dashboardLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/fragment_dashboard.xml"
      )!!
    val nlModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        dashboardLayout,
      )
    val visualLintExecutorService = MoreExecutors.newDirectExecutorService()
    visualLintService.runVisualLintAnalysis(
      projectRule.fixture.testRootDisposable,
      ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
      listOf(nlModel),
      emptyMap(),
      visualLintExecutorService,
    )
    visualLintExecutorService.waitForTasksToComplete()

    val issues = visualLintIssueModel.issues
    assertEquals(2, issues.size)
    issues.forEach { assertEquals("Visual Lint Issue", it.category) }

    val atfLayout =
      projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/atf_layout.xml")!!
    val atfModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        atfLayout,
      )
    VisualLintService.getInstance(projectRule.project)
      .runVisualLintAnalysis(
        projectRule.fixture.testRootDisposable,
        ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
        listOf(atfModel),
        emptyMap(),
        visualLintExecutorService,
      )
    visualLintExecutorService.waitForTasksToComplete()

    val atfIssues = visualLintIssueModel.issues
    assertEquals(1, atfIssues.size)
    atfIssues.forEach {
      assertEquals("Visual Lint Issue", it.category)
      assertFalse((it as VisualLintRenderIssue).type.isAtfErrorType())
    }

    val wearLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/wear_layout.xml"
      )!!
    val wearConfiguration =
      RenderTestUtil.getConfiguration(module, wearLayout, "wearos_small_round")
    val wearModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        wearLayout,
        wearConfiguration,
      )
    VisualLintService.getInstance(projectRule.project)
      .runVisualLintAnalysis(
        projectRule.fixture.testRootDisposable,
        ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
        listOf(wearModel),
        emptyMap(),
        visualLintExecutorService,
      )
    visualLintExecutorService.waitForTasksToComplete()

    val wearIssues = visualLintIssueModel.issues
    assertEquals(13, wearIssues.size)
    wearIssues.forEach { assertEquals("Visual Lint Issue", it.category) }
    val wearMarginIssues =
      wearIssues.filterIsInstance<VisualLintRenderIssue>().filter {
        it.type == VisualLintErrorType.WEAR_MARGIN
      }
    assertEquals(5, wearMarginIssues.size)
  }

  @Test
  fun runOnPreviewVisualLintAnalysis() {
    projectRule.load("projects/visualLintApplication")

    val visualLintService = VisualLintService.getInstance(projectRule.project)
    val visualLintIssueModel = visualLintService.issueModel

    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val visualLintExecutorService = MoreExecutors.newDirectExecutorService()
    val notificationsLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/fragment_notifications.xml"
      )!!

    val phoneConfig =
      RenderTestUtil.getConfiguration(
        module,
        notificationsLayout,
        "_device_class_phone",
        "Theme.MaterialComponents.DayNight.DarkActionBar",
      )
    val phoneModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        notificationsLayout,
        phoneConfig,
      )
    RenderTestUtil.withRenderTask(facet, notificationsLayout, phoneConfig) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        visualLintService.runVisualLintAnalysis(
          projectRule.fixture.testRootDisposable,
          ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
          emptyList(),
          mapOf(result to phoneModel),
          visualLintExecutorService,
        )
        visualLintExecutorService.waitForTasksToComplete()
        val issues = visualLintIssueModel.issues
        assertEquals(0, issues.size)
      } catch (ex: Exception) {
        throw RuntimeException(ex)
      }
    }

    val tabletConfig =
      RenderTestUtil.getConfiguration(
        module,
        notificationsLayout,
        "_device_class_tablet",
        "Theme.MaterialComponents.DayNight.DarkActionBar",
      )
    val tabletModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        notificationsLayout,
        tabletConfig,
      )
    RenderTestUtil.withRenderTask(facet, notificationsLayout, tabletConfig) { task: RenderTask ->
      task.setDecorations(false)
      try {
        val result = task.render().get()
        visualLintService.runVisualLintAnalysis(
          projectRule.fixture.testRootDisposable,
          ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
          emptyList(),
          mapOf(result to tabletModel),
          visualLintExecutorService,
        )
        visualLintExecutorService.waitForTasksToComplete()
        val issues = visualLintIssueModel.issues
        assertEquals(1, issues.size)
        assertEquals("Visual Lint Issue", issues[0].category)
      } catch (ex: Exception) {
        throw RuntimeException(ex)
      }
    }

    val atfLayout =
      projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/atf_layout.xml")!!
    val atfModel =
      SyncNlModel.create(
        projectRule.fixture.testRootDisposable,
        NlComponentRegistrar,
        BuildTargetReference.gradleOnly(facet),
        atfLayout,
        phoneConfig,
      )
    RenderTestUtil.withRenderTask(facet, atfLayout, phoneConfig) { task: RenderTask ->
      task.setDecorations(false)
      task.setEnableLayoutScanner(true)
      try {
        val result = task.render().get()
        NlModelHierarchyUpdater.updateHierarchy(result, atfModel)
        visualLintService.runVisualLintAnalysis(
          projectRule.fixture.testRootDisposable,
          ViewVisualLintIssueProvider(projectRule.fixture.testRootDisposable),
          emptyList(),
          mapOf(result to atfModel),
          visualLintExecutorService,
        )
        visualLintExecutorService.waitForTasksToComplete()
        val issues = visualLintIssueModel.issues
        assertEquals(3, issues.size)
        val clickIssue =
          issues.filterIsInstance<VisualLintRenderIssue>().filter { it.type.isAtfErrorType() }
        assertEquals(2, clickIssue.size)
      } catch (ex: Exception) {
        throw RuntimeException(ex)
      }
    }
  }
}

private fun ExecutorService.waitForTasksToComplete() {
  submit {}?.get(30, TimeUnit.SECONDS)
}
