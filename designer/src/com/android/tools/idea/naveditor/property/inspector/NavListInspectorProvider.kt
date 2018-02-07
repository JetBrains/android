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

import com.android.annotations.VisibleForTesting
import com.android.ide.common.resources.ResourceResolver
import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.common.property.NlProperty
import com.android.tools.idea.common.property.editors.NlComponentEditor
import com.android.tools.idea.common.property.inspector.InspectorComponent
import com.android.tools.idea.common.property.inspector.InspectorPanel
import com.android.tools.idea.common.property.inspector.InspectorProvider
import com.android.tools.idea.naveditor.property.ListProperty
import com.android.tools.idea.naveditor.property.NavPropertiesManager
import com.android.tools.idea.naveditor.surface.NavDesignSurface
import com.intellij.openapi.actionSystem.*
import com.intellij.ui.JBColor
import com.intellij.ui.SortedListModel
import com.intellij.ui.components.JBList
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Font
import java.awt.Point
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.KeyEvent.VK_BACK_SPACE
import java.awt.event.KeyEvent.VK_ENTER
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

const val NAV_LIST_COMPONENT_NAME = "NavListPropertyInspector"

abstract class NavListInspectorProvider<PropertyType : ListProperty>(
    private val propertyType: Class<PropertyType>, val icon: Icon)
  : InspectorProvider<NavPropertiesManager> {

  protected val inspector = NavListInspectorComponent()

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
    return true
  }

  override fun createCustomInspector(components: List<NlComponent>,
                                     properties: Map<String, NlProperty>,
                                     propertiesManager: NavPropertiesManager): NavListInspectorComponent {
    inspector.updateProperties(components, properties, propertiesManager)
    return inspector
  }

  override fun resetCache() {
    inspector.reset()
  }

  protected open fun plusClicked(event: MouseEvent,
                                 parents: List<NlComponent>,
                                 resourceResolver: ResourceResolver?,
                                 surface: NavDesignSurface) =
      addItem(null, parents, resourceResolver)

  private fun addItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?) {
    doAddItem(existing, parents, resourceResolver)
    inspector.refresh()
  }

  protected abstract fun doAddItem(existing: NlComponent?, parents: List<NlComponent>, resourceResolver: ResourceResolver?)

  protected abstract fun getTitle(components: List<NlComponent>, surface: NavDesignSurface?): String

  @VisibleForTesting
  inner class NavListInspectorComponent :
      InspectorComponent<NavPropertiesManager> {

    private val displayProperties = SortedListModel<NlProperty>(compareBy { it.name })
    private val markerProperties = mutableListOf<PropertyType>()
    private val components = mutableListOf<NlComponent>()
    private var surface: NavDesignSurface? = null
    private val attachListeners = mutableListOf<(JBList<NlProperty>) -> Unit>()
    lateinit var list: JBList<NlProperty>

    fun addAttachListener(listener: (JBList<NlProperty>) -> Unit) = attachListeners.add(listener)

    override fun updateProperties(components: List<NlComponent>,
                                  properties: Map<String, NlProperty>,
                                  propertiesManager: NavPropertiesManager) {
      reset()
      this.components.addAll(components)

      surface = propertiesManager.designSurface as? NavDesignSurface

      properties.values.filterIsInstanceTo(markerProperties, propertyType)
      refresh()
    }

    override fun getEditors(): List<NlComponentEditor> = listOf()

    override fun getMaxNumberOfRows() = 1

    override fun attachToInspector(inspector: InspectorPanel<NavPropertiesManager>) {
      val panel = JPanel(BorderLayout())
      list = JBList<NlProperty>(displayProperties)
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
          icon = if (isSelected) whiteIcon else this@NavListInspectorProvider.icon
          return this
        }
      }
      list.addMouseListener(object : MouseAdapter() {
        override fun mouseReleased(e: MouseEvent) {
          maybeShowPopup(e)
        }

        override fun mousePressed(e: MouseEvent) {
          maybeShowPopup(e)
        }

        override fun mouseClicked(e: MouseEvent) {
          if (e.clickCount == 2 && list.selectedValuesList.size == 1) {
            addItem(list.selectedValue.components[0], components, markerProperties[0].resolver)
          }
        }
      })
      list.addKeyListener(object : KeyAdapter() {
        override fun keyReleased(e: KeyEvent?) {
          when (e?.keyCode) {
            KeyEvent.VK_DELETE, KeyEvent.VK_BACK_SPACE -> {
              list.selectedValue.model.delete(list.selectedValuesList.flatMap { it.components })
              refresh()
            }
          }
        }
      })
      attachListeners.forEach { it.invoke(list) }

      panel.add(list, BorderLayout.CENTER)
      val plus = JLabel("+")
      plus.font = Font(null, Font.BOLD, 14)
      plus.foreground = JBColor.GRAY
      plus.addMouseListener(object : MouseAdapter() {
        override fun mouseClicked(e: MouseEvent) {
          surface?.let { plusClicked(e, components, markerProperties[0].resolver, it) }
          refresh()
        }
      })
      val plusPanel = JPanel(BorderLayout())
      plusPanel.add(plus, BorderLayout.EAST)

      val title = getTitle(components, surface)
      inspector.addExpandableComponent(title, null, plusPanel, plusPanel)
      inspector.addPanel(panel)
    }

    @VisibleForTesting
    fun createPopupContent(e: MouseEvent): ActionGroup? {
      return if (e.isPopupTrigger) {
        @Suppress("UNCHECKED_CAST")
        val list = e.source as JBList<NlProperty>
        val clicked = list.model.getElementAt(list.locationToIndex(Point(e.x, e.y)))
        if (clicked !in list.selectedValuesList) {
          list.setSelectedValue(clicked, false)
        }
        createPopupMenu(list.selectedValuesList.flatMap { it.components })
      }
      else {
        null
      }
    }

    private fun maybeShowPopup(e: MouseEvent) {
      val group = createPopupContent(e) ?: return
      val actionManager = ActionManager.getInstance()
      val popupMenu = actionManager.createActionPopupMenu("NavListInspector", group)
      val invoker: Component = e.source as? Component ?: surface!!
      popupMenu.component.show(invoker, e.x, e.y)
    }

    private fun createPopupMenu(items: List<NlComponent>): ActionGroup {
      val actions = mutableListOf<AnAction>()
      if (items.size == 1) {
        actions.add(object : AnAction("Edit") {
          init {
            shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(VK_ENTER, 0))
          }

          override fun actionPerformed(e: AnActionEvent?) {
            addItem(items[0], components, markerProperties[0].resolver)
          }
        })
        actions.add(Separator.getInstance())
      }
      val deleteAction: AnAction = object : AnAction("Delete") {
        init {
          shortcutSet = CustomShortcutSet(KeyStroke.getKeyStroke(VK_BACK_SPACE, 0))
        }

        override fun actionPerformed(e: AnActionEvent?) {
          items[0].model.delete(items)
          refresh()
        }
      }
      actions.add(deleteAction)
      return DefaultActionGroup(actions)
    }

    override fun refresh() {
      displayProperties.clear()

      markerProperties.flatMap {
        it.refreshList()
        it.properties.values
      }.forEach { displayProperties.add(it) }
    }

    fun reset() {
      markerProperties.clear()
      this.components.clear()
    }
  }
}