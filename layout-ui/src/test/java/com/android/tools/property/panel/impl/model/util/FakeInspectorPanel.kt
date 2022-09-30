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

import com.android.testutils.MockitoKt.mock
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.ptable.PTableModel
import com.google.common.truth.Truth
import com.intellij.openapi.actionSystem.AnAction
import javax.swing.Icon
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

  override fun addSubTitle(title: String, initiallyExpanded: Boolean, parent: InspectorLineModel?): InspectorLineModel {
    val line = FakeInspectorLineModel(FakeLineType.SUBTITLE)
    line.title = title
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

  fun checkTitle(line: Int, title: String): FakeInspectorLineModel {
    Truth.assertThat(line).isLessThan(lines.size)
    val model = lines[line]
    Truth.assertThat(model.type).isEqualTo(FakeLineType.TITLE)
    Truth.assertThat(model.title).isEqualTo(title)
    return model
  }

  fun checkTitle(line: Int, title: String, expandable: Boolean): FakeInspectorLineModel {
    Truth.assertThat(line).isLessThan(lines.size)
    val model = lines[line]
    Truth.assertThat(model.type).isEqualTo(FakeLineType.TITLE)
    Truth.assertThat(model.title).isEqualTo(title)
    Truth.assertThat(model.expandable).isEqualTo(expandable)
    return model
  }

  fun checkEditor(line: Int, namespace: String, name: String) {
    Truth.assertThat(line).isLessThan(lines.size)
    val model = lines[line]
    Truth.assertThat(model.type).isEqualTo(FakeLineType.PROPERTY)
    Truth.assertThat(model.editorModel?.property?.name).isEqualTo(name)
    Truth.assertThat(model.editorModel?.property?.namespace).isEqualTo(namespace)
  }

  fun checkTable(line: Int): FakeTableLineModel {
    Truth.assertThat(line).isLessThan(lines.size)
    val model = lines[line]
    Truth.assertThat(model.type).isEqualTo(FakeLineType.TABLE)
    return model as FakeTableLineModel
  }

  fun performAction(line: Int, action: Int, icon: Icon) {
    Truth.assertThat(line).isLessThan(lines.size)
    val model = lines[line]
    Truth.assertThat(action).isLessThan(model.actions.size)
    val anAction = model.actions[action]
    Truth.assertThat(anAction.templatePresentation.icon).isEqualTo(icon)

    anAction.actionPerformed(mock())
  }
}
