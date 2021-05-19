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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.DataKey
import kotlin.properties.Delegates

val DEVICE_VIEW_SETTINGS_KEY = DataKey.create<DeviceViewSettings>(DeviceViewSettings::class.qualifiedName!!)

private const val DRAW_BORDERS_KEY = "live.layout.inspector.draw.borders"
private const val SHOW_LAYOUT_BOUNDS_KEY = "live.layout.inspector.draw.layout"
private const val DRAW_LABEL_KEY = "live.layout.inspector.draw.label"

class DeviceViewSettings(scalePercent: Int = 100) {
  val modificationListeners = mutableListOf<() -> Unit>()

  /** Scale of the view in percentage: 100 = 100% */
  var scalePercent: Int by Delegates.observable(scalePercent) {
    _, _, _ -> modificationListeners.forEach { it() }
  }

  /** Scale of the view as a fraction: 1 = 100% */
  val scaleFraction: Double
    get() = scalePercent / 100.0

  var drawBorders: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(DRAW_BORDERS_KEY, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(DRAW_BORDERS_KEY, value, true)
      modificationListeners.forEach { it() }
    }

  var drawUntransformedBounds: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(SHOW_LAYOUT_BOUNDS_KEY, false)
    set(value) {
      PropertiesComponent.getInstance().setValue(SHOW_LAYOUT_BOUNDS_KEY, value, false)
      modificationListeners.forEach { it() }
    }

  var drawLabel: Boolean
    get() = PropertiesComponent.getInstance().getBoolean(DRAW_LABEL_KEY, true)
    set(value) {
      PropertiesComponent.getInstance().setValue(DRAW_LABEL_KEY, value, true)
      modificationListeners.forEach { it() }
    }
}