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
package com.android.tools.idea.uibuilder.property2.model

import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager
import com.android.tools.idea.uibuilder.model.hasNlComponentInfo
import com.android.tools.idea.uibuilder.property2.NelePropertyItem
import com.intellij.util.text.nullize
import icons.StudioIcons
import javax.swing.Icon

private const val UNNAMED_COMPONENT = "<unnamed>"
private const val MULTIPLE_COMPONENTS = "<multiple>"

/**
 * Model for supplying data to a SelectedComponentPanel.
 *
 * For displaying which component is being edited.
 * Intended for being shown at the top of the properties panel.
 */
class SelectedComponentModel(
  private val property: NelePropertyItem?,
  private val components: List<NlComponent>,
  val elementDescription: String) {

  val componentIcon: Icon?
    get() = findComponentIcon()

  val componentName: String
    get() {
      return when (components.size) {
        1 -> property?.value.nullize() ?: UNNAMED_COMPONENT
        else -> MULTIPLE_COMPONENTS
      }
    }

  private fun findComponentIcon(): Icon? {
    if (components.size != 1) {
      // TODO: Get another icon for multiple components
      return StudioIcons.LayoutEditor.Palette.VIEW_SWITCHER
    }

    return components[0].mixin?.icon ?: StudioIcons.LayoutEditor.Palette.VIEW
  }
}
