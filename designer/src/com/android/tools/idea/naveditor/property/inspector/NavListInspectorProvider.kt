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

import com.android.ide.common.resources.ResourceResolver
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.naveditor.property.ListProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.ui.JBColor
import com.intellij.ui.SortedListModel
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.*

const val NAV_LIST_COMPONENT_NAME = "NavListPropertyInspector"

abstract class NavListInspectorProvider<PropertyType : ListProperty>(
    private val propertyType: Class<PropertyType>, val icon: Icon)
  : InspectorProvider<NavPropertiesManager> {

  // If we decide to guarantee that each subclass only handles a single tag this map can be replaced with a single value
  private val myInspectors = HashMap<String, InspectorComponent<NavPropertiesManager>>()

  private val whiteIcon = ColoredIconGenerator.generateWhiteIcon(icon)

  override fun isApplicable(components: List<NlComponent>,
                            properties: Map<String, NlProperty>,
                            propertiesManager: NavPropertiesManager): Boolean {
    if (components.size != 1) {
      return false
    }
    if (properties.values.none { propertyType.isInstance(it) }) {
      return false
    }
    val tagName = components[0].tagName
    if (myInspectors.containsKey(tagName)) {
      return true
    }
    myInspectors.put(tagName, NavListInspectorComponent(this))
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

  abstract protected fun addItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?)

  abstract protected fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?): String

  private class NavListInspectorComponent<PropertyType : ListProperty>(val provider: NavListInspectorProvider<PropertyType>):
      InspectorComponent<NavPropertiesManager> {

    private val myDisplayProperties = SortedListModel<NlProperty>(compareBy { it.name } )
    private val myMarkerProperties = mutableListOf<PropertyType>()
    private val myComponents = mutableListOf<NlComponent>()
    private var mySurface: NavDesignSurface? = null

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      myMarkerProperties.clear()
      myComponents.clear()
      myComponents.addAll(components)

      mySurface = propertiesManager.designSurface as? NavDesignSurface

      properties.values.filterIsInstanceTo(myMarkerProperties, provider.propertyType)
      refresh()
    }

    override fun getEditors(): List<NlComponentEditor> = listOf()

    override fun getMaxNumberOfRows() = 1

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      val panel = JPanel(BorderLayout())
      val list = JBList<NlProperty>(myDisplayProperties)
      list.name = NAV_LIST_COMPONENT_NAME
      list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      list.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          text = (value as NlProperty?)?.name
          // TODO: truncate to actual width of the frame
          if (text.length > 25) {
            text = text.substring(0, 22) + "..."
          }
          icon = if (isSelected) provider.whiteIcon else provider.icon
          return this
        }
      }
      list.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          if (e?.clickCount == 2 && list.selectedValuesList.size == 1) {
            provider.addItem(list.selectedValue.components[0], myComponents, myMarkerProperties[0].resolver)
            refresh()
          }
        }
      })
      list.addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent?) {
          when (e?.keyCode) {
            KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
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
          provider.addItem(null, myComponents, myMarkerProperties[0].resolver)
          refresh()
        }
      })
      val plusPanel = JPanel(BorderLayout())
      plusPanel.add(plus, BorderLayout.EAST)

      val title = provider.getTitle(myComponents, mySurface)
      inspector.addExpandableComponent(title, null, plusPanel, plusPanel)
      inspector.addPanel(panel)
    }

    override fun refresh() {
      myDisplayProperties.clear()

      myMarkerProperties.flatMap {
        it.refreshList()
        it.properties.values
      }.forEach { myDisplayProperties.add(it) }
    }
  }
}