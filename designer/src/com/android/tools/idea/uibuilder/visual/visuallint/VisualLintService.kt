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

import com.android.sdklib.devices.Device
import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProviderListener
import com.android.tools.idea.common.model.ModelListener
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.createHtmlLogger
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.rendering.taskBuilder
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
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pool of 1 thread to trigger background visual linting analysis one at a time, and wait for its
 * completion
 */
private val visualLintExecutorService =
  AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Service", 1)
/** Pool of 1 thread to run all the visual linting analyzers triggered from one analysis */
private val visualLintAnalyzerExecutorService =
  AppExecutorUtil.createBoundedApplicationPoolExecutor("Visual Lint Analyzer", 1)
/**
 * Time out for visual lint analysis. Use a longer one for testing to ensure it always completes
 * then.
 */
private val visualLintTimeout: Long =
  if (ApplicationManager.getApplication().isUnitTestMode) 30 else 5

private val LOG = Logger.getInstance(VisualLintService::class.java)

/** Service that runs visual lints. */
@Service(Service.Level.PROJECT)
class VisualLintService(val project: Project) : Disposable {

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintService {
      return project.getService(VisualLintService::class.java)
    }
  }

  val issueModel: VisualLintIssueModel

  private val basicAnalyzers = listOf(BoundsAnalyzer, OverlapAnalyzer, AtfAnalyzer)
  private val adaptiveAnalyzers =
    listOf(
      BottomNavAnalyzer,
      BottomAppBarAnalyzer,
      TextFieldSizeAnalyzer,
      LongTextAnalyzer,
      ButtonSizeAnalyzer,
    )
  private val wearAnalyzers = listOf(WearMarginAnalyzer)

  private val ignoredTypes: MutableList<VisualLintErrorType>

  init {
    val connection = project.messageBus.connect()
    ignoredTypes = mutableListOf()
    getIgnoredTypesFromProfile(InspectionProfileManager.getInstance(project).currentProfile)
    connection.subscribe(
      ProfileChangeAdapter.TOPIC,
      object : ProfileChangeAdapter {
        override fun profileActivated(oldProfile: InspectionProfile?, profile: InspectionProfile?) {
          profile?.let { getIgnoredTypesFromProfile(it) }
        }

        override fun profileChanged(profile: InspectionProfile) {
          val oldIgnoredTypes = ignoredTypes.toList()
          getIgnoredTypesFromProfile(profile)
          ignoredTypes
            .filterNot { it in oldIgnoredTypes }
            .forEach { VisualLintUsageTracker.getInstance().trackRuleStatusChanged(it, false) }
          oldIgnoredTypes
            .filterNot { it in ignoredTypes }
            .forEach { VisualLintUsageTracker.getInstance().trackRuleStatusChanged(it, true) }
        }
      },
    )
    // We pass the VisualLintService as the IssueModel parent disposable. We need to initialize it
    // only in the end of this constructor to
    // prevent Project leaks if the constructor throws an exception sooner, because that could make
    // the IssueModel never to be disposed,
    // since it will be registered as the child of a broken VisualLintService object.
    issueModel = VisualLintIssueModel(this, project)
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
   * Runs visual lint analysis in a pooled thread and adds the issues found to the [IssueModel]. For
   * models in [modelsForBackgroundRun], it creates related configurations to analyze. For renders
   * in [renderResultsForAnalysis], it simply analyzes the given results.
   */
  fun runVisualLintAnalysis(
    parentDisposable: Disposable,
    issueProvider: VisualLintIssueProvider,
    modelsForBackgroundRun: List<NlModel>,
    renderResultsForAnalysis: Map<RenderResult, NlModel>,
  ) {
    runVisualLintAnalysis(
      parentDisposable,
      issueProvider,
      modelsForBackgroundRun,
      renderResultsForAnalysis,
      visualLintExecutorService,
    )
  }

  @VisibleForTesting
  fun runVisualLintAnalysis(
    parentDisposable: Disposable,
    issueProvider: VisualLintIssueProvider,
    modelsForBackgroundRun: List<NlModel>,
    renderResultsForAnalysis: Map<RenderResult, NlModel>,
    executorService: ExecutorService,
  ) {
    CompletableFuture.runAsync(
      {
        removeAllIssueProviders()
        issueProvider.clear()
        val wasAdded = issueModel.addIssueProvider(issueProvider, true)
        issueModel.uiCheckInstanceId = issueProvider.uiCheckInstanceId
        if (wasAdded) {
          Disposer.register(parentDisposable) { issueModel.removeIssueProvider(issueProvider) }
        }

        val visualLintBaseConfigIssues = VisualLintBaseConfigIssues()
        modelsForBackgroundRun.forEach {
          runBackgroundVisualLinting(it, issueProvider, visualLintBaseConfigIssues)
        }

        runOnPreviewVisualLinting(
          renderResultsForAnalysis,
          issueProvider,
          visualLintBaseConfigIssues,
        )
      },
      executorService,
    )
  }

  /** Creates configurations based on the [baseModel] and run Visual Lint analysis on those. */
  private fun runBackgroundVisualLinting(
    baseModel: NlModel,
    issueProvider: VisualLintIssueProvider,
    visualLintBaseConfigIssues: VisualLintBaseConfigIssues,
  ) {
    val listener =
      object : ModelListener {
        override fun modelChanged(model: NlModel) {
          val numberOfCancelledActions =
            RenderService.getRenderAsyncActionExecutor()
              .cancelActionsByTopic(listOf(RenderingTopic.VISUAL_LINT), false)
          if (numberOfCancelledActions > 0) {
            VisualLintUsageTracker.getInstance().trackCancelledBackgroundAnalysis()
          }
        }
      }
    baseModel.addListener(listener)
    try {
      val modelsToAnalyze =
        if (Device.isWear(baseModel.configuration.device)) {
          WearDeviceModelsProvider.createNlModels(baseModel, baseModel.file, baseModel.facet)
        } else {
          WindowSizeModelsProvider.createNlModels(baseModel, baseModel.file, baseModel.facet)
        }
      val latch = CountDownLatch(modelsToAnalyze.size)
      val hasTimedOut = AtomicBoolean(false)
      for (model in modelsToAnalyze) {
        val runAtfChecks = VisualLintErrorType.ATF !in ignoredTypes
        createRenderResult(model, runAtfChecks)
          .handleAsync(
            { result, _ ->
              try {
                if (!hasTimedOut.get() && result != null) {
                  updateHierarchy(result, model)
                  analyzeAfterModelUpdate(
                    issueProvider,
                    result,
                    model,
                    visualLintBaseConfigIssues,
                    true,
                  )
                }
              } finally {
                Disposer.dispose(model)
                latch.countDown()
              }
            },
            visualLintAnalyzerExecutorService,
          )
      }
      hasTimedOut.set(!latch.await(visualLintTimeout, TimeUnit.SECONDS))
      issueModel.updateErrorsList(IssueProviderListener.TOPIC)
      LOG.debug(
        "Visual Lint analysis finished, ${issueModel.issueCount} ${if (issueModel.issueCount > 1) "errors" else "error"} found"
      )
    } finally {
      baseModel.removeListener(listener)
    }
  }

  /** Runs Visual Lint analysis on the given [RenderResult]s */
  private fun runOnPreviewVisualLinting(
    renderResultsForAnalysis: Map<RenderResult, NlModel>,
    issueProvider: VisualLintIssueProvider,
    visualLintBaseConfigIssues: VisualLintBaseConfigIssues,
  ) {
    val latch = CountDownLatch(renderResultsForAnalysis.size)
    val hasTimedOut = AtomicBoolean(false)
    renderResultsForAnalysis.forEach { (result, model) ->
      CompletableFuture.runAsync(
        {
          if (!hasTimedOut.get()) {
            analyzeAfterModelUpdate(issueProvider, result, model, visualLintBaseConfigIssues)
          }
          latch.countDown()
        },
        visualLintAnalyzerExecutorService,
      )
    }
    hasTimedOut.set(!latch.await(visualLintTimeout, TimeUnit.SECONDS))
    issueModel.updateErrorsList(IssueProviderListener.UI_CHECK)
    LOG.debug(
      "Visual Lint analysis finished, ${issueModel.issueCount} ${if (issueModel.issueCount > 1) "errors" else "error"} found"
    )
  }

  /**
   * Collects in [issueProvider] all the [RenderErrorModel.Issue] found when analyzing the given
   * [RenderResult] after model is updated.
   */
  fun analyzeAfterModelUpdate(
    targetIssueProvider: VisualLintIssueProvider,
    result: RenderResult,
    model: NlModel,
    baseConfigIssues: VisualLintBaseConfigIssues,
    runningInBackground: Boolean = false,
  ) {
    runAnalyzers(targetIssueProvider, basicAnalyzers, result, model, runningInBackground)
    if (Device.isWear(model.configuration.device)) {
      runAnalyzers(targetIssueProvider, wearAnalyzers, result, model, runningInBackground)
    } else {
      runAnalyzers(targetIssueProvider, adaptiveAnalyzers, result, model, runningInBackground)
      if (VisualLintErrorType.LOCALE_TEXT !in ignoredTypes) {
        LocaleAnalyzer(baseConfigIssues).let {
          targetIssueProvider.addAllIssues(
            it.analyze(result, model, getSeverity(it.type), runningInBackground)
          )
        }
      }
    }
  }

  private fun runAnalyzers(
    targetIssueProvider: VisualLintIssueProvider,
    analyzers: List<VisualLintAnalyzer>,
    result: RenderResult,
    model: NlModel,
    runningInBackground: Boolean,
  ) {
    analyzers
      .filter { !ignoredTypes.contains(it.type) }
      .forEach {
        val issues = it.analyze(result, model, getSeverity(it.type), runningInBackground)
        targetIssueProvider.addAllIssues(issues)
      }
  }

  private fun getSeverity(type: VisualLintErrorType): HighlightSeverity {
    val key = HighlightDisplayKey.find(type.shortName)
    return key?.let {
      InspectionProfileManager.getInstance(project).currentProfile.getErrorLevel(it, null).severity
    } ?: HighlightSeverity.WARNING
  }

  fun removeAllIssueProviders() {
    issueModel.removeAllIssueProviders()
    issueModel.updateErrorsList()
  }

  override fun dispose() {
    issueModel.removeAllIssueProviders()
  }
}

/** Inflates a model, then returns the completable future with render result. */
fun createRenderResult(model: NlModel, runAtfChecks: Boolean): CompletableFuture<RenderResult> {
  val renderService = StudioRenderService.getInstance(model.project)
  val logger = renderService.createHtmlLogger(model.project)

  return renderService
    .taskBuilder(model.facet, model.configuration, logger)
    .withPsiFile(PsiXmlFile(model.file))
    .withLayoutScanner(runAtfChecks)
    .withTopic(RenderingTopic.VISUAL_LINT)
    .withQuality(0.25f)
    .build()
    .thenCompose { newTask ->
      if (newTask == null) {
        logger.error(
          "INFLATE",
          "Error inflating view for visual lint on background. No RenderTask Created.",
          null,
          null,
          null,
        )
        return@thenCompose CompletableFuture.failedFuture(IllegalArgumentException())
      }

      // TODO: Potentially save this task for future?
      return@thenCompose newTask.inflate().whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result.renderResult.exception
        if (exception != null || result == null) {
          logger.error(
            "INFLATE",
            "Error inflating views for visual lint on background",
            exception,
            null,
            null,
          )
        }
        newTask.dispose()
      }
    }
}

class VisualLintIssueModel(parentDisposable: Disposable, project: Project) :
  IssueModel(parentDisposable, project) {

  /** If using in UI Check mode, represents the Compose Preview instance being checked. */
  var uiCheckInstanceId: String? = null
}

enum class VisualLintMode {
  DISABLED,
  RUN_ON_PREVIEW_ONLY,
  RUN_IN_BACKGROUND,
}
