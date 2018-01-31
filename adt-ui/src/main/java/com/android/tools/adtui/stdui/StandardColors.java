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
package com.android.tools.adtui.stdui;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;

import java.awt.*;

/**
 * Standard UI color constants used in various components.
 */
public class StandardColors {
  public static Color INNER_BORDER_COLOR = new JBColor(0xBEBEBE, 0x646464);
  public static Color FOCUSED_INNER_BORDER_COLOR = new JBColor(0x97C3F3, 0x5781C6);
  public static Color FOCUSED_OUTER_BORDER_COLOR = new JBColor(new Color(0x7F97C3F3, true), new Color(0x7F6297F6, true));
  public static Color DISABLED_INNER_BORDER_COLOR = new JBColor(new Color(0x7FBEBEBE, true), new Color(0x7F646464, true));
  public static Color PLACEHOLDER_INNER_BORDER_COLOR = new JBColor(new Color(0x92BEBEBE, true), new Color(0x92646464, true));
  public static Color TEXT_COLOR = new JBColor(0x1D1D1D, 0xBFBFBF);
  public static Color SELECTED_TEXT_COLOR = new JBColor(0x000000, 0xFFFFFF);
  public static Color DISABLED_TEXT_COLOR = new JBColor(new Color(0x7F1D1D1D, true), new Color(0x7FBFBFBF, true));
  public static Color PLACEHOLDER_TEXT_COLOR = new JBColor(new Color(0x921D1D1D, true), new Color(0x92BFBFBF, true));
  public static Color BACKGROUND_COLOR = new JBColor(new Color(0xFFFFFF), new Color(0x13FFFFFF, true));
  public static Color MENU_BACKGROUND_COLOR = new JBColor(new Color(0xFFFFFF), new Color(0x313335));
  public static Color SELECTED_BACKGROUND_COLOR = new JBColor(0xA4CDFF, 0x2F65CA);
  public static Color ERROR_INNER_BORDER_COLOR = new JBColor(new Color(0x7FFF0F0F, true), new Color(0xC0FD7F7E, true));
  public static Color ERROR_OUTER_BORDER_COLOR = ERROR_INNER_BORDER_COLOR;
  public static Color DROPDOWN_ARROW_COLOR = new JBColor(0x000000, 0xBFBFBF);
  public static Color TAB_HOVER_COLOR = new JBColor(0xd3d3d3, 0x323232);
  public static Color TAB_SELECTED_COLOR = new JBColor(0x397FE4, 0x7CAEFE);
  public static Color TAB_BORDER_COLOR = new JBColor(Gray._201, Gray._40);

  public static Color ERROR_BUBBLE_TEXT_COLOR = TEXT_COLOR;
  public static Color ERROR_BUBBLE_FILL_COLOR = new JBColor(0xF5E6E7, 0x593D41);
  public static Color ERROR_BUBBLE_BORDER_COLOR = new JBColor(0xE0A8A9, 0x73454B);
}
