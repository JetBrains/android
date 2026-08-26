/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.streaming.core

import com.android.tools.adtui.util.toWxH
import com.intellij.util.xmlb.Converter
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Tag
import java.awt.Dimension

/** XML-serializable descriptor of a device display. */
@Tag("display")
internal data class DisplayDescriptor(
  @Attribute var displayId: Int,
  @Attribute(converter = DimensionConverter::class) var size: Dimension,
  @Attribute var orientation: Int = 0,
  @Attribute var type: DisplayType = DisplayType.UNKNOWN,
) : Comparable<DisplayDescriptor> {

  @Suppress("unused") // Used by XML deserializer.
  constructor() : this(0, Dimension())

  override fun compareTo(other: DisplayDescriptor): Int {
    return displayId - other.displayId
  }

  class DimensionConverter : Converter<Dimension>() {
    override fun fromString(value: String): Dimension? {
      val parts = value.split('x', 'X')
      if (parts.size != 2) return null
      val width = parts[0].trim().toIntOrNull() ?: return null
      val height = parts[1].trim().toIntOrNull() ?: return null
      return Dimension(width, height)
    }

    override fun toString(value: Dimension): String = value.toWxH()
  }
}
