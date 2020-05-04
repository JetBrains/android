/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.appinspection.ide.ui

import com.android.tools.idea.appinspection.ide.AppInspectionHostService
import com.android.tools.idea.appinspection.ide.analytics.AppInspectionAnalyticsTrackerService
import com.android.tools.idea.appinspection.inspector.ide.AppInspectionIdeServices
import com.android.tools.idea.model.AndroidModuleInfo
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import javax.swing.JComponent

class AppInspectionToolWindow(toolWindow: ToolWindow, private val project: Project) : Disposable {
  /**
   * This dictates the names of the preferred processes. They are drawn from the android applicationIds of the modules in this [project].
   */
  private fun getPreferredProcesses(): List<String> = ModuleManager.getInstance(project).modules
    .mapNotNull { AndroidModuleInfo.getInstance(it)?.`package` }
    .toList()

  private val ideServices = object : AppInspectionIdeServices {
    override fun showToolWindow(callback: () -> Unit) = toolWindow.show(Runnable { callback() })
  }

  private val appInspectionView = AppInspectionView(
    project,
    AppInspectionHostService.instance.discoveryHost,
    ideServices,
    ::getPreferredProcesses,
    DefaultAppInspectionNotificationFactory(NotificationGroup.toolWindowGroup(APP_INSPECTION_ID, APP_INSPECTION_ID))
  )
  val component: JComponent = appInspectionView.component

  init {
    Disposer.register(this, appInspectionView)
    project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      private var wasVisible = false
      override fun stateChanged() {
        val inspectionToolWindow = ToolWindowManager.getInstance(project).getToolWindow(APP_INSPECTION_ID) ?: return
        val visibilityChanged = inspectionToolWindow.isVisible != wasVisible
        wasVisible = inspectionToolWindow.isVisible
        if (visibilityChanged) {
          if (inspectionToolWindow.isVisible) {
            AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowOpened()
          } else {
            AppInspectionAnalyticsTrackerService.getInstance(project).trackToolWindowHidden()
          }
        }
      }
    })
  }

  override fun dispose() {
    // Although we do nothing here, because this class is disposable, other components can register
    // against it
  }
}