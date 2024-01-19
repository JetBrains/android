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
package com.android.tools.idea.uibuilder.handlers

import com.android.SdkConstants.*
import com.android.tools.idea.uibuilder.api.ViewHandler
import com.google.common.collect.ImmutableList

class ChipHandler : ViewHandler() {

  override fun getInspectorProperties(): List<String> {
    return ImmutableList.of(
      ATTR_STYLE,
      ATTR_CHECKABLE,
      ATTR_TEXT,
      ATTR_CHIP_ICON,
      ATTR_CHIP_ICON_VISIBLE,
      ATTR_CHECKED_ICON,
      ATTR_CHECKED_ICON_VISIBLE,
      ATTR_CLOSE_ICON,
      ATTR_CLOSE_ICON_VISIBLE,
    )
  }

  override fun getBaseStyles(tagName: String): List<String> {
    return listOf("app:Base.Widget.MaterialComponents.${getSimpleTagName(tagName)}")
  }
}
