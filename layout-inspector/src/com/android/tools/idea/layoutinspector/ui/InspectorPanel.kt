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
package com.android.tools.idea.layoutinspector.ui

import com.android.tools.adtui.workbench.WorkBench
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.model.InspectorModel
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.LayoutInspectorPropertiesPanelDefinition
import com.android.tools.idea.layoutinspector.tree.LayoutInspectorTreePanelDefinition
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JPanel

/**
 * Main (center) panel of the layout inspector
 */
class InspectorPanel(val project: Project, toolName: String, disposableParent: Disposable) : JPanel(BorderLayout()) {
  val deviceViewPanel: DeviceViewPanel

  init {
    val workbench = WorkBench<LayoutInspector>(project, toolName, null, disposableParent)
    val viewSettings = DeviceViewSettings()
    val layoutInspector = LayoutInspector(InspectorModel(project))
    deviceViewPanel = DeviceViewPanel(layoutInspector, viewSettings)
    workbench.init(deviceViewPanel, layoutInspector, listOf(
      LayoutInspectorTreePanelDefinition(), LayoutInspectorPropertiesPanelDefinition()))
    add(workbench, BorderLayout.CENTER)
  }
}

