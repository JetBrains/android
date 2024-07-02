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
import com.android.tools.idea.common.surface.LayoutScannerControl
import com.android.tools.idea.gradle.project.build.GradleBuildState
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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** [MergingUpdateQueue] to update */
class ErrorQueue(private val parentDisposable: Disposable, private val project: Project) {
  private val queue: MergingUpdateQueue by
    lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
      MergingUpdateQueue(
        "android.error.computation",
        200,
        true,
        null,
        parentDisposable,
        null,
        Alarm.ThreadToUse.POOLED_THREAD,
      )
    }

  fun updateErrorDisplay(
    scannerControl: LayoutScannerControl?,
    visualLintIssueProvider: VisualLintIssueProvider,
    surface: EditorDesignSurface,
    issueModel: IssueModel,
    sceneManagers: () -> List<LayoutlibSceneManager>,
  ) {

    assert(
      ApplicationManager.getApplication().isDispatchThread ||
        !ApplicationManager.getApplication().isReadAccessAllowed
    ) {
      "Do not hold read lock when calling updateErrorDisplay!"
    }

    queue.cancelAllUpdates()
    val errorUpdate =
      ErrorUpdate(
        scannerControl,
        visualLintIssueProvider,
        surface,
        issueModel,
        parentDisposable,
        sceneManagers,
        project,
      )
    queue.queue(errorUpdate)
  }

  private class ErrorUpdate(
    private val scannerControl: LayoutScannerControl?,
    private val visualLintIssueProvider: VisualLintIssueProvider,
    private val surface: EditorDesignSurface,
    private val issueModel: IssueModel,
    private val parentDisposable: Disposable,
    private val sceneManagers: () -> List<LayoutlibSceneManager>,
    private val project: Project,
  ) : Update("errors") {

    private var renderIssueProviders: ImmutableList<IssueProvider> = persistentListOf()

    override fun run() {
      // Whenever error queue is active, make sure to resume any paused scanner control.
      scannerControl?.resume()
      // Look up *current* result; a newer one could be available
      val renderResults = sceneManagers().associateWithNotNull { it.renderResult }

      if (renderResults.isEmpty()) {
        return
      }

      if (project.isDisposed) {
        return
      }

      // createErrorModel needs to run in Smart mode to resolve the classes correctly
      DumbService.getInstance(project).runReadActionInSmartMode {
        var newRenderIssueProviders: ImmutableList<RenderIssueProvider>? = null
        if (GradleBuildState.getInstance(project).isBuildInProgress) {
          for ((manager, renderResult) in renderResults) {
            if (renderResult.logger.hasErrors()) {
              // We are still building, display the message to the user.
              newRenderIssueProviders =
                persistentListOf(
                  RenderIssueProvider(manager.model, RenderErrorModel.STILL_BUILDING_ERROR_MODEL)
                )
              break
            }
          }
        }

        if (newRenderIssueProviders == null) {
          newRenderIssueProviders =
            renderResults
              .map {
                val errorModel = RenderErrorModelFactory.createErrorModel(surface, it.value)
                RenderIssueProvider(it.key.model, errorModel)
              }
              .toImmutableList()
        }
        this.renderIssueProviders.forEach { issueModel.removeIssueProvider(it) }
        this.renderIssueProviders = newRenderIssueProviders
        newRenderIssueProviders.forEach { issueModel.addIssueProvider(it) }
      }

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

    override fun canEat(update: Update) = true
  }
}
