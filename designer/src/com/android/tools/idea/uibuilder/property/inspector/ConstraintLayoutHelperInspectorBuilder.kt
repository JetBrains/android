/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.AndroidXConstants
import com.android.SdkConstants.AUTO_URI
import com.android.SdkConstants.CONSTRAINT_REFERENCED_IDS
import com.android.tools.adtui.LightCalloutPopup
import com.android.tools.adtui.common.secondaryPanelBackground
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.model.isOrHasSuperclass
import com.android.tools.idea.uibuilder.property.NlPropertyItem
import com.android.tools.idea.uibuilder.property.ui.ReferencesIdsPanel
import com.android.tools.property.panel.api.EditorProvider
import com.android.tools.property.panel.api.InspectorBuilder
import com.android.tools.property.panel.api.InspectorLineModel
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.api.PropertiesTable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.util.ui.JBUI
import icons.StudioIcons
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.LayoutFocusTraversalPolicy
import javax.swing.border.EmptyBorder

private const val ADD_PROPERTY_ACTION_TITLE = "Add View Reference"
private const val DELETE_ROW_ACTION_TITLE = "Remove selected View reference"

/**
 * Builder for the ConstraintHelper references panel
 */
class ConstraintLayoutHelperInspectorBuilder(private val editorProvider: EditorProvider<NlPropertyItem>) : InspectorBuilder<NlPropertyItem> {

  override fun attachToInspector(inspector: InspectorPanel, properties: PropertiesTable<NlPropertyItem>) {
    if (properties.isEmpty || !InspectorSection.REFERENCES.visible) {
      return
    }
    if (!isApplicable(properties)) return

    val property = properties.getOrNull(AUTO_URI, CONSTRAINT_REFERENCED_IDS) ?: return

    var referencesIdsPanel = ReferencesIdsPanel()
    referencesIdsPanel.setProperty(property)

    val addNewRow = AddNewRowAction(referencesIdsPanel)
    val deleteRowAction = DeleteRowAction(referencesIdsPanel)
    val actions = listOf(addNewRow, deleteRowAction)

    val titleModel = inspector.addExpandableTitle(InspectorSection.REFERENCES.title, false, actions)
    inspector.addComponent(referencesIdsPanel, titleModel)
    inspector.addEditor(editorProvider.createEditor(property), titleModel)
  }

  private class AddNewRowAction(panel: ReferencesIdsPanel) : AnAction(null, ADD_PROPERTY_ACTION_TITLE, StudioIcons.Common.ADD) {
    var panel = panel
    var dataModel = panel.getDataModel()
    var titleModel: InspectorLineModel? = null

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      val popupMenu = LightCalloutPopup({})
      val picker = createPopupPanel(popupMenu)
      popupMenu.show(picker, panel, Point(panel.width - JBUI.scale(40), 0), Balloon.Position.below)
    }

    private fun createPopupPanel(popupMenu: LightCalloutPopup): JPanel {
      val picker = JPanel(BorderLayout())
      picker.background = secondaryPanelBackground
      picker.preferredSize = Dimension(150, 70)
      val label = JLabel("Pick a View to add")
      label.border = EmptyBorder(8, 8, 8, 8)
      picker.add(label, BorderLayout.NORTH)
      val comboBox = ComboBox<String>()
      fillCombobox(comboBox)
      comboBox.border = EmptyBorder(8, 8, 8, 8)
      comboBox.putClientProperty("JComboBox.isTableCellEditor", true)
      comboBox.addActionListener {
        val selectedIndex = comboBox.selectedIndex
        var referencePicked = comboBox.getItemAt(selectedIndex)
        popupMenu.close()
        if (selectedIndex > 0) { // first element is empty
          dataModel.addReference(referencePicked)
        }
      }
      picker.add(comboBox)
      picker.isFocusCycleRoot = true
      picker.focusTraversalPolicy = LayoutFocusTraversalPolicy()
      val escapeKeyStroke = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0, false)
      val escapeAction: Action = object : AbstractAction() {
        override fun actionPerformed(e: ActionEvent) {
          popupMenu.close()
        }
      }
      picker.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escapeKeyStroke, "ESCAPE")
      picker.actionMap.put("ESCAPE", escapeAction)

      return picker
    }

    private fun fillCombobox(comboBox: ComboBox<String>) {
      comboBox.addItem("")
      var listIds = panel.getListIds();
      listIds.forEach { comboBox.addItem(it) }
    }
  }

  private class DeleteRowAction(panel: ReferencesIdsPanel) : AnAction(null, DELETE_ROW_ACTION_TITLE, StudioIcons.Common.REMOVE) {
    var panel = panel
    var titleModel: InspectorLineModel? = null

    init {
      val manager = ActionManager.getInstance()
      shortcutSet = manager.getAction(IdeActions.ACTION_DELETE).shortcutSet
    }

    override fun update(event: AnActionEvent) {
      val enabled = panel.getDataModel().rowCount > 0
      event.presentation.isEnabled = enabled
    }

    override fun actionPerformed(event: AnActionEvent) {
      titleModel?.expanded = true
      panel.getDataModel().removeRow(panel.getSelectedRowIndex())
    }
  }

  companion object {
    fun isApplicable(properties: PropertiesTable<NlPropertyItem>): Boolean {
      var components: List<NlComponent>? = properties.first?.components ?: return false
      if (components!!.isEmpty()) return false
      var component: NlComponent? = components?.get(0)
      return component!!.isOrHasSuperclass(AndroidXConstants.CLASS_CONSTRAINT_LAYOUT_HELPER)
    }
  }

}