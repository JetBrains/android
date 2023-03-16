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
package com.android.tools.idea.uibuilder.visual

import com.android.tools.idea.common.error.IssueModel
import com.android.tools.idea.common.surface.getDesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.lint.CommonLintUserDataHandler
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RenderListener
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintBaseConfigIssues
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.gradleTooling.get

/**
 * Helper class to handle the process of visual lint in [VisualizationForm].
 */
class VisualizationFormVisualLintHandler(
  parentDisposable: Disposable,
  private val project: Project,
  private val issueModel: IssueModel) {

  private val myBaseConfigIssues = VisualLintBaseConfigIssues()
  val lintIssueProvider = VisualLintIssueProvider(parentDisposable)

  init {
    issueModel.addIssueProvider(lintIssueProvider)
  }

  fun clearIssueProvider() {
    lintIssueProvider.clear()
    issueModel.updateErrorsList()
  }

  fun clearIssueProviderAndBaseConfigurationIssue() {
    lintIssueProvider.clear()
    issueModel.updateErrorsList()
    myBaseConfigIssues.clear()
  }

  fun setupForLayoutlibSceneManager(manager: LayoutlibSceneManager, isCancelled: () -> Boolean) {
    val renderListener: RenderListener = object : RenderListener {
      override fun onRenderCompleted() {
        if (isCancelled() || manager.model.isDisposed) return
        val model = manager.model
        val result = manager.renderResult
        if (result != null) {
          ApplicationManager.getApplication().executeOnPooledThread {
            VisualLintService.getInstance(project).analyzeAfterModelUpdate(
              lintIssueProvider, result, model, myBaseConfigIssues)
            if (StudioFlags.NELE_SHOW_VISUAL_LINT_ISSUE_IN_COMMON_PROBLEMS_PANEL.get()) {
              CommonLintUserDataHandler.updateVisualLintIssues(model.file, lintIssueProvider)
              issueModel.updateErrorsList()
            }
          }
        }

        // Remove self. This will not cause ConcurrentModificationException.
        // Callback iteration creates copy of a list. (see {@link ListenerCollection.kt#foreach})
        manager.removeRenderListener(this)
      }

      override fun onRenderFailed(e: Throwable) {
        manager.removeRenderListener(this)
      }
    }
    manager.addRenderListener(renderListener)
  }

  fun onActivate() {
    // Clean up the visual lint issue from Layout Editor
    VisualLintService.getInstance(project).removeAllIssueProviders()

    issueModel.addIssueProvider(lintIssueProvider)
    issueModel.updateErrorsList()
  }

  fun onDeactivate() {
    issueModel.removeIssueProvider(lintIssueProvider)
    lintIssueProvider.clear()
    issueModel.updateErrorsList()

    // Trigger Layout Editor Visual Lint
    (FileEditorManager.getInstance(project).selectedEditor?.getDesignSurface() as? NlDesignSurface)?.updateErrorDisplay()
  }
}