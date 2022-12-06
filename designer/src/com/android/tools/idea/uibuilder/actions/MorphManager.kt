/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.actions

import com.android.AndroidXConstants
import com.android.SdkConstants
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.model.isOrHasSuperclass
import com.android.tools.idea.util.mapAndroidxName

/**
 * This class returns suggestion of possible conversion for a given [NlComponent]
 */
object MorphManager {
  fun getMorphSuggestion(component: NlComponent): List<String> {
    val suggestions: MutableList<String>
    if (component.isOrHasSuperclass(SdkConstants.CLASS_VIEWGROUP)) {
      val module = component.model.module
      suggestions = mutableListOf(
        module.mapAndroidxName(AndroidXConstants.CONSTRAINT_LAYOUT),
        SdkConstants.LINEAR_LAYOUT,
        module.mapAndroidxName(AndroidXConstants.COORDINATOR_LAYOUT),
        SdkConstants.RELATIVE_LAYOUT,
        SdkConstants.FRAME_LAYOUT)
    }
    else {
      suggestions = mutableListOf(
        SdkConstants.BUTTON,
        SdkConstants.IMAGE_VIEW,
        SdkConstants.TEXT_VIEW,
        SdkConstants.EDIT_TEXT,
        SdkConstants.CHECK_BOX,
        SdkConstants.RADIO_BUTTON,
        SdkConstants.TOGGLE_BUTTON)
    }

    // Ensure that the component for which we get the suggestion is the first one in the list
    val currentComponentIndex = suggestions.indexOf(component.tagName)
    if (currentComponentIndex > 1) {
      val current = suggestions[currentComponentIndex]
      suggestions.removeAt(currentComponentIndex)
      suggestions.add(0, current)
    }

    return suggestions
  }
}
