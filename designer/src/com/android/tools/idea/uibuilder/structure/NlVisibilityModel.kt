/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure

import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.handlers.constraint.ComponentModification
import com.intellij.openapi.actionSystem.Presentation
import icons.StudioIcons

/**
 * Helper model for component visibility.
 */
class NlVisibilityModel(val component: NlComponent) {

  enum class Visibility {
    NONE,
    VISIBLE,
    INVISIBLE,
    GONE;

    companion object {
      fun convert(str: String?): Visibility {
        return when (str) {
          "visible" -> Visibility.VISIBLE
          "invisible" -> Visibility.INVISIBLE
          "gone" -> Visibility.GONE
          else -> Visibility.NONE
        }
      }

      fun convert(visibility: Visibility): String? {
        return when (visibility) {
          NONE -> null
          VISIBLE -> "visible"
          INVISIBLE -> "invisible"
          GONE -> "gone"
        }
      }
    }
  }

  /** [SdkConstants.ANDROID_URI] visibility */
  val androidVisibility: Visibility get() = myAndroidVisibility

  /** [SdkConstants.TOOLS_URI] visibility */
  val toolsVisibility: Visibility get() = myToolsVisibility

  private var myAndroidVisibility: Visibility
  private var myToolsVisibility: Visibility

  init {
    val android = component.getAttribute(SdkConstants.ANDROID_URI, "visibility")
    val tools = component.getAttribute(SdkConstants.TOOLS_URI, "visibility")

    myAndroidVisibility = Visibility.convert(android)
    myToolsVisibility = Visibility.convert(tools)
  }

  /**
   * Returns visibility and whether the visibility is from tools attribute.
   * If tools attribute is not none, it returns tools attribute visibility and true
   * If tools attribute is none, it returns android attribute visibility and false
   */
  fun getCurrentVisibility(): Pair<Visibility, Boolean> {
    if (toolsVisibility != Visibility.NONE) {
      return Pair(toolsVisibility, true)
    }
    return Pair(androidVisibility, false)
  }

  /**
   * Update the visibility attribute in the component.
   */
  fun writeToComponent(
    visibility: Visibility,
    uri: String) {
    val modification = ComponentModification(component, "Update Visibility")
    when (visibility) {
      Visibility.NONE -> modification.setAttribute(uri, SdkConstants.ATTR_VISIBILITY, null)
      Visibility.VISIBLE -> modification.setAttribute(uri, SdkConstants.ATTR_VISIBILITY, "visible")
      Visibility.INVISIBLE -> modification.setAttribute(uri, SdkConstants.ATTR_VISIBILITY, "invisible")
      Visibility.GONE -> modification.setAttribute(uri, SdkConstants.ATTR_VISIBILITY, "gone")
    }

    if (SdkConstants.TOOLS_URI == uri) {
      myToolsVisibility = visibility
    } else if (SdkConstants.ANDROID_URI == uri) {
      myAndroidVisibility = visibility
    }

    modification.commit()
  }

  /**
   * Returns true if the component contains [visibility] && [uri] pair.
   * False otherwise.
   */
  fun contains(visibility: Visibility, uri: String): Boolean {
    if (SdkConstants.ANDROID_URI == uri) {
      return androidVisibility == visibility
    } else if (SdkConstants.TOOLS_URI == uri) {
      return toolsVisibility == visibility
    }
    return false
  }
}

/**
 * Update the presentation based on the visibility.
 */
fun updatePresentation(
  visibility: NlVisibilityModel.Visibility,
  isToolsAttr: Boolean,
  presentation: Presentation) {

  when (visibility) {
    NlVisibilityModel.Visibility.NONE -> {
      presentation.icon = StudioIcons.Common.REMOVE
      presentation.text = "Visibility not set"
    }
    NlVisibilityModel.Visibility.VISIBLE -> {
      presentation.icon = if (isToolsAttr)
        StudioIcons.LayoutEditor.Properties.VISIBLE_TOOLS_ATTRIBUTE else
        StudioIcons.LayoutEditor.Properties.VISIBLE
      presentation.text = "visible"
    }
    NlVisibilityModel.Visibility.INVISIBLE -> {
      presentation.icon = if (isToolsAttr)
        StudioIcons.LayoutEditor.Properties.INVISIBLE_TOOLS_ATTRIBUTE else
        StudioIcons.LayoutEditor.Properties.INVISIBLE
      presentation.text = "invisible"
    }
    NlVisibilityModel.Visibility.GONE -> {
      presentation.icon = if (isToolsAttr)
        StudioIcons.LayoutEditor.Properties.GONE_TOOLS_ATTRIBUTE else
        StudioIcons.LayoutEditor.Properties.GONE
      presentation.text = "gone"
    }
  }
  presentation.isEnabled = true
  presentation.isVisible = true
}
