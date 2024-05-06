/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.inspector

import com.android.tools.idea.uibuilder.property.getPropertiesToolContent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.ToggleAction

val neleDesignPropertySections =
  listOf(
    InspectorSection.DECLARED,
    InspectorSection.LAYOUT,
    InspectorSection.FAVORITES,
    InspectorSection.COMMON,
    InspectorSection.TRANSFORMS,
    InspectorSection.REFERENCES,
    InspectorSection.ALL
  )

enum class InspectorSection(val title: String) {
  DECLARED("Declared Attributes"),
  LAYOUT("Layout"),
  FAVORITES("Favorite Attributes"),
  COMMON("Common Attributes"),
  TRANSFORMS("Transforms"),
  TRANSITION("View Transition"), // MotionPropertyEditor only
  TRANSITION_MODIFIERS("Transition Modifiers"), // MotionPropertyEditor only
  REFERENCES("Referenced Views"),
  ALL("All Attributes");

  private val propertyName = "ANDROID.INSPECTOR-SECTION.$name"

  private val defaultValue
    get() = this != FAVORITES

  var visible: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(propertyName, defaultValue)
    set(value) {
      PropertiesComponent.getInstance().setValue(propertyName, value, defaultValue)
    }

  val action =
    object : ToggleAction(title) {
      override fun isSelected(event: AnActionEvent) = visible

      override fun setSelected(event: AnActionEvent, state: Boolean) {
        visible = !visible
        getPropertiesToolContent(event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
          ?.firePropertiesGenerated()
      }

      override fun update(event: AnActionEvent) {
        super.update(event)
        event.presentation.isVisible =
          getPropertiesToolContent(event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT))
            ?.isInspectorSectionsActive ?: false
      }
    }
}
