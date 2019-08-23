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
import javax.swing.UIManager

/**
 * Colors defined in the UX prototype
 */

/**
 * Background color for panels that have a primary role.
 *
 * Example: central panel of the layout editor.
 */
val primaryPanelBackground = JBColor.namedColor("UIDesigner.Canvas.background", JBColor(0xf5f5f5, 0x2D2F31))

/**
 * Background color for panels that have a secondary role
 *
 * Example: the palette or component tree in the layout editor.
 */
val secondaryPanelBackground = JBColor.namedColor("UIDesigner.Panel.background", JBColor(0xfcfcfc, 0x313435))

/**
 * Color of the border that separates panels.
 *
 * Example : Between the component tree and the main panel of the layout editor
 */
val border = JBColor.namedColor("UIDesigner.Panel.borderColor", JBColor(0xc9c9c9, 0x282828))

/**
 * Border color to use when separating element inside the same panel.
 *
 * Example: border between the category list and widget list in the
 * layout editor's palette
 */
val borderLight = JBColor.namedColor("Canvas.Tooltip.borderColor", JBColor(0xD9D9D9, 0x4A4A4A))

/**
 * Background color for tooltips on canvases
 *
 * Example: Hover tooltips for chart data points, tooltips on designer surfaces
 */
val canvasTooltipBackground = JBColor.namedColor("Canvas.Tooltip.background", JBColor(0xf7f7f7, 0x4A4C4C))

/**
 * Background color for content (same background colors as Editors)
 *
 * Example: Background for charts, editors
 */
val primaryContentBackground = JBColor.namedColor("Content.background", JBColor(0xffffff, 0x2b2b2b))

/**
 * Color of the underline when a intellij style tab is focused.
 *
 * Example: Analysis tab of a cpu profiling capture.
 */
val tabbedPaneFocus = UIManager.getColor("TabbedPane.focus")

/**
 * Color of the background when user mouse overs an intellij style tab.
 *
 * Example: Analysis tab of a cpu profiling capture.
 */
val tabbedPaneHoverHighlight = UIManager.getColor("TabbedPane.shadow")