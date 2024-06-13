/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.ide.common.resources.parseColor
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.idea.res.RESOURCE_ICON_SIZE
import com.android.tools.property.panel.api.ActionIconButton
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ColorIcon
import javax.swing.Icon

/** ActionIconButton for the left side of the attribute value. */
class ColorActionIconButton(override val actionIcon: Icon?) : ActionIconButton {
  override val actionButtonFocusable = false
  override val action: AnAction? = null

  companion object {
    suspend fun createColorButton(
      type: PropertyType,
      value: String?,
      view: ViewNode,
      lookup: ResourceLookup,
    ): ActionIconButton? =
      when (type) {
        PropertyType.COLOR,
        PropertyType.DRAWABLE ->
          value?.let { ColorActionIconButton(createColorButtonIcon(value, view, lookup)) }
        else -> null
      }

    private suspend fun createColorButtonIcon(
      value: String?,
      view: ViewNode,
      lookup: ResourceLookup,
    ): Icon? {
      lookup.resolveAsIcon(value, view)?.let {
        return it
      }
      val color = value?.let { parseColor(value) } ?: return null
      return JBUIScale.scaleIcon(ColorIcon(RESOURCE_ICON_SIZE, color, false))
    }
  }
}
