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
package com.android.tools.idea.preview

import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import java.awt.Color

object Colors {

  /**
   * Default background used by the surface. This is used to restore the state after exiting the
   * active modes.
   */
  val DEFAULT_BACKGROUND_COLOR = JBColor(0xFFFFFF, 0x1E1F22)

  /**
   * Background color for the surface while some of the active modes are enabled - for example,
   * Interactive Preview, Animation Preview, UI Check.
   */
  @Suppress("UnstableApiUsage")
  val ACTIVE_BACKGROUND_COLOR: Color =
    if (ExperimentalUI.isNewUI()) JBColor.PanelBackground else JBColor(0xCBD2D9, 0x46454D)
}
