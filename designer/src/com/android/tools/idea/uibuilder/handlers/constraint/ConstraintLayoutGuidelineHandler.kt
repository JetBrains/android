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
package com.android.tools.idea.uibuilder.handlers.constraint

import com.android.SdkConstants.*
import com.android.tools.idea.common.model.NlComponent
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.google.common.collect.ImmutableList
import icons.StudioIcons
import javax.swing.Icon

/**
 * Handles interactions with a Guideline for a ConstraintLayout.
 */
class ConstraintLayoutGuidelineHandler : ViewHandler() {

  override fun getGradleCoordinateId(tagName: String): String = CONSTRAINT_LAYOUT_LIB_ARTIFACT

  override fun getIcon(component: NlComponent): Icon {
    if (!CONSTRAINT_LAYOUT_GUIDELINE.isEquals(component.tagName)) {
      return super.getIcon(component)
    }
    return if (isVertical(component)) {
      StudioIcons.LayoutEditor.Toolbar.VERTICAL_GUIDE
    }
    else {
      StudioIcons.LayoutEditor.Toolbar.HORIZONTAL_GUIDE
    }
  }

  companion object {
    @JvmStatic
    fun isVertical(component: NlComponent): Boolean {
      return ATTR_GUIDELINE_ORIENTATION_VERTICAL == component.resolveAttribute(ANDROID_URI, ATTR_ORIENTATION)
    }
  }

  override fun getInspectorProperties(): List<String> {
    return ImmutableList.of(LAYOUT_CONSTRAINT_GUIDE_BEGIN, LAYOUT_CONSTRAINT_GUIDE_END, LAYOUT_CONSTRAINT_GUIDE_PERCENT)
  }
}
