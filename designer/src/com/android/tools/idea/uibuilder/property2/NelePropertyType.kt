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
package com.android.tools.idea.uibuilder.property2

import com.android.resources.ResourceType
import java.util.*

/**
 * Types of a [NelePropertyItem].
 */
enum class NelePropertyType {
  BOOLEAN,
  COLOR,
  COLOR_OR_DRAWABLE,
  DIMENSION,
  FLOAT,
  FONT_SIZE,
  FRACTION,
  FRAGMENT,
  ID,
  INTEGER,
  LAYOUT,
  LIST,
  READONLY_STRING,
  STRING,
  STYLE,
  TEXT_APPEARANCE;

  val resourceTypes: EnumSet<ResourceType>
    get() = when (this) {
      NelePropertyType.BOOLEAN -> EnumSet.of(ResourceType.BOOL)
      NelePropertyType.COLOR -> EnumSet.of(ResourceType.COLOR)
      NelePropertyType.COLOR_OR_DRAWABLE -> EnumSet.of(ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP)
      NelePropertyType.DIMENSION -> EnumSet.of(ResourceType.DIMEN)
      NelePropertyType.FLOAT -> EnumSet.of(ResourceType.DIMEN)
      NelePropertyType.FRACTION -> EnumSet.of(ResourceType.FRACTION)
      NelePropertyType.FONT_SIZE -> EnumSet.of(ResourceType.DIMEN)
      NelePropertyType.ID -> EnumSet.of(ResourceType.ID)
      NelePropertyType.INTEGER -> EnumSet.of(ResourceType.INTEGER)
      NelePropertyType.LIST -> EnumSet.noneOf(ResourceType.ID.javaClass)
      NelePropertyType.READONLY_STRING -> EnumSet.noneOf(ResourceType.ID.javaClass)
      NelePropertyType.STRING -> EnumSet.of(ResourceType.STRING)
      NelePropertyType.STYLE -> EnumSet.of(ResourceType.STYLE)
      NelePropertyType.TEXT_APPEARANCE -> EnumSet.of(ResourceType.STYLE)
      else -> EnumSet.noneOf(ResourceType.BOOL.javaClass)
    }
}
