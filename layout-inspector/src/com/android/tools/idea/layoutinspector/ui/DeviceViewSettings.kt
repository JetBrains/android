/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector.ui

import icons.StudioIcons
import javax.swing.Icon
import kotlin.properties.Delegates

enum class ViewMode(val icon: Icon) {
  FIXED(StudioIcons.LayoutEditor.Extras.ROOT_INLINE),
  X_ONLY(StudioIcons.DeviceConfiguration.SCREEN_WIDTH),
  XY(StudioIcons.DeviceConfiguration.SMALLEST_SCREEN_SIZE);

  val next: ViewMode
    get() = enumValues<ViewMode>()[(this.ordinal + 1).rem(enumValues<ViewMode>().size)]
}

class DeviceViewSettings(scalePercent: Int = 100, drawBorders: Boolean = true, viewMode: ViewMode = ViewMode.XY) {
  val modificationListeners = mutableListOf<() -> Unit>()

  /** Scale of the view in percentage: 100 = 100% */
  var scalePercent: Int by Delegates.observable(scalePercent) {
    _, _, _ -> modificationListeners.forEach { it() }
  }

  /** Scale of the view as a fraction: 1 = 100% */
  val scaleFraction: Double
    get() = scalePercent / 100.0

  var drawBorders: Boolean by Delegates.observable(drawBorders) {
    _, _, _ -> modificationListeners.forEach { it() }
  }

  var viewMode: ViewMode by Delegates.observable(viewMode) {
    _, _, _ -> modificationListeners.forEach { it() }
  }
}