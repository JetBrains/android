/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.property.inspector

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.common.util.WhiteIconGenerator
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.ui.JBColor
import com.intellij.ui.SortedListModel
import com.intellij.ui.components.JBList
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.*
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_DELETE
import java.util.*
import javax.swing.*

class NavigationActionsInspectorProvider : InspectorProvider<NavPropertiesManager> {
  private val myInspectors = HashMap<String, InspectorComponent<NavPropertiesManager>>()

  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    if (components.size != 1) {
      return false
    }
    if (properties.values.none { it is NavActionsProperty }) {
      return false
    }
    val tagName = components[0].tagName
    if (myInspectors.containsKey(tagName)) {
      return true
    }
    myInspectors.put(tagName, NavActionListInspectorComponent())
    return true
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): InspectorComponent<NavPropertiesManager> {
    val tagName = components[0].tagName
    val inspector = myInspectors[tagName]!!
    inspector.updateProperties(components, properties, propertiesManager)
    return inspector
  }

  override fun resetCache() {
    myInspectors.clear()
  }

  private class NavActionListInspectorComponent : InspectorComponent<NavPropertiesManager> {
    private val myActions = SortedListModel<NlProperty>(compareBy { it.name } )
    private val myProperties = mutableListOf<NavActionsProperty>()
    private val myComponents = mutableListOf<NlComponent>()
    private var mySurface: NavDesignSurface? = null

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      myProperties.clear()
      myComponents.clear()
      myComponents.addAll(components)

      mySurface = propertiesManager.designSurface as? NavDesignSurface

      properties.values.filterIsInstanceTo(myProperties, NavActionsProperty::class.java)
      refresh()
    }

    override fun getEditors(): List<NlComponentEditor> = listOf()

    override fun getMaxNumberOfRows() = 1

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      val panel = JPanel(BorderLayout())
      val list = JBList<NlProperty>(myActions)
      list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      list.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          text = (value as NlProperty?)?.name
          icon = if (isSelected) WHITE_ACTION
                 else StudioIcons.NavEditor.Properties.ACTION
          return this
        }
      }
      list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          if (e?.clickCount == 2 && list.selectedValuesList.size == 1) {
            val actionComponent = list.selectedValue.components[0]
            addOrUpdateAction(AddActionDialog(actionComponent, null), { actionComponent })
          }
        }
      })
      list.addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent?) {
          when (e?.keyCode) {
            VK_DELETE, VK_BACK_SPACE -> {
              list.selectedValue.components.let { it[0].model.delete(it)}
              refresh()
            }
          }
        }
      })

      panel.add(list, BorderLayout.CENTER)
      val plus = JLabel("+")
      plus.font = Font(null, Font.BOLD, 14)
      plus.foreground = JBColor.GRAY
      plus.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          addAction()
        }
      })
      val plusPanel = JPanel(BorderLayout())
      plusPanel.add(plus, BorderLayout.EAST)

      val title = if (myComponents.size == 1 && myComponents[0] == mySurface?.currentNavigation) { "Global Actions" } else { "Actions" }
      inspector.addExpandableComponent(title, null, plusPanel, plusPanel)
      inspector.addPanel(panel)
    }

    override fun refresh() {
      myActions.clear()

      myProperties.flatMap {
        it.refreshActionList()
        it.actions.values
      }.forEach { myActions.add(it) }
    }

    fun addAction() {
      val addActionDialog = AddActionDialog(myComponents, myProperties[0].resolver)
      val componentProducer = {
        val source = addActionDialog.source
        val tag = source.tag.createChildTag(NavigationSchema.TAG_ACTION, null, null, false)
        source.model.createComponent(tag, source, null)
      }
      addOrUpdateAction(addActionDialog, componentProducer)
      refresh()
    }
  }
}

private val WHITE_ACTION = WhiteIconGenerator.generateWhiteIcon(StudioIcons.NavEditor.Properties.ACTION)

private fun addOrUpdateAction(addActionDialog: AddActionDialog, componentProducer: () -> NlComponent) {
  if (addActionDialog.showAndGet()) {
    WriteCommandAction.runWriteCommandAction(null, {
      val newComponent = componentProducer()
      newComponent.ensureId()
      newComponent.setAttribute(
          SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, SdkConstants.ID_PREFIX + addActionDialog.destination.id!!)
      addActionDialog.popTo?.let { newComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO, it) }
          ?: newComponent.removeAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO)
      if (addActionDialog.isInclusive) {
        newComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_POP_UP_TO_INCLUSIVE, "true")
      }
      if (addActionDialog.isSingleTop) {
        newComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_SINGLE_TOP, "true")
      }
      if (addActionDialog.isDocument) {
        newComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DOCUMENT, "true")
      }
      if (addActionDialog.isClearTask) {
        newComponent.setAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_CLEAR_TASK, "true")
      }
    })
  }
}
