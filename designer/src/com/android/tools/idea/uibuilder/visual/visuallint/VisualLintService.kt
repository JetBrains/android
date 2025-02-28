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
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintRenderIssue.Companion.createVisualLintRenderIssue
import com.android.tools.rendering.RenderAsyncActionExecutor.RenderingTopic
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.visuallint.VisualLintAnalyzer
import com.android.tools.visuallint.VisualLintBaseConfigIssues
import com.android.tools.visuallint.analyzers.AtfAnalyzer
import com.android.tools.visuallint.analyzers.BottomAppBarAnalyzer
import com.android.tools.visuallint.analyzers.BottomNavAnalyzer
import com.android.tools.visuallint.analyzers.BoundsAnalyzer
import com.android.tools.visuallint.analyzers.ButtonSizeAnalyzer
import com.android.tools.visuallint.analyzers.LocaleAnalyzer
import com.android.tools.visuallint.analyzers.LongTextAnalyzer
import com.android.tools.visuallint.analyzers.OverlapAnalyzer
import com.android.tools.visuallint.analyzers.TextFieldSizeAnalyzer
import com.android.tools.visuallint.analyzers.WearMarginAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.profile.codeInspection.InspectionProfileManager
import com.intellij.serviceContainer.AlreadyDisposedException
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.annotations.TestOnly

/** Dispatcher to trigger background visual linting analysis using only 1 thread */
@OptIn(ExperimentalCoroutinesApi::class)
private val visualLintSingleThreadedDispatcher = Dispatchers.Default.limitedParallelism(1)
/**
 * Dispatcher to run all the visual linting analyzers triggered from one analysis using only 1
 * thread
 */
@OptIn(ExperimentalCoroutinesApi::class)
private val visualLintAnalyzerSingleThreadedDispatcher = Dispatchers.Default.limitedParallelism(1)
/**
 * Time out for visual lint analysis. Use a longer one for testing to ensure it always completes
 * then.
 */
private val visualLintTimeout: Long =
  if (ApplicationManager.getApplication().isUnitTestMode) 30 else 5

private val LOG = Logger.getInstance(VisualLintService::class.java)

/** Service that runs visual lints. */
@Service(Service.Level.PROJECT)
class VisualLintService
private constructor(
  val project: Project,
  val coroutineScope: CoroutineScope,
  val visualLintDispatcher: CoroutineDispatcher,
  val visualLintAnalyzerDispatcher: CoroutineDispatcher,
) : Disposable {

  constructor(
    project: Project,
    coroutineScope: CoroutineScope,
  ) : this(
    project = project,
    coroutineScope = coroutineScope,
    visualLintDispatcher = visualLintSingleThreadedDispatcher,
    visualLintAnalyzerDispatcher = visualLintAnalyzerSingleThreadedDispatcher,
  )

  companion object {
    @JvmStatic
    fun getInstance(project: Project): VisualLintService {
      return project.getService(VisualLintService::class.java)
    }

    @TestOnly
    fun getInstanceForTest(
      project: Project,
      parentDisposable: Disposable,
      coroutineScope: CoroutineScope,
      visualLintDispatcher: CoroutineDispatcher,
      visualLintAnalyzerDispatcher: CoroutineDispatcher,
    ): VisualLintService {
      return VisualLintService(
          project = project,
          coroutineScope = coroutineScope,
          visualLintDispatcher = visualLintDispatcher,
          visualLintAnalyzerDispatcher = visualLintAnalyzerDispatcher,
        )
        .apply { Disposer.register(parentDisposable, this) }
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

  private var listenerRemovalDisposable: Disposable? = null

  init {
    // We pass the VisualLintService as the IssueModel parent disposable. We need to initialize it
    // only in the end of this constructor to
    // prevent Project leaks if the constructor throws an exception sooner, because that could make
    // the IssueModel never to be disposed,
    // since it will be registered as the child of a broken VisualLintService object.
    issueModel = VisualLintIssueModel(this, project)
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
  ) =
    coroutineScope.launch(visualLintDispatcher) {
      removeAllIssueProviders()
      issueProvider.clear()
      val wasAdded = issueModel.addIssueProvider(issueProvider, true)
      issueModel.uiCheckInstanceId = issueProvider.uiCheckInstanceId
      if (wasAdded) {
        listenerRemovalDisposable = Disposable { issueModel.removeIssueProvider(issueProvider) }
        Disposer.register(parentDisposable, listenerRemovalDisposable!!)
      }

      val visualLintBaseConfigIssues = VisualLintBaseConfigIssues()
      modelsForBackgroundRun.forEach {
        runBackgroundVisualLinting(it, issueProvider, visualLintBaseConfigIssues)
      }

      runOnPreviewVisualLinting(renderResultsForAnalysis, issueProvider, visualLintBaseConfigIssues)
    }

  /** Creates configurations based on the [baseModel] and run Visual Lint analysis on those. */
  private suspend fun runBackgroundVisualLinting(
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
          WearDeviceModelsProvider.createNlModels(baseModel, baseModel.file, baseModel.buildTarget)
        } else {
          WindowSizeModelsProvider.createNlModels(baseModel, baseModel.file, baseModel.buildTarget)
        }
      withTimeout(visualLintTimeout.seconds) {
        for (model in modelsToAnalyze) {
          val runAtfChecks = AtfAnalyzer.shouldRun(project, true)
          val result = createRenderResult(model, runAtfChecks).get()
          withContext(visualLintAnalyzerDispatcher) {
            try {
              updateHierarchy(result, model)
              analyzeAfterModelUpdate(
                issueProvider,
                result,
                model,
                visualLintBaseConfigIssues,
                true,
              )
            } finally {
              Disposer.dispose(model)
            }
          }
        }
      }
      issueModel.updateErrorsList(IssueProviderListener.TOPIC)
      LOG.debug(
        "Visual Lint analysis finished, ${issueModel.issueCount} ${if (issueModel.issueCount > 1) "errors" else "error"} found"
      )
    } finally {
      baseModel.removeListener(listener)
    }
  }

  /** Runs Visual Lint analysis on the given [RenderResult]s */
  private suspend fun runOnPreviewVisualLinting(
    renderResultsForAnalysis: Map<RenderResult, NlModel>,
    issueProvider: VisualLintIssueProvider,
    visualLintBaseConfigIssues: VisualLintBaseConfigIssues,
  ) {
    withTimeout(visualLintTimeout.seconds) {
      renderResultsForAnalysis.forEach { (result, model) ->
        withContext(visualLintAnalyzerDispatcher) {
          analyzeAfterModelUpdate(issueProvider, result, model, visualLintBaseConfigIssues)
        }
      }
    }
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
      runAnalyzers(
        targetIssueProvider,
        listOf(LocaleAnalyzer(baseConfigIssues)),
        result,
        model,
        runningInBackground,
      )
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
      .filter { it.shouldRun(project, runningInBackground) }
      .forEach { analyzer ->
        val profile = InspectionProfileManager.getInstance(project).currentProfile
        val tools = profile.getToolsOrNull(analyzer.type.shortName, project) ?: return@forEach
        if (!tools.isEnabled) {
          return@forEach
        }
        val inspection =
          tools.getInspectionTool(null).tool as? VisualLintInspection ?: return@forEach
        if (runningInBackground && !inspection.runInBackground) {
          return@forEach
        }
        val issues =
          analyzer.analyze(result).map { createVisualLintRenderIssue(it, model, analyzer.type) }
        targetIssueProvider.addAllIssues(issues)
      }
  }

  fun removeAllIssueProviders() {
    listenerRemovalDisposable?.let {
      // This disposable is not needed anymore, so we dispose it to avoid it hanging around in the
      // Disposer tree
      Disposer.dispose(it)
      listenerRemovalDisposable = null
    }
    issueModel.removeAllIssueProviders()
    issueModel.updateErrorsList()
  }

  override fun dispose() {
    listenerRemovalDisposable = null
    issueModel.removeAllIssueProviders()
  }
}

/** Inflates a model, then returns the completable future with render result. */
fun createRenderResult(model: NlModel, runAtfChecks: Boolean): CompletableFuture<RenderResult> {
  val renderService = StudioRenderService.getInstance(model.project)
  val logger = renderService.createHtmlLogger(model.project)

  return renderService
    .taskBuilder(model.buildTarget, model.configuration, logger)
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

      if (model.isDisposed) {
        newTask.dispose()
        return@thenCompose CompletableFuture.failedFuture(
          AlreadyDisposedException("NlModel was already disposed")
        )
      }

      // TODO: Potentially save this task for future?
      return@thenCompose newTask.inflate().whenComplete { result, inflateException ->
        val exception: Throwable? = inflateException ?: result?.renderResult?.exception
        newTask.dispose()
        if (exception != null || result == null) {
          logger.error(
            "INFLATE",
            "Error inflating views for visual lint on background",
            exception,
            null,
            null,
          )
        }
      }
    }
}

private fun VisualLintAnalyzer.shouldRun(project: Project, runningInBackground: Boolean): Boolean {
  val profile = InspectionProfileManager.getInstance(project).currentProfile
  val tools = profile.getToolsOrNull(this.type.shortName, project) ?: return false
  if (!tools.isEnabled) {
    return false
  }
  if (!runningInBackground) {
    return true
  }
  val inspection = tools.getInspectionTool(null).tool as? VisualLintInspection ?: return false
  return inspection.runInBackground
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
