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
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.StudioIcons

class StudioProfilersWrapper(private val profilers: StudioProfilers,
                             private val window: ToolWindowWrapper,
                             project: Project) : AspectObserver(), Disposable {
  val profilersView: StudioProfilersView

  init {
    profilers.sessionsManager.addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION) { selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { profilingSessionChanged() }

    val profilerComponents: IdeProfilerComponents = IntellijProfilerComponents(project, profilers.ideServices.featureTracker)
    profilersView = SessionProfilersView(profilers, profilerComponents, this)

    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC,
                                               AndroidProfilerWindowManagerListener(project, profilers, profilersView))
  }

  override fun dispose() {}

  private fun selectedSessionChanged() {
    val metaData = profilers.sessionsManager.selectedSessionMetaData
    // setTitle appends to the ToolWindow's existing name (i.e. "Profiler"), hence we only need to create and set the string for the
    // session's name.
    window.title = metaData.sessionName
  }

  private fun profilingSessionChanged() {
    val profilingSession = profilers.sessionsManager.profilingSession
    if (SessionsManager.isSessionAlive(profilingSession)) {
      window.icon = ExecutionUtil.getLiveIndicator(StudioIcons.Shell.ToolWindows.ANDROID_PROFILER)
    }
    else {
      window.icon = StudioIcons.Shell.ToolWindows.ANDROID_PROFILER
    }
  }
}
