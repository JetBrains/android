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
package com.android.tools.idea.layoutinspector.pipeline.legacy

import com.android.SdkConstants

const val GRAVITY_CENTER_VERTICAL = 0x10
const val GRAVITY_TOP = 0x30
const val GRAVITY_BOTTOM = 0x50
const val GRAVITY_FILL_VERTICAL = 0x70

const val GRAVITY_CENTER_HORIZONTAL = 0x01
const val GRAVITY_LEFT = 0x03
const val GRAVITY_RIGHT = 0x05
const val GRAVITY_FILL_HORIZONTAL = 0x07

const val GRAVITY_CENTER = 0x11
const val GRAVITY_FILL = 0x77

const val GRAVITY_CLIP_HORIZONTAL = 0x08
const val GRAVITY_CLIP_VERTICAL = 0x80

const val GRAVITY_RTL_FLAG = 0x800000
const val GRAVITY_START = 0x800003
const val GRAVITY_END = 0x800005

/** Utility for mapping an integer gravity value to a user readable string. */
class GravityIntMapping {
  private val intMapping = IntFlagMapping()

  fun fromIntValue(value: Int): Set<String> {
    val values = intMapping.of(value)
    if ((value and GRAVITY_RTL_FLAG) != 0) {
      if (values.remove(SdkConstants.GRAVITY_VALUE_LEFT)) {
        values.add(SdkConstants.GRAVITY_VALUE_START)
      }
      if (values.remove(SdkConstants.GRAVITY_VALUE_RIGHT)) {
        values.add(SdkConstants.GRAVITY_VALUE_END)
      }
    }
    return values
  }

  init {
    intMapping.add(GRAVITY_FILL, GRAVITY_FILL, SdkConstants.GRAVITY_VALUE_FILL)

    intMapping.add(
      GRAVITY_FILL_VERTICAL,
      GRAVITY_FILL_VERTICAL,
      SdkConstants.GRAVITY_VALUE_FILL_VERTICAL,
    )
    intMapping.add(GRAVITY_FILL_VERTICAL, GRAVITY_TOP, SdkConstants.GRAVITY_VALUE_TOP)
    intMapping.add(GRAVITY_FILL_VERTICAL, GRAVITY_BOTTOM, SdkConstants.GRAVITY_VALUE_BOTTOM)

    intMapping.add(
      GRAVITY_FILL_HORIZONTAL,
      GRAVITY_FILL_HORIZONTAL,
      SdkConstants.GRAVITY_VALUE_FILL_HORIZONTAL,
    )
    intMapping.add(GRAVITY_FILL_HORIZONTAL, GRAVITY_LEFT, SdkConstants.GRAVITY_VALUE_LEFT)
    intMapping.add(GRAVITY_FILL_HORIZONTAL, GRAVITY_RIGHT, SdkConstants.GRAVITY_VALUE_RIGHT)

    intMapping.add(GRAVITY_FILL, GRAVITY_CENTER, SdkConstants.GRAVITY_VALUE_CENTER)
    intMapping.add(
      GRAVITY_FILL_VERTICAL,
      GRAVITY_CENTER_VERTICAL,
      SdkConstants.GRAVITY_VALUE_CENTER_VERTICAL,
    )
    intMapping.add(
      GRAVITY_FILL_HORIZONTAL,
      GRAVITY_CENTER_HORIZONTAL,
      SdkConstants.GRAVITY_VALUE_CENTER_HORIZONTAL,
    )

    intMapping.add(
      GRAVITY_CLIP_VERTICAL,
      GRAVITY_CLIP_VERTICAL,
      SdkConstants.GRAVITY_VALUE_CLIP_VERTICAL,
    )
    intMapping.add(
      GRAVITY_CLIP_HORIZONTAL,
      GRAVITY_CLIP_HORIZONTAL,
      SdkConstants.GRAVITY_VALUE_CLIP_HORIZONTAL,
    )
  }
}
