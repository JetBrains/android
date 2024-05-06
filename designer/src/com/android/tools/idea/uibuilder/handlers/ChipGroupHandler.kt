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
import com.android.tools.idea.uibuilder.api.ViewGroupHandler

class ChipGroupHandler : ViewGroupHandler() {

  override fun getInspectorProperties(): List<String> {
    return listOf(
      ATTR_STYLE,
      ATTR_CHIP_SPACING,
      ATTR_CHIP_SPACING_HORIZONTAL,
      ATTR_CHIP_SPACING_VERTICAL,
      ATTR_SINGLE_LINE,
      ATTR_SINGLE_SELECTION,
      ATTR_CHECKED_CHIP
    )
  }
}
