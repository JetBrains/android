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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

import com.intellij.ui.JBColor

object BackgroundTaskInspectorColors {
  // Table View
  @JvmField val ENTRY_SECTION_BACKGROUND_COLOR = JBColor(0xF2F2F2, 0x2B2B2B)

  // Details View
  val DATA_VALUE_TEXT_COLOR = JBColor(0x58AB5C, 0x65BB69)
  val DATA_TEXT_NULL_COLOR = JBColor(0x0033B3, 0xCC7832)
  val DATA_TEXT_AWAITING_COLOR = JBColor(0x787878, 0xC8C8C8)
  val EMPTY_CONTENT_COLOR = JBColor(0x787878, 0xC8C8C8)

  // Dependency Graph
  val DEFAULT_WORK_BORDER_COLOR =
    JBColor.namedColor("AppInspector.GraphNode.borderColor", JBColor(0xa7a7a7, 0x2d2f31))
  val SELECTED_WORK_BORDER_COLOR =
    JBColor.namedColor("AppInspector.GraphNode.focusedBorderColor", JBColor(0x1886f7, 0x9ccdff))
  val GRAPH_LABEL_BACKGROUND_COLOR =
    JBColor.namedColor("AppInspector.GraphNode.background", JBColor(0xfdfdfd, 0x515658))
}
