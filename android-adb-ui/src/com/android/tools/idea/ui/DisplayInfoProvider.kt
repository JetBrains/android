/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.ui

import com.android.tools.adtui.device.SkinDefinition
import java.awt.Dimension

/** Provides information about displays of an Android device. */
interface DisplayInfoProvider {

  /** Returns logical IDs of all device displays. */
  fun getIdsOfAllDisplays(): IntArray

  /**
   * Returns size in pixels of the display with the given logical ID. A zero returned size is
   * an indication that the display size is not yet known.
   *
   * @throws IllegalArgumentException if the display does not exist.
   */
  fun getDisplaySize(displayId: Int): Dimension

  /**
   * Returns orientation in quadrants of the display with the given logical ID.
   *
   * @throws IllegalArgumentException if the display does not exist.
   */
  fun getDisplayOrientation(displayId: Int): Int

  /**
   * Returns the additional rotation in quadrants that has to be applied to display screenshots
   * to make them match the display orientation.
   *
   * @throws IllegalArgumentException if the display does not exist.
   */
  fun getScreenshotRotation(displayId: Int): Int

  /**
   * Returns the skin for the display.
   *
   * @throws IllegalArgumentException if the display does not exist.
   */
  fun getSkin(displayId: Int): SkinDefinition?
}
