/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.property2.impl.ui

import com.android.tools.adtui.ptable2.PTableModel
import com.android.tools.idea.common.property2.api.*
import com.android.tools.idea.common.property2.impl.model.CollapsibleLabelModel
import com.android.tools.idea.common.property2.impl.model.GenericInspectorLineModel
import com.android.tools.idea.common.property2.impl.model.InspectorPanelModel
import com.android.tools.idea.common.property2.impl.model.TableLineModel
import com.intellij.openapi.Disposable
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

private const val TITLE_SEPARATOR_HEIGHT = 4
private const val VERTICAL_SCROLLING_UNIT_INCREMENT = 3
private const val VERTICAL_SCROLLING_BLOCK_INCREMENT = 25

class PropertiesPage(parentDisposable: Disposable) : InspectorPanel {
  private val inspectorModel = InspectorPanelModel()
  private val inspector = InspectorPanelImpl(inspectorModel, parentDisposable)
  private val gotoNextLine: (InspectorLineModel) -> Unit = { inspectorModel.moveToNextLineEditor(it) }
  private val boldFont = UIUtil.getLabelFont().deriveFont(Font.BOLD)
  private var lastAddedNonTitleLine: InspectorLineModel? = null

  val component = createScrollPane(inspector)

  var filter
    get() = inspectorModel.filter
    set(value) { inspectorModel.filter = value }

  fun enterInFilter():Boolean {
    return inspectorModel.enterInFilter()
  }

  fun clear() {
    inspectorModel.clear()
    inspector.removeAll()
    lastAddedNonTitleLine = null
  }

  fun propertyValuesChanged() {
    inspectorModel.propertyValuesChanged()
  }

  fun repaint() {
    inspector.revalidate()
    inspector.repaint()
  }

  private fun createScrollPane(component: JComponent): JComponent {
    val scrollPane = ScrollPaneFactory.createScrollPane(
      component,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = BorderFactory.createEmptyBorder()
    scrollPane.verticalScrollBar.unitIncrement = VERTICAL_SCROLLING_UNIT_INCREMENT
    scrollPane.verticalScrollBar.blockIncrement = VERTICAL_SCROLLING_BLOCK_INCREMENT
    return scrollPane
  }

  override fun addTitle(title: String): InspectorLineModel {
    addSeparatorBeforeTitle()
    val model = CollapsibleLabelModel(title)
    val label = CollapsibleLabel(model)
    inspectorModel.add(model)
    inspector.addLineElement(label)
    label.font = boldFont
    label.isOpaque = true
    label.border = JBUI.Borders.merge(
      JBUI.Borders.empty(TITLE_SEPARATOR_HEIGHT, LEFT_HORIZONTAL_CONTENT_BORDER_SIZE, TITLE_SEPARATOR_HEIGHT, 0),
      JBUI.Borders.customLine(JBColor.border(), 0, 0, 1, 0), true)
    label.background = UIUtil.getPanelBackground()
    model.gotoNextLine = gotoNextLine
    model.separatorAfterTitle = addSeparator(bottomDivider = false)
    return model
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val model = CollapsibleLabelModel(editorModel.property.name, editorModel)
    val label = CollapsibleLabel(model)
    label.border = JBUI.Borders.emptyLeft(LEFT_HORIZONTAL_CONTENT_BORDER_SIZE)
    editorModel.lineModel = model
    inspectorModel.add(model)
    inspector.addLineElement(label, editor)
    model.gotoNextLine = gotoNextLine
    lastAddedNonTitleLine = model
    addAsChild(model, parent)
    return model
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        parent: InspectorLineModel?): InspectorLineModel {
    val model = TableLineModel(tableModel, searchable)
    val editor = TableEditor(model, tableUI.tableCellRendererProvider, tableUI.tableCellEditorProvider)
    inspectorModel.add(model)
    inspector.addLineElement(editor.component)
    model.gotoNextLine = gotoNextLine
    lastAddedNonTitleLine = model
    addAsChild(model, parent)
    return model
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    val model = GenericInspectorLineModel()
    val wrapper = GenericLinePanel(component, model)
    inspectorModel.add(model)
    inspector.addLineElement(wrapper)
    model.gotoNextLine = gotoNextLine
    lastAddedNonTitleLine = model
    addAsChild(model, parent)
    return model
  }

  private fun addAsChild(child: GenericInspectorLineModel, parent: InspectorLineModel?) {
    if (parent == null) {
      return
    }
    val label = parent as? CollapsibleLabelModel ?: throw IllegalArgumentException()
    label.addChild(child)
  }

  private fun addSeparatorBeforeTitle() {
    val lastLine = lastAddedNonTitleLine ?: return
    addSeparator(bottomDivider = true, parent = lastLine.parent)
  }

  private fun addSeparator(bottomDivider: Boolean, parent: InspectorLineModel? = null): GenericInspectorLineModel {
    val component = JPanel()
    val bottom = if (bottomDivider) 1 else 0
    component.preferredSize = JBDimension(0, TITLE_SEPARATOR_HEIGHT)
    component.background = inspector.background
    component.border = JBUI.Borders.customLine(JBColor.border(), 0, 0, bottom, 0)
    val line = addComponent(component, parent)
    lastAddedNonTitleLine = null
    return line as GenericInspectorLineModel
  }
}
