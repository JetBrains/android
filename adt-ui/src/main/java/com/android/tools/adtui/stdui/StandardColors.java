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

import com.android.tools.adtui.common.StudioColorsKt;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import java.awt.Color;

/**
 * Standard UI color constants used in various components.
 */
public final class StandardColors {
  public static final Color INNER_BORDER_COLOR = new JBColor(0xBEBEBE, 0x646464);
  public static final Color FOCUSED_INNER_BORDER_COLOR = new JBColor(0x90AADC, 0x5781C6);
  public static final Color FOCUSED_OUTER_BORDER_COLOR = new JBColor(0xB2CCFB, 0x395D82);
  public static final Color DISABLED_INNER_BORDER_COLOR = new JBColor(0xDFDFDF, 0x484848);
  public static final Color PLACEHOLDER_INNER_BORDER_COLOR = new JBColor(0xDFDFDF, 0x484848);
  public static final Color TEXT_COLOR = new JBColor(0x1D1D1D, 0xBFBFBF);
  public static final Color INACTIVE_TEXT_COLOR = new JBColor(0x737373, 0x8A8C8D);
  public static final Color SELECTED_TEXT_COLOR = new JBColor(0x000000, 0xFFFFFF);
  public static final Color DISABLED_TEXT_COLOR = new JBColor(0x8E8E8E, 0x757575);
  public static final Color PLACEHOLDER_TEXT_COLOR = new JBColor(0x7F7F7F, 0x8A8C8C);
  public static final Color BACKGROUND_COLOR = new JBColor(0xFFFFFF, 0x45494A);
  public static final Color MENU_BACKGROUND_COLOR = new JBColor(new Color(0xFFFFFF), new Color(0x313335));
  public static final Color SELECTED_BACKGROUND_COLOR = new JBColor(0xA4CDFF, 0x2F65CA);
  public static final Color ERROR_INNER_BORDER_COLOR = new JBColor(0xFF8787, 0xC86969);
  public static final Color ERROR_OUTER_BORDER_COLOR = ERROR_INNER_BORDER_COLOR;
  public static final Color DROPDOWN_ARROW_COLOR = new JBColor(0x000000, 0xBFBFBF);
  public static final Color TAB_HOVER_COLOR = new JBColor(0xd3d3d3, 0x323232);
  public static final Color TAB_SELECTED_COLOR = new JBColor(0x397FE4, 0x7CAEFE);
  public static final Color TAB_BORDER_COLOR = new JBColor(Gray._201, Gray._40);
  public static final Color HOVER_COLOR = new JBColor(new Color(0x171650C5, true), new Color(0x0CFFFFFF, true));
  public static final Color DEFAULT_CONTENT_BACKGROUND_COLOR = StudioColorsKt.getPrimaryContentBackground();
  public static final Color AXIS_MARKER_COLOR = Gray._150.withAlpha(50);

  public static final Color ERROR_BUBBLE_TEXT_COLOR = TEXT_COLOR;
  public static final Color ERROR_BUBBLE_FILL_COLOR = new JBColor(0xF5E6E7, 0x593D41);
  public static final Color ERROR_BUBBLE_BORDER_COLOR = new JBColor(0xE0A8A9, 0x73454B);
}
