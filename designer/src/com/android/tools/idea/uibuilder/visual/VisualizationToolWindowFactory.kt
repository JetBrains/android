/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory

/**
 * [ToolWindowFactory] for the Layout Validation Tool. The tool is registered in designer.xml and the initialization is controlled by IJ's
 * framework.
 */
class VisualizationToolWindowFactory : ToolWindowFactory {

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    // Initializes the VisualizationManager when tool window needs the content.
    // This guarantees the Visualization Manager does not init during indexing and only consume the resource when it needs.
    // This also gets rid of using postStartupActivity to init the VisualizationManager.
    VisualizationManager.getInstance(project)
    // TODO(b/180927397): Move content initialization from VisualizationManager to here
  }
}
