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
package com.android.tools.idea.common.property2.impl.model.util

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.InspectorLineModel
import com.android.tools.idea.common.property2.api.InspectorPanel
import com.android.tools.idea.common.property2.api.PropertyEditorModel
import com.android.tools.idea.common.property2.api.TableLineModel
import com.android.tools.idea.common.property2.api.TableUIProvider
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.JComponent

class TestInspectorPanel : InspectorPanel {
  val lines = mutableListOf<TestInspectorLineModel>()

  override fun addTitle(title: String, vararg actions: AnAction): InspectorLineModel {
    val line = TestInspectorLineModel(TestLineType.TITLE)
    line.title = title
    line.actions = actions.asList()
    lines.add(line)
    return line
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = TestInspectorLineModel(TestLineType.PROPERTY)
    editorModel.lineModel = line
    line.editorModel = editorModel
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        parent: InspectorLineModel?): TableLineModel {
    val line = TestTableLineModel(tableModel, searchable)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val line = TestInspectorLineModel(TestLineType.PANEL)
    lines.add(line)
    addAsChild(line, parent)
    return line
  }

  fun refresh() {
    lines.forEach { it.refresh() }
  }

  private fun addAsChild(child: TestInspectorLineModel, parent: InspectorLineModel?) {
    val group = parent as? TestInspectorLineModel ?: return
    group.children.add(child)
    child.parent = group
  }
}
