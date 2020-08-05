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
package com.android.tools.idea.ui

import com.intellij.facet.ui.FacetDependentToolWindow
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.RegisterToolWindowTask
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager

private const val PREFERENCE_KEY = "live.layout.inspector.enabled"

// Should match LayoutInspectorFileType.getName()
private const val LAYOUT_INSPECTOR_FILE_TYPE_NAME = "Layout Inspector"

// Should match LayoutInspectorToolWindowFactory.LAYOUT_INSPECTOR_TOOL_WINDOW_ID
private const val LAYOUT_INSPECTOR_TOOL_WINDOW_ID = "Layout Inspector"

var enableLiveLayoutInspector
  get() = PropertiesComponent.getInstance().getBoolean(PREFERENCE_KEY, true)
  set(value) {
    if (value != enableLiveLayoutInspector) {
      PropertiesComponent.getInstance().setValue(PREFERENCE_KEY, value, true)
      if (value) {
        for (windowEp in FacetDependentToolWindow.EXTENSION_POINT_NAME.extensionList) {
          if (windowEp.id == LAYOUT_INSPECTOR_TOOL_WINDOW_ID) {
            for (project in ProjectManager.getInstance().openProjects) {
              val windowManager = ToolWindowManager.getInstance(project)
              var window: ToolWindow? = windowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
              if (window == null) {
                windowManager.registerToolWindow(
                  RegisterToolWindowTask(LAYOUT_INSPECTOR_TOOL_WINDOW_ID,
                                         contentFactory = windowEp.getToolWindowFactory(windowEp.pluginDescriptor)))
              }
              window = windowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
              window?.setAvailable(true, null)
              window?.show(null)
              window?.activate(null)
            }
          }
        }
        for (project in ProjectManager.getInstance().openProjects) {
          val editorManager = FileEditorManager.getInstance(project)
          for (vf in editorManager.openFiles) {
            if (vf.fileType.name == LAYOUT_INSPECTOR_FILE_TYPE_NAME) {
              editorManager.closeFile(vf)
            }
          }
        }
      }
      else {
        for (project in ProjectManager.getInstance().openProjects) {
          val windowManager = ToolWindowManager.getInstance(project)
          val window = windowManager.getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
          window?.setAvailable(false, null)
          window?.hide(null)
        }
      }
    }
  }