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
package com.android.tools.idea.common.property2.api

import com.android.tools.idea.common.property2.impl.model.CollapsibleLabelModel
import com.android.tools.idea.common.property2.impl.model.GenericInspectorLineModel
import com.android.tools.idea.common.property2.impl.model.InspectorPanelModel
import com.android.tools.idea.common.property2.impl.ui.CollapsibleLabel
import com.android.tools.idea.common.property2.impl.ui.GenericLinePanel
import com.android.tools.idea.common.property2.impl.ui.InspectorPanelImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.ScrollPaneFactory
import java.awt.BorderLayout
import java.util.*
import javax.swing.*

private const val VERTICAL_SCROLLING_UNIT_INCREMENT = 3
private const val VERTICAL_SCROLLING_BLOCK_INCREMENT = 25

/**
 * The top level class for creating UI classes and model classes for a properties panel.
 *
 * Creates the main [component] for the properties panel which at this point contains
 * a property inspector. Separate views such as a tabular view may be added at a later
 * point.
 * The content of the inspector is controlled by a list of [PropertiesView]s which
 * must be added to this class using [addView].
 */
class PropertiesPanel(parentDisposable: Disposable) : InspectorPanel, Disposable, PropertiesModelListener {

  val component = JPanel(BorderLayout())

  var filter
    get() = inspectorModel.filter
    set(value) {
      inspectorModel.filter = value
    }

  private var activeModel: PropertiesModel<*>? = null
  private val views = IdentityHashMap<PropertiesModel<*>, PropertiesView<*>>()
  private val inspectorModel = InspectorPanelModel()
  private val inspector = InspectorPanelImpl(inspectorModel, this)
  private val gotoNextLine: (InspectorLineModel) -> Unit = { inspectorModel.moveToNextLineEditor(it) }

  init {
    val scrollPane = ScrollPaneFactory.createScrollPane(
      inspector,
      ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
      ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    scrollPane.border = BorderFactory.createEmptyBorder()
    scrollPane.verticalScrollBar.unitIncrement = VERTICAL_SCROLLING_UNIT_INCREMENT
    scrollPane.verticalScrollBar.blockIncrement = VERTICAL_SCROLLING_BLOCK_INCREMENT

    component.add(scrollPane, BorderLayout.CENTER)
    Disposer.register(parentDisposable, this)
  }

  fun addView(view: PropertiesView<*>) {
    views[view.model] = view
    view.model.addListener(this)
  }

  override fun propertiesGenerated(model: PropertiesModel<*>) {
    populateInspector(model)
  }

  override fun propertyValuesChanged(model: PropertiesModel<*>) {
    if (model == activeModel) {
      inspectorModel.propertyValuesChanged()
    }
  }

  fun enterInFilter(): Boolean {
    return inspectorModel.enterInFilter()
  }

  private fun populateInspector(model: PropertiesModel<*>) {
    val view = views[model] ?: return
    if (activeModel != model) {
      activeModel?.deactivate()
      activeModel = model
    }
    inspectorModel.clear()
    inspector.removeAll()
    view.attachToInspector(this)
    inspector.revalidate()
    inspector.repaint()
  }

  override fun dispose() {
    views.keys.forEach { it.removeListener(this) }
  }

  override fun addTitle(title: String): InspectorLineModel {
    val model = CollapsibleLabelModel(title)
    val label = CollapsibleLabel(model, true)
    inspectorModel.add(model)
    inspector.addLineElement(label)
    model.gotoNextLine = gotoNextLine
    return model
  }

  override fun addEditor(editorModel: PropertyEditorModel, editor: JComponent): InspectorLineModel {
    val model = CollapsibleLabelModel(editorModel.property.name, editorModel)
    val label = CollapsibleLabel(model, false)
    editorModel.lineModel = model
    inspectorModel.add(model)
    inspector.addLineElement(label, editor)
    model.gotoNextLine = gotoNextLine
    return model
  }

  override fun addComponent(component: JComponent): InspectorLineModel {
    val model = GenericInspectorLineModel()
    val wrapper = GenericLinePanel(component, model)
    inspectorModel.add(model)
    inspector.addLineElement(wrapper)
    model.gotoNextLine = gotoNextLine
    return model
  }

  override fun addSeparator(): InspectorLineModel {
    return addComponent(JSeparator())
  }
}
