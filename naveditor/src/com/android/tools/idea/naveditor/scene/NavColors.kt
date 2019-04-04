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
  val FRAME = JBColor(0xa7a7a7, 0x2d2f31)
  @JvmField
  val HIGHLIGHTED_FRAME = JBColor(0xa7a7a7, 0xa1a1a1)
  @JvmField
  val BACKGROUND = JBColor(0xf5f5f5, 0x2d2f31)
  @JvmField
  val SUBDUED_BACKGROUND = JBColor(0xfcfcfc, 0x313435)
  val COMPONENT_BACKGROUND = JBColor(0xfafafa, 0x515658)
  val TEXT = JBColor(0xa7a7a7, 0x888888)
  val SUBDUED_TEXT = JBColor(0x656565, 0xbababb)
  val ACTION = JBColor(Color(-0x4d585859, true), Color(-0x4d777778, true))
  val HIGHLIGHTED_ACTION = JBColor(0xa7a7a7, 0x888888)
  val ACTIVITY_BORDER = JBColor(0xa7a7a7, 0x2d2f31)
  // Note that this must match the highlight color of ActionButtonWithText
  val LIST_MOUSEOVER = JBColor(Gray.xDB, Color(0x55595c))
  val PLACEHOLDER_BORDER = JBColor(0xcccccc, 0x3f4244)
  val PLACEHOLDER_TEXT = JBColor(0xcccccc, 0x888888)
  val PLACEHOLDER_BACKGROUND = JBColor(0xfdfdfd, 0x515658)
  @JvmField
  val SELECTED = JBColor(0x1886f7, 0x9ccdff)
}