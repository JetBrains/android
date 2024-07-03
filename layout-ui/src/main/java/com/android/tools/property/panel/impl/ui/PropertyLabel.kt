/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.adtui.stdui.CommonTextBorder
import com.android.tools.adtui.stdui.HIDE_RIGHT_BORDER
import com.android.tools.adtui.stdui.OUTLINE_PROPERTY
import com.android.tools.property.panel.api.TableExpansionState
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.android.tools.property.panel.impl.support.expandableText
import com.android.tools.property.ptable.KEY_IS_VISUALLY_RESTRICTED
import com.intellij.openapi.ui.ErrorBorderCapable
import com.intellij.ui.ClientProperty
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.plaf.UIResource

/**
 * Static text component.
 *
 * Used for certain table renderer instead of [PropertyTextField] to avoid scrolling, and clipping
 * of expanded text.
 */
class PropertyLabel(private val model: BasePropertyEditorModel) : JBLabel() {
  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    // This component is not editable. Taking focus would be confusing: b/147907441
    isFocusable = false
    model.addListener { updateFromModel() }
    ClientProperty.put(this, KEY_IS_VISUALLY_RESTRICTED) {
      // Allow expansion when the user is hovering over this label:
      model.tableExpansionState != TableExpansionState.NORMAL || width < preferredSize.width
    }
  }

  override fun getToolTipText(event: MouseEvent): String? {
    // Trick: Use the component from the event.source for tooltip in tables. See
    // TableEditor.getToolTip().
    val component = event.source as? JComponent ?: this
    PropertyTooltip.setToolTip(
      component,
      model.property,
      forValue = true,
      text = model.property.value.orEmpty(),
    )
    return null
  }

  private fun updateFromModel() {
    val actualValue = model.value
    val textValue = actualValue.takeIf { it.isNotEmpty() } ?: model.defaultValue
    val textColor =
      if (actualValue.isEmpty()) NamedColorUtil.getInactiveTextColor()
      else UIUtil.getLabelForeground()
    text = expandableText(textValue, model.tableExpansionState)
    isVisible = model.visible
    foreground = model.displayedForeground(textColor)
    background = model.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = model.isUsedInRendererWithSelection
    updateOutline()
    // Avoid painting the right vertical edge of the cell border if this is the left part of the
    // complete value:
    ClientProperty.put(
      this,
      HIDE_RIGHT_BORDER,
      model.tableExpansionState == TableExpansionState.EXPANDED_CELL_FOR_POPUP,
    )
  }

  // Update the outline property on component such that the Darcula border will
  // be able to indicate an error by painting a red border.
  private fun updateOutline() {
    // If this label is a renderer in a complex edit control,
    // set the property on the nearest parent that has an error border (which may be this Label).
    val component = getComponentWithErrorBorder() ?: return
    val (code, _) = model.property.editingSupport.validation(model.value)
    val newOutline = code.outline
    val current = component.getClientProperty(OUTLINE_PROPERTY)
    if (current != newOutline) {
      component.putClientProperty(OUTLINE_PROPERTY, newOutline)
    }
  }

  private fun getComponentWithErrorBorder(): JComponent? {
    var component: JComponent? = this
    while (component != null && component.border !is ErrorBorderCapable) {
      component = component.parent as? JComponent
    }
    return component
  }

  override fun updateUI() {
    super.updateUI()
    if (border == null || border is UIResource) {
      border = CommonTextBorder()
    }
  }
}
