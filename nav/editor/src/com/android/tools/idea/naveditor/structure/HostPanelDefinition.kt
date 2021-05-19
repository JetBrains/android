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
package com.android.tools.idea.naveditor.structure

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.workbench.AutoHide
import com.android.tools.adtui.workbench.Side
import com.android.tools.adtui.workbench.Split
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowDefinition
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.icons.AllIcons
import java.awt.BorderLayout
import javax.swing.JComponent

class HostPanelDefinition : ToolWindowDefinition<DesignSurface>(
  "Hosts", AllIcons.Toolwindows.ToolWindowStructure, "HOSTS", Side.LEFT, Split.TOP, AutoHide.DOCKED,
  { HostPanelContainer() }
)

private class HostPanelContainer : ToolContent<DesignSurface> {
  val panel = AdtSecondaryPanel(BorderLayout())
  var hostPanel: JComponent? = null

  override fun getComponent(): JComponent {
    return hostPanel ?: panel
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    hostPanel = toolContext?.let { HostPanel(it) }
  }

  override fun dispose() {
  }
}