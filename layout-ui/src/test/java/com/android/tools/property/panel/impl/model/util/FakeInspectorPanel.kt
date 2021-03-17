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
package com.android.tools.property.panel.impl.model.util

import com.android.tools.property.ptable2.PTableModel
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

class FakeInspectorPanel : InspectorPanel {
  val lines = mutableListOf<FakeInspectorLineModel>()

  override fun addTitle(title: String, actions: List<AnAction>): InspectorLineModel {
    val line = FakeInspectorLineModel(FakeLineType.TITLE)
    line.title = title
    line.actions = actions
    lines.add(line)
    return line
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = FakeInspectorLineModel(FakeLineType.PROPERTY)
    editorModel.lineModel = line
    line.editorModel = editorModel
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        actions: List<AnAction>,
                        parent: InspectorLineModel?): TableLineModel {
    val line = FakeTableLineModel(tableModel, tableUI, searchable)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = FakeComponentLineModel(component)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  fun refresh() {
    lines.forEach { it.refresh() }
  }

  private fun addAsChild(child: FakeInspectorLineModel, parent: InspectorLineModel?) {
    val group = parent as? FakeInspectorLineModel ?: return
    group.children.add(child)
    child.parent = group
  }
}
