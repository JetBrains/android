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

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.common.util.WhiteIconGenerator
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.google.common.collect.ImmutableList
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import icons.StudioIcons
import org.jetbrains.android.dom.navigation.NavActionElement
import org.jetbrains.android.dom.navigation.NavigationSchema
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
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
    val tag = components[0].tag
    val schema = NavigationSchema.getOrCreateSchema(propertiesManager.facet)
    if (schema.getDestinationClassByTag(tag.name) == null ||
        !schema.getDestinationSubtags(tag.name).containsKey(NavActionElement::class.java)) {
      return false
    }
    val tagName = tag.name
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
    private val myProperties = LinkedHashMap<NlProperty, String>()

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      myProperties.clear()

      for (name in TreeSet(properties.keys)) {
        val value = properties[name]
        if (value?.tagName == NavigationSchema.TAG_ACTION) {
          NlComponent.stripId(name)?.let { myProperties.put(value, it) }
        }
      }
    }

    override fun getEditors(): List<NlComponentEditor> = ImmutableList.of()

    override fun getMaxNumberOfRows() = 1

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      refresh()
      val panel = JPanel(BorderLayout())
      val list = JBList(myProperties.keys)
      list.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
      list.cellRenderer = object : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          text = myProperties[value as NlProperty?]
          icon = if (isSelected) WHITE_ACTION
                 else StudioIcons.NavEditor.Toolbar.ACTION
          return this
        }
      }

      panel.add(list, BorderLayout.CENTER)
      val plus = JLabel("+")
      plus.font = Font(null, Font.BOLD, 14)
      plus.foreground = JBColor.GRAY
      plus.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent?) {
          (inspector as? NavInspectorPanel)?.addAction()
        }
      })
      val plusPanel = JPanel(BorderLayout())
      plusPanel.add(plus, BorderLayout.EAST)

      inspector.addExpandableComponent("Actions", null, plusPanel, plusPanel)
      inspector.addPanel(panel)
    }

    override fun refresh() {}

    companion object {
      private val WHITE_ACTION = WhiteIconGenerator.generateWhiteIcon(StudioIcons.NavEditor.Toolbar.ACTION)
    }
  }
}
