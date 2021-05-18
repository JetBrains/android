/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.actions

import com.android.build.attribution.BuildAttributionStateReporter.State
import com.android.build.attribution.ui.BuildAttributionUiManager
import com.android.build.attribution.ui.analytics.BuildAttributionUiAnalytics
import com.android.tools.idea.assistant.AssistActionHandler
import com.android.tools.idea.assistant.datamodel.ActionData
import com.android.tools.idea.gradle.project.ProjectStructure
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.android.tools.idea.whatsnew.assistant.WhatsNewMetricsTracker
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.containers.toArray

class BuildAnalyzerShowAction : AssistActionHandler {
  companion object {
    const val ACTION_KEY = "build.analyzer.show"
  }

  override fun getId(): String = ACTION_KEY

  override fun handleAction(actionData: ActionData, project: Project) {
    WhatsNewMetricsTracker.getInstance().clickActionButton(project, ACTION_KEY)
    val buildAttributionUiManager = BuildAttributionUiManager.getInstance(project)
    if (buildAttributionUiManager.stateReporter.currentState() == State.REPORT_DATA_READY) {
      buildAttributionUiManager.openTab(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON)
    }
    else {
      invokeProjectBuild(project)
      buildAttributionUiManager.requestOpenTabWhenDataReady(BuildAttributionUiAnalytics.TabOpenEventSource.WNA_BUTTON)
    }
  }

  private fun invokeProjectBuild(project: Project) {
    // Copied from com.android.tools.idea.gradle.actions.MakeGradleProjectAction
    val modules: List<Module> = ProjectStructure.getInstance(project).leafModules
    GradleBuildInvoker.getInstance(project).assemble(modules.toArray(Module.EMPTY_ARRAY), TestCompileType.ALL)
  }
}