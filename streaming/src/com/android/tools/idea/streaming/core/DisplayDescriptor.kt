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

import java.awt.Dimension

/** XML-serializable descriptor of a device display. */
internal data class DisplayDescriptor(
  var displayId: Int,
  var width: Int,
  var height: Int,
  var orientation: Int = 0,
  var type: DisplayType = DisplayType.UNKNOWN
) : Comparable<DisplayDescriptor> {

  constructor(displayId: Int, size: Dimension, orientation: Int = 0, type: DisplayType = DisplayType.UNKNOWN) :
      this(displayId, size.width, size.height, orientation, type)

  @Suppress("unused") // Used by XML deserializer.
  constructor() : this(0, 0, 0)

  val size
    get() = Dimension(width, height)

  override fun compareTo(other: DisplayDescriptor): Int {
    return displayId - other.displayId
  }
}
