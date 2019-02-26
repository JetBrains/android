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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.property.panel.api.PropertiesPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class LayoutInspectorProperties(parentDisposable: Disposable) : ToolContent<LayoutInspector> {
  private val componentModel = InspectorPropertiesModel()
  private val componentView = InspectorPropertiesView(componentModel)
  private val properties = PropertiesPanel<InspectorPropertyItem>(this)

  init {
    properties.addView(componentView)
    Disposer.register(parentDisposable, this)
  }

  override fun setToolContext(toolContext: LayoutInspector?) {
    componentModel.layoutInspector = toolContext
  }

  override fun getComponent() = properties.component

  override fun dispose() {
  }
}
