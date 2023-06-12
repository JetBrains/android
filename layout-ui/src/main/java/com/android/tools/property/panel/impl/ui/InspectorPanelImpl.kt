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

import com.android.tools.adtui.common.AdtSecondaryPanel
import com.android.tools.adtui.model.stdui.ValueChangedListener
import com.android.tools.property.panel.api.InspectorPanel
import com.android.tools.property.panel.impl.model.InspectorPanelModel
import com.android.tools.property.ptable.ColumnFraction
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Rectangle
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

typealias ComponentBounds = com.intellij.openapi.util.Pair<Component, Rectangle>

/**
 * Implementation of [InspectorPanel].
 */
class InspectorPanelImpl(val model: InspectorPanelModel, nameColumnFraction: ColumnFraction, parentDisposable: Disposable) :
  AdtSecondaryPanel(InspectorLayoutManager(nameColumnFraction)), Disposable, ValueChangedListener {

  init {
    Disposer.register(parentDisposable, this)
    border = JBUI.Borders.empty()
    model.addValueChangedListener(this)
  }

  fun addLineElement(component: JComponent) {
    add(component, Placement.LINE)
  }

  fun addLineElement(label: CollapsibleLabelPanel, component: JComponent) {
    add(label, Placement.LEFT)
    add(component, Placement.RIGHT)
  }

  override fun dispose() {
    model.removeValueChangedListener(this)
  }

  override fun valueChanged() {
    revalidate()
    repaint()
  }

  override fun getToolTipText(event: MouseEvent): String? {
    val point = SwingUtilities.convertPoint(event.component, event.point, this)
    if (!contains(point)) {
      return null
    }
    val component = getComponentAt(point.x, point.y) as? CollapsibleLabelPanel ?: return null
    return PropertyTooltip.setToolTip(this, event, component.model.editorModel?.property, forValue = false, text = "")
  }
}
