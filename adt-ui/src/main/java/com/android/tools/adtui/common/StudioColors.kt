/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.adtui.common

import com.intellij.ui.JBColor
import java.awt.Color

/**
 * Colors defined in the UX prototype
 */

/**
 * Background color for panels that have a primary role.
 *
 * Example: central panel of the layout editor.
 */
val primaryPanelBackground = JBColor(0xf5f5f5, 0x2D2F31)

/**
 * Background color for panels that have a secondary role
 *
 * Example: the palette or component tree in the layout editor.
 */
val secondaryPanelBackground = JBColor(0xfcfcfc, 0x313435)

/**
 * Color of the border that separates panels.
 *
 * Example : Between the component tree and the main panel of the layout editor
 */
val border: Color = JBColor(0xc9c9c9, 0x242627)

/**
 * Border color to use when separating element inside the same panel.
 *
 * Example: boder between the category list and widget list in the
 * layout editor's palette
 */
val borderLight: Color = JBColor(0xe8e6e6, 0x3c3f41)

