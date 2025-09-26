/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.error.IssueProvider
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.scene.SceneManager
import com.android.tools.idea.common.surface.LayoutScannerControl
import com.android.tools.idea.concurrency.createCoroutineScope
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.rendering.RenderErrorModelFactory
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel
import com.android.tools.idea.ui.designer.EditorDesignSurface
import com.android.tools.idea.uibuilder.error.RenderIssueProvider
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory.Companion.hasVisibleValidationWindow
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.android.tools.rendering.RenderResult
import com.android.utils.associateWithNotNull
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.update.MergingUpdateQueue
import kotlin.collections.map
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private data class ErrorUpdate(
  private val scannerControl: LayoutScannerControl?,
  private val visualLintIssueProvider: VisualLintIssueProvider,
  private val surface: EditorDesignSurface,
  private val issueModel: IssueModel,
  private val parentDisposable: Disposable,
  private val sceneManagers: () -> List<LayoutlibSceneManager>,
  private val project: Project,
  private val updateRenderIssueProviders: (ImmutableList<RenderIssueProvider>) -> Unit,
) {
  private fun triggerRenderIssueProvidersUpdate(
    renderResults: Map<out SceneManager, RenderResult>
  ) {
    var newRenderIssueProviders: ImmutableList<RenderIssueProvider>? = null
    if (project.getProjectSystem().getBuildManager().isBuilding) {
      for ((manager, renderResult) in renderResults) {
        if (
          renderResult.logger.hasErrors()
        ) { // We are still building, display the message to the user.
          newRenderIssueProviders =
            persistentListOf(
              RenderIssueProvider(manager.model, RenderErrorModel.STILL_BUILDING_ERROR_MODEL)
            )
          break
        }
      }
    }

    if (
      newRenderIssueProviders == null
    ) { // createErrorModel needs to run in Smart mode to resolve the classes correctly
      newRenderIssueProviders =
        renderResults
          .map {
            val errorModel = RenderErrorModelFactory.createErrorModel(surface, it.value)
            RenderIssueProvider(it.key.model, errorModel)
          }
          .toImmutableList()
    }
    updateRenderIssueProviders(newRenderIssueProviders)
  }

  private fun triggerVisualLintUpdate(renderResults: Map<LayoutlibSceneManager, RenderResult>) {
    val hasLayoutValidationOpen = hasVisibleValidationWindow(project)
    var hasRunAtfOnMainPreview = hasLayoutValidationOpen
    if (!hasLayoutValidationOpen) {
      val modelsForBackgroundRun: MutableList<NlModel> = ArrayList()
      val renderResultsForAnalysis: MutableMap<RenderResult, NlModel> = HashMap()
      renderResults.forEach { (manager: LayoutlibSceneManager, result: RenderResult) ->
        when (manager.visualLintMode) {
          VisualLintMode.RUN_IN_BACKGROUND -> modelsForBackgroundRun.add(manager.model)
          VisualLintMode.RUN_ON_PREVIEW_ONLY -> renderResultsForAnalysis[result] = manager.model
          VisualLintMode.DISABLED -> {}
        }
      }
      hasRunAtfOnMainPreview = renderResultsForAnalysis.isNotEmpty()
      VisualLintService.getInstance(project)
        .runVisualLintAnalysis(
          parentDisposable,
          visualLintIssueProvider,
          modelsForBackgroundRun,
          renderResultsForAnalysis,
        )
    }

    if (!hasRunAtfOnMainPreview) {
      scannerControl?.validateAndUpdateLint(renderResults)
    }
  }

  fun run() { // Whenever error queue is active, make sure to resume any paused scanner control.
    scannerControl?.resume() // Look up *current* result; a newer one could be available
    val renderResults = sceneManagers().associateWithNotNull { it.renderResult }

    if (renderResults.isEmpty()) {
      return
    }

    if (project.isDisposed) {
      return
    }

    triggerRenderIssueProvidersUpdate(renderResults)
    triggerVisualLintUpdate(renderResults)
  }
}

/** [MergingUpdateQueue] to update */
class ErrorQueue(private val parentDisposable: Disposable, private val project: Project) :
  Disposable {

  private val coroutineScope = parentDisposable.createCoroutineScope()
  private val updateFlow =
    MutableSharedFlow<ErrorUpdate>(
      replay = 1,
      onBufferOverflow = BufferOverflow.DROP_OLDEST,
      extraBufferCapacity = 0,
    )

  init {
    Disposer.register(parentDisposable, this)

    // Run the update queue. The updates run *at most* once per second.
    // This will ensure that if a bunch of updates happen in a short period of time, the first
    // one will happen immediately but the next will wait 1s as a batching period.
    coroutineScope.launch {
      updateFlow.collect {
        it.run()
        delay(1000)
      }
    }
  }

  private var renderIssueProviders: ImmutableList<IssueProvider> = persistentListOf()

  fun deactivate(issueModel: IssueModel) {
    renderIssueProviders.forEach { renderIssueProvider ->
      issueModel.removeIssueProvider(renderIssueProvider)
    }
    renderIssueProviders = persistentListOf()
  }

  override fun dispose() {
    renderIssueProviders = persistentListOf()
  }

  fun updateErrorDisplay(
    scannerControl: LayoutScannerControl?,
    visualLintIssueProvider: VisualLintIssueProvider,
    surface: EditorDesignSurface,
    issueModel: IssueModel,
    sceneManagers: () -> List<LayoutlibSceneManager>,
  ) {
    val errorUpdate =
      ErrorUpdate(
        scannerControl,
        visualLintIssueProvider,
        surface,
        issueModel,
        parentDisposable,
        sceneManagers,
        project,
        updateRenderIssueProviders = { newRenderIssueProviders ->
          renderIssueProviders.forEach { issueModel.removeIssueProvider(it) }
          renderIssueProviders = newRenderIssueProviders
          newRenderIssueProviders.forEach { issueModel.addIssueProvider(it) }
        },
      )
    updateFlow.tryEmit(errorUpdate)
  }
}
