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
package com.android.tools.idea.uibuilder.property2.support

import com.android.SdkConstants
import com.android.resources.ResourceType
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog
import com.android.tools.idea.uibuilder.property2.NelePropertiesModel
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.openapi.actionSystem.*
import java.awt.event.KeyEvent
import java.util.*
import javax.swing.KeyStroke

/**
 * Resource actions in Nele.
 *
 * Note: this may change pending UX specifications.
 */
class ToggleShowResolvedValueAction(val model: NelePropertiesModel) : AnAction("Toggle Computed Value") {

  init {
    shortcutSet = CustomShortcutSet(SHORTCUT)
  }

  override fun actionPerformed(e: AnActionEvent?) {
    model.showResolvedValues = !model.showResolvedValues
  }

  companion object {
    @JvmField
    val SHORTCUT = KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.META_MASK), null)
  }
}

class OpenResourceManagerAction(val property: NelePropertyItem) : AnAction("Open Resource Manager") {

  override fun actionPerformed(event: AnActionEvent) {
    val newValue = selectFromResourceDialog() ?: return
    property.value = newValue
  }

  private fun selectFromResourceDialog(): String? {
    val module = property.model.facet.module
    val propertyName = property.name
    val tag = if (property.components.size == 1) property.components[0].tag else null
    val hasImageTag = property.components.stream().filter { component -> component.tagName == SdkConstants.IMAGE_VIEW }.findFirst()
    val defaultResourceType = getDefaultResourceType(propertyName)
    val isImageViewDrawable = hasImageTag.isPresent &&
        (SdkConstants.ATTR_SRC_COMPAT == propertyName || SdkConstants.ATTR_SRC == propertyName)
    val dialog = ChooseResourceDialog.builder()
      .setModule(module)
      .setTypes(property.type.resourceTypes)
      .setCurrentValue(property.rawValue)
      .setTag(tag)
      .setDefaultType(defaultResourceType)
      .setFilterColorStateLists(isImageViewDrawable)
      .build()
    return if (dialog.showAndGet()) dialog.resourceName else null
  }

  /**
   * For some attributes, it make more sense the display a specific type by default.
   *
   * For example `textColor` has more chance to have a color value than a drawable value,
   * so in the [ChooseResourceDialog], we need to select the Color tab by default.
   *
   * @param propertyName The property name to get the associated default type from.
   * @return The [ResourceType] that should be selected by default for the provided property name.
   */
  private fun getDefaultResourceType(propertyName: String): ResourceType? {
    val lowerCaseProperty = propertyName.toLowerCase(Locale.getDefault())
    return when {
      lowerCaseProperty.contains("color") || lowerCaseProperty.contains("tint")
        -> ResourceType.COLOR
      lowerCaseProperty.contains("drawable") || propertyName == SdkConstants.ATTR_SRC || propertyName == SdkConstants.ATTR_SRC_COMPAT
        -> ResourceType.DRAWABLE
      else -> null
    }
  }
}
