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

import com.android.emulator.control.DisplayConfiguration
import java.awt.Dimension

internal data class DisplayDescriptor(
  var displayId: Int,
  var width: Int,
  var height: Int,
  var orientation: Int = 0,
  var type: Type = Type.UNKNOWN
) : Comparable<DisplayDescriptor> {

  constructor(displayConfig: DisplayConfiguration) : this(displayConfig.display, displayConfig.width, displayConfig.height)

  constructor(displayId: Int, size: Dimension) : this(displayId, size.width, size.height)

  @Suppress("unused") // Used by XML deserializer.
  constructor() : this(0, 0, 0)

  val size
    get() = Dimension(width, height)

  override fun compareTo(other: DisplayDescriptor): Int {
    return displayId - other.displayId
  }

  /** Values correspond to the TYPE_* constants in android.view.Display */
  enum class Type { UNKNOWN, INTERNAL, EXTERNAL, WIFI, OVERLAY, VIRTUAL }
}