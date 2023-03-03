// Copyright (C) 2017 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.android.tools.idea.gradle.structure.configurables.suggestions

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.gradle.project.GradleVersionCatalogDetector
import com.android.tools.idea.gradle.structure.configurables.BasePerspectiveConfigurable
import com.android.tools.idea.gradle.structure.configurables.PsContext
import com.android.tools.idea.gradle.structure.configurables.android.dependencies.PsAllModulesFakeModule
import com.android.tools.idea.gradle.structure.configurables.android.modules.AbstractModuleConfigurable
import com.android.tools.idea.gradle.structure.configurables.ui.AbstractMainPanel
import com.android.tools.idea.gradle.structure.model.PsIssue
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModulePath
import com.android.tools.idea.structure.dialog.VersionCatalogWarningHeader
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.ui.navigation.Place
import java.awt.BorderLayout

open class ModuleSuggestionsConfigurable(
  context: PsContext,
  perspectiveConfigurable: BasePerspectiveConfigurable,
  module: PsModule
) : AbstractModuleConfigurable<PsModule, AbstractMainPanel>(context, perspectiveConfigurable, module) {
  override fun getId() = "android.psd.suggestions." + displayName

  override fun createPanel(): AbstractMainPanel = object : AbstractMainPanel(context) {
    private val panel = createInnerPanel().also {
      if (GradleVersionCatalogDetector.getInstance(context.project.ideProject).isVersionCatalogProject) {
        if (StudioFlags.GRADLE_VERSION_CATALOG_DISPLAY_BANNERS.get()) {
          add(VersionCatalogWarningHeader(), BorderLayout.NORTH)
        }
      }
      add(it.panel, BorderLayout.CENTER)
    }

    override fun navigateTo(place: Place?, requestFocus: Boolean): ActionCallback = panel.navigateTo(place, requestFocus)
    override fun queryPlace(place: Place) = panel.queryPlace(place)
    override fun restoreUiState() = Unit
    override fun dispose() {
      Disposer.dispose(panel)
    }
  }

  private fun createInnerPanel(): SuggestionsForm {
    val psModulePath = when (module) {
      is PsAllModulesFakeModule -> null
      else -> PsModulePath(module)
    }
    val issueRenderer = SuggestionsViewIssueRenderer(context)
    return SuggestionsForm(context, issueRenderer).apply {
      renderIssues(getIssues(context, psModulePath), psModulePath)

      context.analyzerDaemon.onIssuesChange(this) {
        if (!uiDisposed) {
          renderIssues(getIssues(context, psModulePath), psModulePath)
        }
      }
      context.analyzerDaemon.onRunningChange(this) @UiThread {
        updateLoading()
      }
    }
  }

  companion object {
    const val SUGGESTIONS_VIEW = "SuggestionsView"
  }
}

internal fun getIssues(psContext: PsContext, psModulePath: PsModulePath?): List<PsIssue> =
  psContext.analyzerDaemon.issues.findIssues(psModulePath, null)

