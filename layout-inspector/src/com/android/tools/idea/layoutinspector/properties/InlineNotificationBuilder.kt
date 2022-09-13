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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import javax.swing.JTextArea

class InlineNotificationBuilder(private val model: InspectorPropertiesModel) : InspectorBuilder<InspectorPropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<InspectorPropertyItem>) {
    if (properties.namespaces.size > 1) return // If we have properties other than the "internal" generated for the dimension builder
    val node = model.layoutInspector?.layoutInspectorModel?.selection
    if (node?.isInlined != true) return
    val titleModel = inspector.addExpandableTitle("Parameters", true)
    val text = JTextArea("The selected composable is inlined. " +
                         "Parameters are not available at this time for inline composables in the Layout Inspector.")
    text.wrapStyleWord = true
    text.lineWrap = true
    text.isEditable = false
    text.isFocusable = false
    text.foreground = NamedColorUtil.getInactiveTextColor()
    text.border = JBUI.Borders.empty(4, 8)
    inspector.addComponent(text, titleModel)
  }
}
