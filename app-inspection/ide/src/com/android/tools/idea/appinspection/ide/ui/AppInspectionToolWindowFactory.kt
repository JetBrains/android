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

import com.android.tools.idea.flags.StudioFlags
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import icons.StudioIcons

// This must match the toolwindow id in app-inspection.xml
// TODO(b/152556591): Rename back to "App Inspection"
internal const val APP_INSPECTION_ID = "Database Inspector"

class AppInspectionToolWindowFactory : DumbAware, ToolWindowFactory {

  override fun isApplicable(project: Project) = StudioFlags.ENABLE_APP_INSPECTION_TOOL_WINDOW.get()

  override fun createToolWindowContent(project: Project,
                                       toolWindow: ToolWindow) {
    val appInspectionToolWindow = AppInspectionToolWindow(toolWindow, project)
    val contentFactory = ContentFactory.SERVICE.getInstance()
    val content = contentFactory.createContent(appInspectionToolWindow.component, "", false)
    toolWindow.contentManager.addContent(content)
    // TODO(b/152556591): Update to app inspection icon and move database icon down to the tab
    toolWindow.setIcon(StudioIcons.Shell.ToolWindows.DATABASE_INSPECTOR)
    Disposer.register(project, appInspectionToolWindow)
    toolWindow.show(null)
    toolWindow.isShowStripeButton = false
    toolWindow.stripeTitle = APP_INSPECTION_ID
  }

  override fun init(toolWindow: ToolWindow) {
    toolWindow.setToHideOnEmptyContent(true)
  }
}