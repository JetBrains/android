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
package com.android.tools.idea.naveditor.scene

import com.intellij.ui.Gray
import com.intellij.ui.JBColor
import java.awt.Color

object NavColors {
  val FRAME = JBColor.namedColor(
    "UIDesigner.Component.borderColor", JBColor(0xa7a7a7, 0x2d2f31))
  val HIGHLIGHTED_FRAME = JBColor.namedColor(
    "UIDesigner.Component.hoverBorderColor", JBColor(0xa7a7a7, 0xa1a1a1))
  val COMPONENT_BACKGROUND = JBColor.namedColor(
    "UIDesigner.Component.background", JBColor(0xfafafa, 0x515658))
  val TEXT = JBColor.namedColor(
    "UIDesigner.Component.foreground", JBColor(0xa7a7a7, 0x888888))
  val SUBDUED_TEXT = JBColor.namedColor(
    "UIDesigner.Label.foreground", JBColor(0x656565, 0xbababb))
  val ACTION = JBColor(0xdfe1e5, 0x43454a)
  val HIGHLIGHTED_ACTION = JBColor.namedColor(
    "UIDesigner.Connector.hoverBorderColor", JBColor(0xa7a7a7, 0x888888))
  val ACTIVITY_BORDER = JBColor.namedColor(
    "UIDesigner.Activity.borderColor", JBColor(0xa7a7a7, 0x2d2f31))
  // Note that this must match the highlight color of ActionButtonWithText
  val LIST_MOUSEOVER = JBColor.namedColor(
    "UIDesigner.List.selectionBackground", JBColor(Gray.xDB, Color(0x36393a)))
  val PLACEHOLDER_BORDER = JBColor.namedColor(
    "UIDesigner.Placeholder.borderColor", JBColor(0xcccccc, 0x3f4244))
  val PLACEHOLDER_TEXT = JBColor.namedColor(
    "UIDesigner.Placeholder.foreground", JBColor(0xcccccc, 0x888888))
  val PLACEHOLDER_BACKGROUND = JBColor.namedColor(
    "UIDesigner.Placeholder.background", JBColor(0xfdfdfd, 0x515658))
  val SELECTED = JBColor.namedColor(
    "UIDesigner.Placeholder.selectedForeground", JBColor(0x1886f7, 0x9ccdff))
}