/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.network.view.constants

import com.intellij.util.ui.JBUI
import javax.swing.border.Border

/** Common length for spacing between axis tick markers */
val MARKER_LENGTH = JBUI.scale(5)

val TIME_AXIS_HEIGHT = JBUI.scale(15)

/** Common space left on top of a vertical axis to make sure label text can fit there */
val Y_AXIS_TOP_MARGIN = JBUI.scale(30)

val MONITOR_LABEL_PADDING: Border = JBUI.Borders.empty(5, 10)

val MONITOR_BORDER: Border = JBUI.Borders.customLineBottom(MONITOR_BORDER_COLOR)

val LEGEND_RIGHT_PADDING = JBUI.scale(9)

val ROW_HEIGHT_PADDING = JBUI.scale(4)

val TOOLTIP_BORDER: Border = JBUI.Borders.empty(8, 10, 8, 10)

// The total usable height of the toolbar is 30px the 1px is for a 1px border at the bottom of the
// toolbar.
val TOOLBAR_HEIGHT = JBUI.scale(31)
