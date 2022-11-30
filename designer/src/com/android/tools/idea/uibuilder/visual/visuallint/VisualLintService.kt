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
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.ProfileChangeAdapter
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/**
 * Pool of 1 thread to trigger background visual linting analysis one at a time, and wait for its completion
 */
private val visualLintExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Service", 1)
/**
 * Pool of 1 thread to run all the visual linting analyzers triggered from one analysis
 */
private val visualLintAnalyzerExecutorService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Analyzer", 1)
/** Time out for visual lint analysis. Use a longer one for testing to ensure it always completes then. */
private val visualLintTimeout: Long = if (ApplicationManager.getApplication().isUnitTestMode) 30 else 5

private val LOG = Logger.getInstance(VisualLintService::class.java)

/**
 * Service that runs visual lints
 */
@Service
class VisualLintService(val project: Project): Disposable {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintService {
      return project.getService(VisualLintService::class.java)
    }
  }

  val issueModel: IssueModel = IssueModel(this, project)

  private val basicAnalyzers = listOf(BoundsAnalyzer, OverlapAnalyzer, AtfAnalyzer)
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
        val oldIgnoredTypes = ignoredTypes.toList()
        getIgnoredTypesFromProfile(profile)
        ignoredTypes.filterNot { it in oldIgnoredTypes }.forEach { VisualLintUsageTracker.getInstance().trackRuleStatusChanged(it, false) }
        oldIgnoredTypes.filterNot { it in ignoredTypes }.forEach { VisualLintUsageTracker.getInstance().trackRuleStatusChanged(it, true) }
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

  /**
   * Runs visual lint analysis in a pooled thread for configurations based on the model provided,
   * and adds the issues found to the [IssueModel]
   */
  fun runVisualLintAnalysis(parentDisposable: Disposable, issueProvider: VisualLintIssueProvider, models: List<NlModel>) {
    runVisualLintAnalysis(parentDisposable, issueProvider, models, visualLintExecutorService)
  }

  @VisibleForTesting
  fun runVisualLintAnalysis(parentDisposable: Disposable, issueProvider: VisualLintIssueProvider, models: List<NlModel>, executorService: ExecutorService) {
    CompletableFuture.runAsync({
      removeAllIssueProviders()
      issueProvider.clear()
      Disposer.register(parentDisposable) {
        issueModel.removeIssueProvider(issueProvider)
      }
      issueModel.addIssueProvider(issueProvider, true)
      if (models.isEmpty()) {
        return@runAsync
      }

      val displayingModel = models[0]
      val listener = object : ModelListener {
        override fun modelChanged(model: NlModel) {
          val numberOfCancelledActions = RenderService.getRenderAsyncActionExecutor().cancelLowerPriorityActions(
            RenderAsyncActionExecutor.RenderingPriority.LOW)
          if (numberOfCancelledActions > 0) {
            VisualLintUsageTracker.getInstance().trackCancelledBackgroundAnalysis()
          }
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
          val requireRender = StudioFlags.NELE_ATF_IN_VISUAL_LINT.get() && VisualLintErrorType.ATF !in ignoredTypes
          createRenderResult(model, requireRender).handleAsync({ result, _ ->
            try {
              if (result != null) {
                updateHierarchy(result, model)
                analyzeAfterModelUpdate(issueProvider, result, model, visualLintBaseConfigIssues, true)
              }
            } finally {
              Disposer.dispose(model)
              latch.countDown()
            }
          }, visualLintAnalyzerExecutorService)
        }
        latch.await(visualLintTimeout, TimeUnit.SECONDS)
        issueModel.updateErrorsList()
        LOG.debug("Visual Lint analysis finished, ${issueModel.issueCount} ${if (issueModel.issueCount > 1) "errors" else "error"} found")
      } finally {
        displayingModel.removeListener(listener)
      }
    }, executorService)
  }

  /**
   * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given [RenderResult] after model is updated.
   */
  fun analyzeAfterModelUpdate(targetIssueProvider: VisualLintIssueProvider,
                              result: RenderResult,
                              model: NlModel,
                              baseConfigIssues: VisualLintBaseConfigIssues,
                              runningInBackground: Boolean = false) {
    runAnalyzers(targetIssueProvider, basicAnalyzers, result, model, runningInBackground)
    if (HardwareConfigHelper.isWear(model.configuration.device)) {
      runAnalyzers(targetIssueProvider, wearAnalyzers, result, model, runningInBackground)
    } else {
      runAnalyzers(targetIssueProvider, adaptiveAnalyzers, result, model, runningInBackground)
      if (VisualLintErrorType.LOCALE_TEXT !in ignoredTypes) {
        LocaleAnalyzer(baseConfigIssues).let {
          targetIssueProvider.addAllIssues(it.type, it.analyze(result, model, getSeverity(it.type), runningInBackground))
        }
      }
    }
  }

  private fun runAnalyzers(targetIssueProvider: VisualLintIssueProvider,
                           analyzers: List<VisualLintAnalyzer>,
                           result: RenderResult,
                           model: NlModel,
                           runningInBackground: Boolean) {
    analyzers.filter { !ignoredTypes.contains(it.type) }.forEach {
      val issues = it.analyze(result, model, getSeverity(it.type), runningInBackground)
      targetIssueProvider.addAllIssues(it.type, issues)
    }
  }

  private fun getSeverity(type: VisualLintErrorType): HighlightSeverity {
    val key = HighlightDisplayKey.find(type.shortName)
    return key?.let { InspectionProfileManager.getInstance(project).currentProfile.getErrorLevel(it, null).severity }
           ?: HighlightSeverity.WARNING
    }

  fun removeAllIssueProviders() {
    issueModel.removeAllIssueProviders()
    issueModel.updateErrorsList()
  }

  override fun dispose() {
    issueModel.removeAllIssueProviders()
  }
}

/**
 * Inflates or renders a model, then returns the completable future with render result.
 */
fun createRenderResult(model: NlModel, requireRender: Boolean): CompletableFuture<RenderResult> {
  val renderService = RenderService.getInstance(model.project)
  val logger = renderService.createLogger(model.facet)

  return renderService.taskBuilder(model.facet, model.configuration)
    .withPsiFile(model.file)
    .withLayoutScanner(requireRender)
    .withLogger(logger)
    .withPriority(RenderAsyncActionExecutor.RenderingPriority.LOW)
    .withMinDownscalingFactor(0.25f)
    .withQuality(0f)
    .build().thenCompose { newTask ->
      if (newTask == null) {
        logger.error("INFLATE", "Error inflating view for visual lint on background. No RenderTask Created.",
        null, null, null)
        return@thenCompose CompletableFuture.failedFuture(IllegalArgumentException())
      }

      // TODO: Potentially save this task for future?
      val renderResult = if (requireRender) newTask.render() else newTask.inflate()
      return@thenCompose renderResult.whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result.renderResult.exception
        if (exception != null || result == null) {
          logger.error("INFLATE", "Error inflating views for visual lint on background", exception, null, null)
        }
        newTask.dispose()
      }
    }
}
