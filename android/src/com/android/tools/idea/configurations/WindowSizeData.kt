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
package com.android.tools.idea.configurations

import com.android.resources.Density
import com.android.resources.ScreenOrientation
import com.android.tools.configurations.DEVICE_CLASS_DESKTOP_ID
import com.android.tools.configurations.DEVICE_CLASS_FOLDABLE_ID
import com.android.tools.configurations.DEVICE_CLASS_PHONE_ID
import com.android.tools.configurations.DEVICE_CLASS_TABLET_ID

data class WindowSizeData(val id: String, val name: String, val widthDp: Double, val heightDp: Double, val density: Density,
                          val defaultOrientation: ScreenOrientation) {
  val widthPx: Int = widthDp.toPx(density)
  val heightPx: Int = heightDp.toPx(density)
}

/**
 * The device definitions used by Android Studio only
 */
val PREDEFINED_WINDOW_SIZES_DEFINITIONS: List<WindowSizeData> = listOf(
  WindowSizeData(DEVICE_CLASS_PHONE_ID, "Medium Phone", 411.0, 891.0, Density.create(420), ScreenOrientation.PORTRAIT),
  WindowSizeData(DEVICE_CLASS_FOLDABLE_ID, "Foldable", 673.0, 841.0, Density.create(420), ScreenOrientation.PORTRAIT),
  WindowSizeData(DEVICE_CLASS_TABLET_ID, "Medium Tablet", 1280.0, 800.0, Density.HIGH, ScreenOrientation.LANDSCAPE),
  WindowSizeData(DEVICE_CLASS_DESKTOP_ID, "Desktop", 1920.0, 1080.0, Density.MEDIUM, ScreenOrientation.LANDSCAPE)
)