/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.profilers

import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.TaskProfilersView
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.StudioIcons

/**
 * A tab in the Profiler window containing a [TaskProfilersView].
 */
class StudioProfilersTaskTab(private val profilers: StudioProfilers,
                             private val window: ToolWindowWrapper,
                             ideProfilerComponents: IdeProfilerComponents,
                             project: Project) : AspectObserver(), StudioProfilersTab {
  override val view: StudioProfilersView

  init {
    // In the Task-Based UX, determining if there is an ongoing task/recording is done via inspection of the selected session.
    profilers.sessionsManager.addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION) { selectedSessionChanged() }

    view = TaskProfilersView(profilers, ideProfilerComponents, this)

    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC,
                                               AndroidProfilerWindowManagerListener(project, profilers, view))
  }

  private fun selectedSessionChanged() {
    window.setIcon(if (profilers.sessionsManager.isSessionAlive) {
      ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER)
    } else {
      StudioIcons.Shell.ToolWindows.ANDROID_PROFILER
    })
  }

  override fun dispose() {
    profilers.sessionsManager.removeDependencies(this)
  }
}