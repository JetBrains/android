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
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.type.DesignerTypeRegistrar
import com.android.tools.idea.rendering.NoSecurityManagerRenderService
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
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
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.android.facet.AndroidFacet
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class VisualLintServiceTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setup() {
    projectRule.fixture.testDataPath = TestUtils.resolveWorkspacePath("tools/adt/idea/designer/testData").toString()
    RenderTestUtil.beforeRenderTestCase()
    RenderService.setForTesting(projectRule.project, NoSecurityManagerRenderService(projectRule.project))
    DesignerTypeRegistrar.register(LayoutFileType)
    val visualLintInspections = arrayOf(BoundsAnalyzerInspection, BottomNavAnalyzerInspection, BottomAppBarAnalyzerInspection,
                                        TextFieldSizeAnalyzerInspection, OverlapAnalyzerInspection, LongTextAnalyzerInspection,
                                        ButtonSizeAnalyzerInspection, WearMarginAnalyzerInspection, AtfAnalyzerInspection)
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @After
  fun tearDown() {
    ApplicationManager.getApplication().invokeAndWait {
      RenderTestUtil.afterRenderTestCase()
    }
    RenderService.setForTesting(projectRule.project, null)
  }

  @Test
  fun runVisualLintAnalysis() {
    projectRule.load("projects/visualLintApplication")

    val visualLintService = VisualLintService.getInstance(projectRule.project)
    val visualLintIssueModel = visualLintService.issueModel

    val module = projectRule.getModule("app")
    val facet = AndroidFacet.getInstance(module)!!
    val dashboardLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/fragment_dashboard.xml")!!
    val nlModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, null, facet, dashboardLayout)
    val visualLintExecutorService = MoreExecutors.newDirectExecutorService()
    visualLintService.runVisualLintAnalysis(projectRule.fixture.testRootDisposable,
                                            VisualLintIssueProvider(projectRule.fixture.testRootDisposable), listOf(nlModel),
                                            visualLintExecutorService)
    visualLintExecutorService.waitForTasksToComplete()

    val issues = visualLintIssueModel.issues
    assertEquals(2, issues.size)
    issues.forEach {
      assertEquals("Visual Lint Issue", it.category)
    }

    val atfLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/atf_layout.xml")!!
    val atfModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, null, facet, atfLayout)
    VisualLintService.getInstance(projectRule.project)
      .runVisualLintAnalysis(projectRule.fixture.testRootDisposable, VisualLintIssueProvider(projectRule.fixture.testRootDisposable),
                             listOf(atfModel), visualLintExecutorService)
    visualLintExecutorService.waitForTasksToComplete()

    val atfIssues = visualLintIssueModel.issues
    assertEquals(2, atfIssues.size)
    atfIssues.forEach {
      assertEquals("Visual Lint Issue", it.category)
    }
    val clickIssue = atfIssues.filterIsInstance<VisualLintRenderIssue>()
      .filter { it.type == VisualLintErrorType.ATF }
    assertEquals(1, clickIssue.size)

    val wearLayout = projectRule.project.baseDir.findFileByRelativePath("app/src/main/res/layout/wear_layout.xml")!!
    val wearConfiguration = RenderTestUtil.getConfiguration(module, wearLayout, "wearos_small_round")
    val wearModel = SyncNlModel.create(projectRule.project, NlComponentRegistrar, null, null, facet, wearLayout, wearConfiguration)
    VisualLintService.getInstance(projectRule.project)
      .runVisualLintAnalysis(projectRule.fixture.testRootDisposable, VisualLintIssueProvider(projectRule.fixture.testRootDisposable),
                             listOf(wearModel), visualLintExecutorService)
    visualLintExecutorService.waitForTasksToComplete()

    val wearIssues = visualLintIssueModel.issues
    assertEquals(13, wearIssues.size)
    wearIssues.forEach {
      assertEquals("Visual Lint Issue", it.category)
    }
    val wearMarginIssues = wearIssues.filterIsInstance<VisualLintRenderIssue>()
      .filter { it.type == VisualLintErrorType.WEAR_MARGIN }
    assertEquals(5, wearMarginIssues.size)
  }
}

private fun ExecutorService.waitForTasksToComplete() {
  submit {}?.get(30, TimeUnit.SECONDS)
}