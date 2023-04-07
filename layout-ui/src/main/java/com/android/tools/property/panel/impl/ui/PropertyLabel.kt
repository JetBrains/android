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

import com.android.tools.adtui.stdui.OUTLINE_PROPERTY
import com.android.tools.property.panel.impl.model.BasePropertyEditorModel
import com.google.common.html.HtmlEscapers
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import javax.swing.plaf.UIResource

/**
 * Static text component.
 *
 * Used for certain table renderer instead of [PropertyTextField] to avoid scrolling,
 * and clipping of expanded text.
 */
class PropertyLabel(private val model: BasePropertyEditorModel) : JBLabel() {
  init {
    background = UIUtil.TRANSPARENT_COLOR
    isOpaque = false
    model.addListener { updateFromModel() }
  }

  private fun updateFromModel() {
    text = model.value
    isVisible = model.visible
    foreground = model.displayedForeground(UIUtil.getLabelForeground())
    background = model.displayedBackground(UIUtil.TRANSPARENT_COLOR)
    isOpaque = model.isUsedInRendererWithSelection
    updateOutline()
  }

  // Update the outline property on component such that the Darcula border will
  // be able to indicate an error by painting a red border.
  private fun updateOutline() {
    val (code, _) = model.property.editingSupport.validation(model.value)
    val newOutline = code.outline
    val current = getClientProperty(OUTLINE_PROPERTY)
    if (current != newOutline) {
      putClientProperty(OUTLINE_PROPERTY, newOutline)
    }
  }

  private fun toHtml(str: String): String =
    "<html><nobr>${HtmlEscapers.htmlEscaper().escape(str)}</nobr></html>"

  override fun updateUI() {
    super.updateUI()
    if (border == null || border is UIResource) {
      border = DarculaTextBorder()
    }
  }
}
