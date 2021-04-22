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
package com.android.tools.idea.naveditor.property

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.property.NlPropertiesModel
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.property.panel.api.PropertiesPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import org.jetbrains.android.facet.AndroidFacet
import java.awt.BorderLayout
import javax.swing.JPanel

class NavPropertiesPanelToolContent(facet: AndroidFacet, parentDisposable: Disposable) : ToolContent<DesignSurface> {
  private val panel = JPanel(BorderLayout())
  private val componentModel = NlPropertiesModel(this, facet)
  private val componentView = NavPropertiesView(componentModel)
  private val properties = PropertiesPanel<NlPropertyItem>(componentModel)

  init {
    Disposer.register(parentDisposable, this)
    panel.add(properties.component, BorderLayout.CENTER)
    properties.addView(componentView)
  }

  override fun setToolContext(toolContext: DesignSurface?) {
    componentModel.surface = toolContext
  }

  override fun getComponent() = panel

  override fun dispose() {
  }
}