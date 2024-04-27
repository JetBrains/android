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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.handlers.frame.FrameLayoutHandler

class BottomNavigationViewHandler : FrameLayoutHandler() {

  override fun getInspectorProperties(): List<String> {
    return listOf(
      ATTR_STYLE,
      ATTR_ITEM_HORIZONTAL_TRANSLATION_ENABLED,
      ATTR_LABEL_VISIBILITY_MODE,
      ATTR_ITEM_ICON_TINT,
      ATTR_MENU,
      ATTR_ITEM_BACKGROUND,
      ATTR_ITEM_TEXT_COLOR,
      ATTR_ELEVATION
    )
  }
}
