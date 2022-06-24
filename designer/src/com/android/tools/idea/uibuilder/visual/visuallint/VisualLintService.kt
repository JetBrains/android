/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.ide.common.rendering.HardwareConfigHelper
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.rendering.RenderAsyncActionExecutor
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.uibuilder.scene.NlModelHierarchyUpdater.updateHierarchy
import com.android.tools.idea.uibuilder.visual.WearDeviceModelsProvider
import com.android.tools.idea.uibuilder.visual.WindowSizeModelsProvider
import com.android.tools.idea.uibuilder.visual.analytics.VisualLintUsageTracker
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.AtfAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomAppBarAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BottomNavAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LocaleAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.TextFieldSizeAnalyzer
import com.android.tools.idea.uibuilder.visual.visuallint.analyzers.WearMarginAnalyzer
import com.google.common.annotations.VisibleForTesting
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionProfile
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService

/**
 * Pool of 1 thread to trigger background visual linting analysis one at a time, and wait for its completion
 */
private val visualLintExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Service", 1)
/**
 * Pool of 1 thread to run all the visual linting analyzers triggered from one analysis
 */
private val visualLintAnalyzerExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Analyzer", 1)

/**
 * Service that runs visual lints
 */
@Service
class VisualLintService(project: Project) {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintService {
      return project.getService(VisualLintService::class.java)
    }
  }

  /** Default issue provider for Visual Lint Service. */
  val issueProvider = VisualLintIssueProvider(project)

  private val basicAnalyzers = listOf(BoundsAnalyzer, OverlapAnalyzer)
  private val adaptiveAnalyzers = listOf(BottomNavAnalyzer, BottomAppBarAnalyzer, TextFieldSizeAnalyzer,
                                         LongTextAnalyzer, ButtonSizeAnalyzer)
  private val wearAnalyzers = listOf(WearMarginAnalyzer)

  private val ignoredTypes: MutableList<VisualLintErrorType>

  init {
    val connection = project.messageBus.connect()
    ignoredTypes = mutableListOf()
    getIgnoredTypesFromProfile(InspectionProfileManager.getInstance(project).currentProfile)
    connection.subscribe(ProfileChangeAdapter.TOPIC, object: ProfileChangeAdapter {
      override fun profileActivated(oldProfile: InspectionProfile?, profile: InspectionProfile?) {
        profile?.let { getIgnoredTypesFromProfile(it) }
      }

      override fun profileChanged(profile: InspectionProfile) {
        getIgnoredTypesFromProfile(profile)
      }
    })
  }

  private fun getIgnoredTypesFromProfile(profile: InspectionProfile) {
    ignoredTypes.clear()
    for (type in VisualLintErrorType.values()) {
      val enabled = profile.isToolEnabled(HighlightDisplayKey.find(type.shortName))
      if (!enabled) {
        ignoredTypes.add(type)
      }
    }
  }

  fun removeIssues(surface: DesignSurface<*>) {
    surface.issueModel.removeIssueProvider(issueProvider)
  }

  /**
   * Runs visual lint analysis in a pooled thread for configurations based on the model provided,
   * and adds the issues found to the [IssueModel]
   */
  fun runVisualLintAnalysis(models: List<NlModel>, surface: DesignSurface<*>) {
    runVisualLintAnalysis(models, surface, visualLintExecutorService)
  }

  @VisibleForTesting
  fun runVisualLintAnalysis(models: List<NlModel>, surface: DesignSurface<*>, executorService: ExecutorService) {
    CompletableFuture.runAsync({
      val issueModel = surface.issueModel
      issueModel.removeIssueProvider(issueProvider)
      issueProvider.clear()
      if (models.isEmpty()) {
        return@runAsync
      }

      issueModel.addIssueProvider(issueProvider, false)
      val displayingModel = models[0]
      val listener = object : ModelListener {
        override fun modelChanged(model: NlModel) {
          RenderService.getRenderAsyncActionExecutor().cancelLowerPriorityActions(
            RenderAsyncActionExecutor.RenderingPriority.LOW)
        }
      }
      displayingModel.addListener(listener)
      try {
        val modelsToAnalyze = if (HardwareConfigHelper.isWear(displayingModel.configuration.device)) {
          WearDeviceModelsProvider.createNlModels(displayingModel, displayingModel.file, displayingModel.facet)
        } else {
          WindowSizeModelsProvider.createNlModels(displayingModel, displayingModel.file, displayingModel.facet)
        }
        val latch = CountDownLatch(modelsToAnalyze.size)
        val visualLintBaseConfigIssues = VisualLintBaseConfigIssues()
        for (model in modelsToAnalyze) {
          inflate(model).handleAsync({ result, _ ->
            if (result != null) {
              updateHierarchy(result, model)
              analyzeAfterModelUpdate(result, model, visualLintBaseConfigIssues, VisualLintUsageTracker.getInstance(surface), true)
            }
            Disposer.dispose(model)
            latch.countDown()
          }, visualLintAnalyzerExecutorService)
        }
        latch.await()
        issueModel.updateErrorsList()
      } finally {
        displayingModel.removeListener(listener)
      }
    }, executorService)
  }

  /**
   * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
   */
  fun analyzeAfterModelUpdate(result: RenderResult,
                              model: NlModel,
                              baseConfigIssues: VisualLintBaseConfigIssues,
                              tracker: VisualLintUsageTracker,
                              runningInBackground: Boolean = false) {
    runAnalyzers(basicAnalyzers, result, model, tracker, runningInBackground)
    if (HardwareConfigHelper.isWear(model.configuration.device)) {
      runAnalyzers(wearAnalyzers, result, model, tracker, runningInBackground)
    } else {
      runAnalyzers(adaptiveAnalyzers, result, model, tracker, runningInBackground)
      if (VisualLintErrorType.LOCALE_TEXT !in ignoredTypes) {
        LocaleAnalyzer(baseConfigIssues).let {
          issueProvider.addAllIssues(it.type, it.analyze(result, model, tracker, runningInBackground))
        }
      }
      if (StudioFlags.NELE_ATF_IN_VISUAL_LINT.get() && VisualLintErrorType.ATF !in ignoredTypes) {
        AtfAnalyzer.analyze(result, model, issueProvider, runningInBackground)
      }
    }
  }

  private fun runAnalyzers(analyzers: List<VisualLintAnalyzer>,
                           result: RenderResult,
                           model: NlModel,
                           tracker: VisualLintUsageTracker,
                           runningInBackground: Boolean) {
    analyzers.filter { !ignoredTypes.contains(it.type) }.forEach {
      val issues = it.analyze(result, model, tracker, runningInBackground)
      issueProvider.addAllIssues(it.type, issues)
    }
  }
}

/**
 * Inflate a view, then return the completable future with render result.
 */
fun inflate(model: NlModel): CompletableFuture<RenderResult> {
  val renderService = RenderService.getInstance(model.project)
  val logger = renderService.createLogger(model.facet)

  return renderService.taskBuilder(model.facet, model.configuration)
    .withPsiFile(model.file)
    .withLayoutScanner(false)
    .withLogger(logger)
    .withPriority(RenderAsyncActionExecutor.RenderingPriority.LOW)
    .build().thenCompose { newTask ->
      if (newTask == null) {
        logger.error("INFLATE", "Error inflating view for visual lint on background. No RenderTask Created.",
        null, null, null)
        return@thenCompose CompletableFuture.failedFuture(IllegalArgumentException())
      }

      // TODO: Potentially save this task for future?
      return@thenCompose newTask.inflate().whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result.renderResult.exception
        if (exception != null || result == null) {
          logger.error("INFLATE", "Error inflating views for visual lint on background", exception, null, null)
        }
        newTask.dispose()
      }
    }
}
