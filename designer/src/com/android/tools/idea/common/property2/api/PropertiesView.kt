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
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSeparator

/**
 * The top level class for creating UI classes and model classes for a properties panel.
 *
 * Creates the main [component] for the properties panel which at this point contains
 * a property inspector. Separate views such as a tabular view may be added at a later
 * point.
 * The content of the inspector is controlled by a list of [InspectorBuilder]s which
 * must be added to this class.
 *
 * @param P the implementation specific class of a single property item.
 * @param model the model of the properties for this property panel.
 */
class PropertiesView<P: PropertyItem>(val model: PropertiesModel<P>, parentDisposable: Disposable) :
    InspectorPanel, Disposable, PropertiesModelListener {

  val builders = mutableListOf<InspectorBuilder<P>>()
  val component = JPanel(BorderLayout())
  val formModel: FormModel
    get() = inspectorModel

  var filter
    get() = inspectorModel.filter
    set(value) {
      inspectorModel.filter = value
    }

  private val inspectorModel = InspectorPanelModel()
  private val inspector = InspectorPanelImpl(inspectorModel, this)

  init {
    component.add(inspector, BorderLayout.CENTER)
    Disposer.register(parentDisposable, this)
    model.addListener(this)
  }

  override fun propertiesGenerated() {
    populateInspector()
  }

  override fun propertyValuesChanged() {
    inspectorModel.propertyValuesChanged()
  }

  fun enterInFilter(): Boolean {
    return inspectorModel.enterInFilter()
  }

  private fun populateInspector() {
    inspectorModel.clear()
    inspector.removeAll()
    builders.forEach { it.attachToInspector(this, model.properties) }
    inspector.revalidate()
    inspector.repaint()
  }

  override fun dispose() {
    model.removeListener(this)
  }

  override fun addTitle(title: String): InspectorLineModel {
    val model = CollapsibleLabelModel(title)
    val label = CollapsibleLabel(model, true)
    inspectorModel.add(model)
    inspector.addLineElement(label)
    return model
  }

  override fun addEditor(editorModel: PropertyEditorModel, editor: JComponent): InspectorLineModel {
    val model = CollapsibleLabelModel(editorModel.property.name, editorModel)
    val label = CollapsibleLabel(model, false)
    editorModel.line = model
    inspectorModel.add(model)
    inspector.addLineElement(label, editor)
    return model
  }

  override fun addComponent(component: JComponent): InspectorLineModel {
    val model = GenericInspectorLineModel()
    val wrapper = GenericLinePanel(component, model)
    inspectorModel.add(model)
    inspector.addLineElement(wrapper)
    return model
  }

  override fun addSeparator(): InspectorLineModel {
    return addComponent(JSeparator())
  }
}
