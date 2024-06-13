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

import com.android.tools.adtui.model.stdui.EditingErrorCategory
import com.android.tools.property.panel.api.PropertyItem
import com.intellij.ide.HelpTooltip
import com.intellij.util.text.nullize
import javax.swing.JComponent

private const val MAX_TOOLTIP_TEXT_LENGTH = 1000

/** Implementation of custom tool tips for displaying errors and warnings. */
object PropertyTooltip {

  fun setToolTip(component: JComponent, text: String?) {
    if (text == null) {
      hideTooltip(component)
    } else {
      createTooltipWithContent(component, text)
    }
  }

  fun setToolTip(component: JComponent, property: PropertyItem?, forValue: Boolean, text: String) {
    if (property == null) {
      hideTooltip(component)
    } else {
      createToolTip(component, property, forValue, text)
    }
  }

  private fun createToolTip(
    component: JComponent,
    property: PropertyItem,
    forValue: Boolean,
    currentText: String,
  ) {
    if (!forValue) {
      val text = property.tooltipForName.nullize()
      setToolTip(component, text)
      return
    }

    val validation = property.editingSupport.validation(currentText)
    val (title, text) =
      when (validation.first) {
        EditingErrorCategory.ERROR -> Pair("Error", validation.second)
        EditingErrorCategory.WARNING -> Pair("Warning", validation.second)
        else ->
          property.tooltipForValue.nullize()?.let { Pair(null, it) }
            ?: return hideTooltip(component)
      }
    createTooltipWithContent(component, text, title)
  }

  private fun hideTooltip(component: JComponent) {
    HelpTooltip.dispose(component)
  }

  private fun createTooltipWithContent(component: JComponent, text: String, title: String? = null) {
    val truncatedText =
      if (text.length > MAX_TOOLTIP_TEXT_LENGTH) text.substring(0, MAX_TOOLTIP_TEXT_LENGTH) + "..."
      else text
    HelpTooltip().setTitle(title).setDescription(truncatedText).installOn(component)
  }
}
