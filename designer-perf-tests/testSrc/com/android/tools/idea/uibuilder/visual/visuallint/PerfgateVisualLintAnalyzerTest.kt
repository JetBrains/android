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

import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.SyncNlModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.TagSnapshotTreeNode
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.ComposeRenderTestBase
import com.android.tools.idea.rendering.ElapsedTimeMeasurement
import com.android.tools.idea.rendering.RenderTestUtil
import com.android.tools.idea.rendering.VISUAL_LINT_APPLICATION_PATH
import com.android.tools.idea.rendering.measureOperation
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.perflogger.Metric
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderTask
import com.android.tools.visuallint.VisualLintAnalyzer
import com.android.tools.visuallint.VisualLintBaseConfigIssues
import com.android.tools.visuallint.analyzers.BottomAppBarAnalyzer
import com.android.tools.visuallint.analyzers.BottomNavAnalyzer
import com.android.tools.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.visuallint.analyzers.LocaleAnalyzer
import com.android.tools.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.visuallint.analyzers.TextFieldSizeAnalyzer
import com.intellij.psi.xml.XmlFile
import org.junit.Before
import org.junit.Test

class PerfgateVisualLintAnalyzerTest : ComposeRenderTestBase(VISUAL_LINT_APPLICATION_PATH) {
  @Before
  override fun setUp() {
    super.setUp()
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
      )
    projectRule.fixture.enableInspections(*visualLintInspections)
  }

  @Test
  fun bottomAppBarAnalyzerRun() {
    visualLintAnalyzerRun(BottomAppBarAnalyzer)
  }

  @Test
  fun bottomNavAnalyzerRun() {
    visualLintAnalyzerRun(BottomNavAnalyzer)
  }

  @Test
  fun boundsAnalyzerRun() {
    visualLintAnalyzerRun(BoundsAnalyzer)
  }

  @Test
  fun buttonSizeAnalyzerRun() {
    visualLintAnalyzerRun(ButtonSizeAnalyzer)
  }

  @Test
  fun localeAnalyzerRun() {
    visualLintAnalyzerRun(LocaleAnalyzer(VisualLintBaseConfigIssues()))
  }

  @Test
  fun longTextAnalyzerRun() {
    visualLintAnalyzerRun(LongTextAnalyzer)
  }

  @Test
  fun overlapAnalyzerRun() {
    visualLintAnalyzerRun(OverlapAnalyzer)
  }

  @Test
  fun textFieldSizeAnalyzerRun() {
    visualLintAnalyzerRun(TextFieldSizeAnalyzer)
  }

  private fun visualLintAnalyzerRun(analyzer: VisualLintAnalyzer) {
    val module = projectRule.getModule("app")
    val facet = projectRule.mainAndroidFacet(":app")
    val activityLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/activity_main.xml"
      )!!
    val dashboardLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/fragment_dashboard.xml"
      )!!
    val notificationsLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/fragment_notifications.xml"
      )!!
    val homeLayout =
      projectRule.project.baseDir.findFileByRelativePath(
        "app/src/main/res/layout/fragment_home.xml"
      )!!
    val filesToAnalyze = listOf(activityLayout, dashboardLayout, notificationsLayout, homeLayout)
    val deviceIds =
      listOf(
        "_device_class_phone",
        "_device_class_foldable",
        "_device_class_tablet",
        "_device_class_desktop",
      )

    val modelResultMap = mutableMapOf<NlModel, RenderResult>()
    deviceIds.forEach { deviceId ->
      val configuration =
        RenderTestUtil.getConfiguration(
          module,
          activityLayout,
          deviceId,
          "Theme.MaterialComponents.DayNight.DarkActionBar",
        )
      filesToAnalyze.forEach { file ->
        val nlModel =
          SyncNlModel.create(
            projectRule.fixture.projectDisposable,
            NlComponentRegistrar,
            AndroidBuildTargetReference.gradleOnly(facet),
            file,
            configuration,
          )
        val psiFile = AndroidPsiUtils.getPsiFileSafely(projectRule.project, file) as XmlFile
        nlModel.syncWithPsi(
          AndroidPsiUtils.getRootTagSafely(psiFile)!!,
          emptyList<TagSnapshotTreeNode>(),
        )
        RenderTestUtil.withRenderTask(facet, file, configuration) { task: RenderTask ->
          task.setDecorations(false)
          try {
            val result = task.inflate().get()
            modelResultMap[nlModel] = result!!
          } catch (ex: Exception) {
            throw RuntimeException(ex)
          }
        }
      }
    }
    visualLintingBenchmark.measureOperation(
      measures =
        listOf(
          ElapsedTimeMeasurement(Metric("${analyzer.type}_run_time"))
          // TODO(b/352075517): re-enable memory measurement once performance issue is fixed
          // HeapSnapshotMemoryUseMeasurement("android:designTools", null,
          // Metric("${analyzer.type}_memory_use"))
        ),
      samplesCount = NUMBER_OF_SAMPLES,
    ) {
      modelResultMap.forEach { (nlModel, renderResult) ->
        analyzer.findIssues(renderResult, nlModel.configuration)
      }
    }
  }
}
