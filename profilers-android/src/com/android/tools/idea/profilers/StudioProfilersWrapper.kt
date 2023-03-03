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
import com.android.tools.idea.model.StudioAndroidModuleInfo
import com.android.tools.idea.transport.TransportService.Companion.channelName
import com.android.tools.profiler.proto.Common
import com.android.tools.profilers.IdeProfilerComponents
import com.android.tools.profilers.ProfilerAspect
import com.android.tools.profilers.ProfilerClient
import com.android.tools.profilers.StudioProfilers
import com.android.tools.profilers.StudioProfilersView
import com.android.tools.profilers.sessions.SessionAspect
import com.android.tools.profilers.sessions.SessionsManager
import com.intellij.execution.runners.ExecutionUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import icons.StudioIcons
import java.util.function.Supplier

class StudioProfilersWrapper constructor(private val project: Project,
                                         private val window: ToolWindowWrapper,
                                         ideProfilerServices: IntellijProfilerServices) : AspectObserver(), Disposable {
  val profilers: StudioProfilers
  val profilersView: StudioProfilersView

  init {
    val client = ProfilerClient(channelName)
    profilers = StudioProfilers(client, ideProfilerServices)
    val navigator = ideProfilerServices.codeNavigator
    // CPU ABI architecture, when needed by the code navigator, should be retrieved from StudioProfiler selected session.
    navigator.cpuArchSource = Supplier { profilers.sessionsManager.selectedSessionMetaData.processAbi }

    profilers.addDependency(this).onChange(ProfilerAspect.STAGE) { stageChanged() }
    profilers.sessionsManager.addDependency(this)
      .onChange(SessionAspect.SELECTED_SESSION) { selectedSessionChanged() }
      .onChange(SessionAspect.PROFILING_SESSION) { profilingSessionChanged() }

    // Attempt to find the last-run process and start profiling it. This covers the case where the user presses "Run" (without profiling),
    // but then opens the profiling window manually.
    val processInfo = project.getUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO)
    if (processInfo != null) {
      profilers.setPreferredProcess(processInfo.deviceName,
                                    processInfo.processName) { p: Common.Process? -> processInfo.processFilter.invoke(p!!) }
      project.putUserData(AndroidProfilerToolWindow.LAST_RUN_APP_INFO, null)
    }
    else {
      StartupManager.getInstance(project).runWhenProjectIsInitialized { profilers.preferredProcessName = getPreferredProcessName(project) }
    }

    val profilerComponents: IdeProfilerComponents = IntellijProfilerComponents(project, profilers.ideServices.featureTracker)
    profilersView = StudioProfilersView(profilers, profilerComponents)
    Disposer.register(this, profilersView)

    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC,
                                               AndroidProfilerWindowManagerListener(project, profilers, profilersView))
  }

  override fun dispose() {
    profilers.stop()
  }

  private fun stageChanged() {
    if (profilers.isStopped) {
      window.removeContent()
    }
  }

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

  companion object {
    private fun getPreferredProcessName(project: Project): String? {
      for (module in ModuleManager.getInstance(project).modules) {
        val moduleName = getModuleName(module)
        if (moduleName != null) {
          return moduleName
        }
      }
      return null
    }

    private fun getModuleName(module: Module): String? {
      val moduleInfo = StudioAndroidModuleInfo.getInstance(module)
      if (moduleInfo != null) {
        val pkg = moduleInfo.packageName
        if (pkg != null) {
          return pkg
        }
      }
      return null
    }
  }
}
