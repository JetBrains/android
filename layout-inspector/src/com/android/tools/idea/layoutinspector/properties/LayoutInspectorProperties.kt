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
package com.android.tools.idea.layoutinspector.properties

import com.android.tools.adtui.stdui.StandardColors.PLACEHOLDER_TEXT_COLOR
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.idea.layoutinspector.LayoutInspector
import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.android.tools.idea.layoutinspector.model.SelectionOrigin
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.property.panel.api.PropertiesPanel
import com.google.common.html.HtmlEscapers
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JLabel
import javax.swing.JPanel

const val PROPERTIES_COMPONENT_NAME = "Properties Component"
const val NO_SELECTION_CARD = "No Selection"
const val SELECTED_VIEW_CARD = "View Selected"
const val INFO_TEXT = "Info Text"

class LayoutInspectorProperties(parentDisposable: Disposable) : ToolContent<LayoutInspector> {
  private val componentModel = InspectorPropertiesModel(parentDisposable)
  private val componentView = InspectorPropertiesView(componentModel)
  private val cardLayout = CardLayout()
  private val cardView = JPanel(cardLayout)
  private val properties = PropertiesPanel<InspectorPropertyItem>(this)
  private val filterKeyListener = createFilterKeyListener()
  private val selectionListener: (ViewNode?, ViewNode?, SelectionOrigin) -> Unit

  init {
    properties.component.name = PROPERTIES_COMPONENT_NAME
    properties.addView(componentView)
    val infoPanel = JPanel(BorderLayout())
    val text =
      HtmlEscapers.htmlEscaper().escape(LayoutInspectorBundle.message("no.selection.no.properties"))
    val infoText = JLabel("<html><div style='text-align: center;'>$text</div></html>")
    infoText.border = JBUI.Borders.empty(50)
    infoText.name = INFO_TEXT
    infoText.foreground = PLACEHOLDER_TEXT_COLOR
    infoPanel.add(infoText, BorderLayout.CENTER)
    cardView.add(infoPanel, NO_SELECTION_CARD)
    cardView.add(properties.component, SELECTED_VIEW_CARD)
    Disposer.register(parentDisposable, this)
    selectionListener = { _, newView, _ ->
      cardLayout.show(cardView, if (newView == null) NO_SELECTION_CARD else SELECTED_VIEW_CARD)
    }
    cardLayout.show(cardView, NO_SELECTION_CARD)
  }

  override fun setToolContext(toolContext: LayoutInspector?) {
    componentModel.layoutInspector?.inspectorModel?.removeSelectionListener(selectionListener)
    componentModel.layoutInspector = toolContext
    componentModel.layoutInspector?.inspectorModel?.addSelectionListener(selectionListener)
  }

  override fun getComponent() = cardView

  override fun dispose() {
    setToolContext(null)
  }

  override fun getGearActions() = listOf(DimensionUnitAction)

  override fun supportsFiltering() = true

  override fun setFilter(filter: String) {
    properties.filter = filter
  }

  override fun getFilterKeyListener() = filterKeyListener

  override fun isFilteringActive(): Boolean {
    return componentModel.layoutInspector?.currentClient?.isConnected ?: false
  }

  private fun createFilterKeyListener() =
    object : KeyAdapter() {
      override fun keyPressed(event: KeyEvent) {
        if (
          properties.filter.isNotEmpty() &&
            event.keyCode == KeyEvent.VK_ENTER &&
            event.modifiers == 0 &&
            properties.enterInFilter()
        ) {
          event.consume()
        }
      }
    }
}
