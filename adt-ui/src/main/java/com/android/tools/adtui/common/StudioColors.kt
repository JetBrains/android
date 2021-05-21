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

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color

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
 * Color of the 3d lines in the transform panel of the layout editor
 */
val lines3d = JBColor.namedColor("UIDesigner.Panel.lines3d", JBColor(0x2D2D2D, 0x26A04A))

/**
 * Color of the graph lines in the transition panel of the layout editor
 */
val graphLines = JBColor.namedColor("UIDesigner.Panel.graphLines", JBColor(0x2D2D2D, 0xD9D9D9))

/**
 * Color of the graph lines in the transition panel of the layout editor
 */
val secondaryGraphLines = JBColor.namedColor("UIDesigner.Panel.secondaryGraphLines", JBColor(0x636363, 0x5F6265))

/**
 * Color of the graph lines in the transition panel of the layout editor
 */
val graphLabel = JBColor.namedColor("UIDesigner.Panel.graphLabel", JBColor(0x636363, 0x8A8A8A))

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
 * Color for textual content that is clickable.
 *
 * Example: text color of "Leak" button
 */
val linkForeground = JBUI.CurrentTheme.Link.linkColor()

/**
 * Background color for selected content.
 *
 * Example: selected range in profilers.
 */
val contentSelectionBackground = JBColor.namedColor("Content.selectionBackground",
                                                    JBColor(Color(0x330478DA, true), Color(0x4C2395F5, true)))

/**
 * Background color for deselected content.
 *
 * Example: box selection in profilers.
 */
val contentDeselectionBackground = JBColor.namedColor("Content.selectionInactiveBackground",
                                                      JBColor(Color(0x33121212, true), Color(0x33EDEDED, true)))

/**
 * Overlay background color for selected range
 *
 * Example: box selection overlay in profilers
 */
val selectionOverlayBackground = JBColor(Color(0x330478DA, true), Color(0x4C2395F5, true))

/**
 * Overlay background color for deselected content
 *
 * Example: box selection overlay in profilers
 */
val inactiveSelectionOverlayBackground = JBColor(Color(0x33121212, true), Color(0x33EDEDED, true))

/**
 * Background color for an active selection.
 *
 * Example: selected track in a track group.
 */
val selectionBackground = JBColor.namedColor("List.selectionBackground", JBColor(0x4874D7, 0x1E67CE))

/**
 * Color of the underline when a intellij style tab is focused.
 *
 * Example: Analysis tab of a cpu profiling capture.
 */
val tabbedPaneFocus = JBUI.CurrentTheme.TabbedPane.ENABLED_SELECTED_COLOR

/**
 * Color of the background when user mouse overs an intellij style tab.
 *
 * Example: Analysis tab of a cpu profiling capture.
 */
val tabbedPaneHoverHighlight = JBUI.CurrentTheme.TabbedPane.HOVER_COLOR

/**
 * Color of the text used to display help information.
 *
 * Example: Keyboard/mouse shortcut descriptions in Summary tab of a cpu profiling capture.
 */
val helpText = JBColor.namedColor("Editor.foreground", JBColor(Gray._80, Gray._160))