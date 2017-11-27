/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.adtui.stdui

import com.intellij.ui.JBColor

import java.awt.*

/**
 * Standard UI color constants used in various components.
 */
object StandardColors {
  val INNER_BORDER_COLOR = JBColor(0xBEBEBE, 0x646464)
  val FOCUSED_INNER_BORDER_COLOR = JBColor(0x97C3F3, 0x5781C6)
  val FOCUSED_OUTER_BORDER_COLOR = JBColor(Color(0x7F97C3F3, true), Color(0x7F6297F6, true))
  val DISABLED_INNER_BORDER_COLOR = JBColor(Color(0x7FBEBEBE, true), Color(0x7F646464, true))
  val PLACEHOLDER_INNER_BORDER_COLOR = JBColor(Color(0x92BEBEBE.toInt(), true), Color(0x92646464.toInt(), true))
  val TEXT_COLOR = JBColor(0x1D1D1D, 0xBFBFBF)
  val SELECTED_TEXT_COLOR = JBColor(0x000000, 0xFFFFFF)
  val DISABLED_TEXT_COLOR = JBColor(Color(0x7F1D1D1D, true), Color(0x7FBFBFBF, true))
  val PLACEHOLDER_TEXT_COLOR = JBColor(Color(0x921D1D1D.toInt(), true), Color(0x92BFBFBF.toInt(), true))
  val BACKGROUND_COLOR = JBColor(Color(0xFFFFFF), Color(0x13FFFFFF, true))
  val SELECTED_BACKGROUND_COLOR = JBColor(0xA4CDFF, 0x2F65CA)
  val ERROR_INNER_BORDER_COLOR = JBColor(Color(0x7FFF0F0F, true), Color(0xC0FD7F7E.toInt(), true))
  val ERROR_OUTER_BORDER_COLOR = ERROR_INNER_BORDER_COLOR
  val DROPDOWN_ARROW_COLOR = JBColor(0x000000, 0xBFBFBF)
  val ERROR_BUBBLE_TEXT_COLOR = TEXT_COLOR
  val ERROR_BUBBLE_FILL_COLOR = JBColor(0xF5E6E7, 0x593D41)
  val ERROR_BUBBLE_BORDER_COLOR = JBColor(0xE0A8A9, 0x73454B)
}
