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
import com.android.tools.profilers.SessionProfilersView
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.StudioIcons

/**
 * A tab in the Profiler window containing a [SessionProfilersView].
 */
class StudioProfilersSessionTab(private val profilers: StudioProfilers,
                                private val window: ToolWindowWrapper,
                                ideProfilerComponents: IdeProfilerComponents,
                                project: Project) : AspectObserver(), StudioProfilersTab {

  override val view: StudioProfilersView

  init {
    profilers.sessionsManager.addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION) { selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { profilingSessionChanged() }

    view = SessionProfilersView(profilers, ideProfilerComponents, this)

    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC,
                                               AndroidProfilerWindowManagerListener(project, profilers, view))
  }

  override fun dispose() {}

  private fun selectedSessionChanged() {
    val metaData = profilers.sessionsManager.selectedSessionMetaData
    // setTitle appends to the ToolWindow's existing name (i.e. "Profiler"), hence we only need to create and set the string for the
    // session's name.
    window.setTitle(metaData.sessionName)
  }

  private fun profilingSessionChanged() {
    val profilingSession = profilers.sessionsManager.profilingSession

    window.setIcon(if (SessionsManager.isSessionAlive(profilingSession)) {
      ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER)
    } else {
      StudioIcons.Shell.ToolWindows.ANDROID_PROFILER
    })
  }
}
