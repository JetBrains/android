/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.compose.pickers.common.enumsupport

import com.android.tools.idea.compose.pickers.base.enumsupport.DescriptionEnumValue
import com.android.tools.property.panel.api.EnumValue
import com.android.tools.property.panel.api.HeaderEnumValue
import com.android.tools.property.panel.impl.ui.EnumValueListCellRenderer
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
import com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseEvent
import javax.swing.Icon

/** Cell renderer for Dropdown popups, supports separators and tooltips. */
internal class PsiEnumValueCellRenderer : EnumValueListCellRenderer() {
  private val headerLabel = JBLabel().apply { foreground = JBColor(0x444444, 0xCCCCCC) }
  private val headerRenderer =
    TitledSeparator().apply {
      this@apply.layout = BorderLayout()
      this@apply.border = JBUI.Borders.empty(2, 2)
      this@apply.removeAll()
      this@apply.add(headerLabel)
    }

  override fun customize(item: EnumValue) {
    when (item) {
      is DescriptionEnumValue -> append(item.display, REGULAR_ATTRIBUTES, item.description)
      is HeaderEnumValue -> append(item.display, GRAYED_ATTRIBUTES)
      else -> append(item.display)
    }
  }

  override fun getToolTipText(event: MouseEvent?): String? =
    getAndFormatDescription() ?: super.getToolTipText(event)

  override fun getHeaderRenderer(header: String, headerIcon: Icon?): Component {
    headerLabel.icon = headerIcon
    headerLabel.text = header
    return headerRenderer
  }

  private fun getAndFormatDescription(): String? {
    val iterator = iterator()
    if (iterator.hasNext()) {
      // Make sure the cell renderer has any content
      iterator.next()
      val description =
        iterator
          .tag // The tag should correspond to the description attached from DescriptionEnumValue
      if (description is String) {
        // Limit the width of the resulting text using html
        return HtmlChunk.div()
          .attr("width", JBUIScale.scale(250))
          .addRaw(description)
          .wrapWith(HtmlChunk.html())
          .toString()
      }
    }
    return null
  }
}
