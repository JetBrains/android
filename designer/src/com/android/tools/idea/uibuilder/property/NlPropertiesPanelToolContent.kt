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
package com.android.tools.idea.uibuilder.property

import com.android.tools.adtui.stdui.registerAnActionKey
import com.android.tools.adtui.workbench.ToolContent
import com.android.tools.adtui.workbench.ToolWindowCallback
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.uibuilder.property.inspector.neleDesignPropertySections
import com.android.tools.idea.uibuilder.property.support.ToggleShowResolvedValueAction
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.property.panel.api.PropertiesPanel
import com.android.tools.property.panel.api.PropertiesView
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.util.Disposer
import com.intellij.platform.ide.documentation.DOCUMENTATION_TARGETS
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JPanel
import org.jetbrains.android.facet.AndroidFacet

fun getPropertiesToolContent(component: Component?): NlPropertiesPanelToolContent? =
  ToolContent.getToolContent(component) as? NlPropertiesPanelToolContent

private const val UPDATE_QUEUE_NAME = "propertysheet"
private const val UPDATE_DELAY_MILLI_SECONDS = 250

/** Create the models and views for the properties tool content. */
class NlPropertiesPanelToolContent(facet: AndroidFacet, parentDisposable: Disposable) :
  JPanel(BorderLayout()), ToolContent<DesignSurface<*>> {
  private val queue =
    MergingUpdateQueue(
      UPDATE_QUEUE_NAME,
      UPDATE_DELAY_MILLI_SECONDS,
      true,
      null,
      parentDisposable,
      null,
      Alarm.ThreadToUse.SWING_THREAD,
    )
  private val componentModel = NlPropertiesModel(this, facet, queue)
  private val componentView = NlPropertiesView(componentModel)
  private val motionModel: NlPropertiesModel? =
    NlPropertiesModel.EP_NAME.extensionList.singleOrNull()?.create(this, facet, queue)
  private val motionEditorView: PropertiesView<NlPropertyItem>? =
    motionModel?.let { NlPropertiesView.EP_NAME.extensionList.singleOrNull()?.create(it) }
  private val properties = PropertiesPanel<NlPropertyItem>(componentModel)
  private val filterKeyListener = createFilterKeyListener()
  private val showResolvedValueAction = ToggleShowResolvedValueAction(componentModel)
  private val documentationTarget =
    NlPropertyDocumentationTarget(componentModel) { properties.selectedItem }
  private var toolWindow: ToolWindowCallback? = null

  init {
    Disposer.register(parentDisposable, this)
    add(properties.component, BorderLayout.CENTER)
    properties.addView(componentView)
    motionEditorView?.let { properties.addView(it) }
    DataManager.registerDataProvider(properties.component) { dataId ->
      if (DOCUMENTATION_TARGETS.`is`(dataId)) listOf(documentationTarget) else null
    }
    registerAnActionKey(
      { showResolvedValueAction },
      ToggleShowResolvedValueAction.SHORTCUT.firstKeyStroke,
      "toggleResolvedValues",
      WHEN_ANCESTOR_OF_FOCUSED_COMPONENT,
    )
  }

  override fun setToolContext(toolContext: DesignSurface<*>?) {
    componentModel.surface = toolContext as? NlDesignSurface
    motionModel?.surface = toolContext as? NlDesignSurface
  }

  override fun registerCallbacks(callback: ToolWindowCallback) {
    toolWindow = callback
  }

  override fun getComponent() = this

  override fun dispose() = Unit

  override fun supportsFiltering() = true

  override fun setFilter(filter: String) {
    properties.filter = filter
  }

  override fun getFilterKeyListener() = filterKeyListener

  override fun getGearActions(): List<AnAction> = neleDesignPropertySections.map { it.action }

  fun firePropertiesGenerated() = componentModel.firePropertiesGenerated()

  val isInspectorSectionsActive
    get() = !componentModel.properties.isEmpty

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
