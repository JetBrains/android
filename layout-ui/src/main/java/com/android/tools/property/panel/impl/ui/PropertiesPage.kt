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
package com.android.tools.property.panel.impl.ui

import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesViewTab
import com.android.tools.property.panel.api.PropertyEditorModel
import com.android.tools.property.panel.api.TableLineModel
import com.android.tools.property.panel.api.TableUIProvider
import com.android.tools.property.panel.impl.model.CollapsibleLabelModel
import com.android.tools.property.panel.impl.model.GenericInspectorLineModel
import com.android.tools.property.panel.impl.model.InspectorPanelModel
import com.android.tools.property.panel.impl.model.SeparatorLineModel
import com.android.tools.property.panel.impl.model.TableLineModelImpl
import com.android.tools.property.panel.impl.model.TitleLineModel
import com.android.tools.property.ptable.ColumnFraction
import com.android.tools.property.ptable.PTableModel
import com.google.common.annotations.VisibleForTesting
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.JBColor
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollBar
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Font
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

private const val TITLE_SEPARATOR_HEIGHT = 4
private const val SUBTITLE_SEPARATOR_HEIGHT = 2

@VisibleForTesting
const val LEFT_FRACTION_KEY = "android.property.left.fraction"

/**
 * Provides a page for a tab defined by a [PropertiesViewTab].
 */
class PropertiesPage(parentDisposable: Disposable) : InspectorPanel {
  @VisibleForTesting
  val inspectorModel = InspectorPanelModel()
  private var lastAddedLine: InspectorLineModel? = null
  private var lastTitleLine: CollapsibleLabelModel? = null
  @VisibleForTesting
  val nameColumnFraction = ColumnFraction(PropertiesComponent.getInstance().getFloat(LEFT_FRACTION_KEY, 0.4f), resizeSupported = true)
  private val inspector = InspectorPanelImpl(inspectorModel, nameColumnFraction, parentDisposable)

  init {
    nameColumnFraction.listeners.add(ValueChangedListener {
      inspector.invalidate()
      inspector.validate()
      inspector.repaint()
      PropertiesComponent.getInstance().setValue(LEFT_FRACTION_KEY, nameColumnFraction.value, 0.4f)
    })
  }

  val component = createScrollPane(inspector)

  var filter
    get() = inspectorModel.filter
    set(value) { inspectorModel.filter = value }

  val isEmpty
    get() = inspectorModel.lines.isEmpty()

  fun enterInFilter():Boolean {
    return inspectorModel.enterInFilter()
  }

  fun clear() {
    inspectorModel.clear()
    inspector.removeAll()
    lastAddedLine = null
    lastTitleLine = null
  }

  fun propertyValuesChanged() {
    inspectorModel.propertyValuesChanged()
  }

  fun repaint() {
    inspector.revalidate()
    inspector.repaint()
  }

  fun addSeparatorBeforeTabs() {
    addSeparatorBeforeTitle()
  }

  private fun createScrollPane(component: JComponent): JComponent {
    val scrollPane = ScrollPaneFactory.createScrollPane(
      component,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.verticalScrollBar = object: JBScrollBar() {
      override fun setOpaque(isOpaque: Boolean) {
        // This disables the "Show scroll bars when scrolling" option on Mac.
        // The problem is that the icons on the right of the properties panel
        // would be covered by the scroll bar when it was visible.
        super.setOpaque(isOpaque || SystemInfo.isMac)
      }
    }
    scrollPane.border = JBUI.Borders.empty()
    scrollPane.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent?) {
        // unitIncrement affects the scroll wheel speed
        // Each platform seem to behave differently to this value.
        // - Windows seem to scroll faster
        // - Unix about half speed of Windows
        // - Mac doesn't seem to use this value
        scrollPane.verticalScrollBar.unitIncrement = JBUI.scale(16) * (if (SystemInfo.isWindows) 1 else 2)

        // blockIncrement affects the page down speed, when clicking above/under the scroll thumb
        scrollPane.verticalScrollBar.blockIncrement = scrollPane.height
      }
    })
    return scrollPane
  }

  override fun addTitle(title: String, actions: List<AnAction>): InspectorLineModel {
    addSeparatorBeforeTitle()
    val model = TitleLineModel(title)
    val label = CollapsibleLabelPanel(model, UIUtil.FontSize.NORMAL, Font.BOLD, actions, nameColumnFraction)
    label.isOpaque = true
    label.innerBorder = JBUI.Borders.empty(TITLE_SEPARATOR_HEIGHT, 0)
    label.border = JBUI.Borders.merge(JBUI.Borders.empty(0, LEFT_HORIZONTAL_CONTENT_BORDER_SIZE, 0, 0),
                                      SideBorder(JBColor.border(), SideBorder.BOTTOM), true)
    addLine(model, null)
    inspector.addLineElement(label)
    label.background = UIUtil.getPanelBackground()
    lastTitleLine = model
    return model
  }

  override fun addSubTitle(title: String, initiallyExpanded: Boolean, parent: InspectorLineModel?): InspectorLineModel {
    addSeparatorBeforeTitle()
    val model = TitleLineModel(title)
    val label = CollapsibleLabelPanel(model, UIUtil.FontSize.NORMAL, Font.BOLD)
    label.isOpaque = true
    label.innerBorder = JBUI.Borders.empty(SUBTITLE_SEPARATOR_HEIGHT, 0)
    label.border = JBUI.Borders.merge(JBUI.Borders.empty(0, LEFT_HORIZONTAL_CONTENT_BORDER_SIZE, 0, 0),
                                      SideBorder(JBColor.border(), SideBorder.BOTTOM), true)
    addLine(model, parent)
    inspector.addLineElement(label)
    lastTitleLine = model
    model.makeExpandable(initiallyExpanded)
    return model
  }

  override fun addCustomEditor(editorModel: PropertyEditorModel, editor: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    addSeparatorAfterTitle(parent)
    val model = CollapsibleLabelModel(editorModel.property.name, editorModel)
    val label = CollapsibleLabelPanel(model, UIUtil.FontSize.SMALL, Font.PLAIN)
    label.border = JBUI.Borders.emptyLeft(LEFT_HORIZONTAL_CONTENT_BORDER_SIZE)
    editorModel.lineModel = model
    addLine(model, parent)
    inspector.addLineElement(label, editor)
    return model
  }

  override fun addTable(tableModel: PTableModel,
                        searchable: Boolean,
                        tableUI: TableUIProvider,
                        actions: List<AnAction>,
                        parent: InspectorLineModel?): TableLineModel {
    // Do NOT call addSeparatorAfterTitle since tables should not be preceded with spacing after a title
    val model = TableLineModelImpl(tableModel, searchable)
    val editor = TableEditor(model, tableUI.tableCellRendererProvider, tableUI.tableCellEditorProvider, actions, nameColumnFraction)
    addLine(model, parent)
    inspector.addLineElement(editor.component)
    return model
  }

  override fun addComponent(component: JComponent, parent: InspectorLineModel?): InspectorLineModel {
    addSeparatorAfterTitle(parent)
    val model = GenericInspectorLineModel()
    val wrapper = GenericLinePanel(component, model)
    addLine(model, parent)
    inspector.addLineElement(wrapper)
    return model
  }

  private fun addLine(model: GenericInspectorLineModel, parent: InspectorLineModel?) {
    parent?.let { checkNewParent(it).addChild(model) }
    inspectorModel.add(model)
    lastAddedLine = model
  }

  private fun checkNewParent(parent: InspectorLineModel): CollapsibleLabelModel {
    val label = parent as? CollapsibleLabelModel ?: throw IllegalArgumentException()
    if (!label.expandable) {
      throw IllegalArgumentException()
    }
    return label
  }

  private fun addSeparatorBeforeTitle() {
    if (lastAddedLine == null || lastAddedLine == lastTitleLine || lastAddedLine is TableLineModel) {
      return
    }
    addSeparator(bottomDivider = true, parent = topParent(lastAddedLine))
  }

  private fun addSeparatorAfterTitle(parent: InspectorLineModel?) {
    if (lastAddedLine == null || lastAddedLine == lastTitleLine) {
      addSeparator(bottomDivider = false, parent = parent)
    }
    else {
      // Special case:
      // If the previous line belongs to a SubTitle and the line being added belongs
      // to a different title, add a separator here such that there will be a separator
      // between the SubTitle and this the line being added when the SubTitle is closed.
      val lastTopParent = topParent(lastAddedLine)
      val topParent = topParent(parent)
      if (lastTopParent != topParent) {
        addSeparator(bottomDivider = false, parent = topParent)
      }
    }
  }

  private fun topParent(line: InspectorLineModel?): InspectorLineModel? {
    var topParent: InspectorLineModel? = line
    while (topParent?.parent != null && topParent !is TitleLineModel) {
      topParent = topParent.parent
    }
    return topParent as? TitleLineModel
  }

  private fun addSeparator(bottomDivider: Boolean, parent: InspectorLineModel? = null): GenericInspectorLineModel {
    val component = JPanel()
    component.preferredSize = JBDimension(0, TITLE_SEPARATOR_HEIGHT)
    component.background = inspector.background
    component.border = if (bottomDivider) SideBorder(JBColor.border(), SideBorder.BOTTOM) else JBUI.Borders.empty()
    val model = SeparatorLineModel()
    val wrapper = GenericLinePanel(component, model)
    addLine(model, parent)
    inspector.addLineElement(wrapper)
    return model
  }
}
