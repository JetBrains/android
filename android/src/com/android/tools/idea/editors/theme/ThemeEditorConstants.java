/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import java.awt.*;

public class ThemeEditorConstants {

  /** Scale of headers of attributes in the ThemeEditorTable */
  public static final float ATTRIBUTES_HEADER_FONT_SCALE = 1.3f;
  /** Scale of attribute font in the ThemeEditorTable */
  public static final float ATTRIBUTES_FONT_SCALE = 0.9f;

  /**
   * Color used to display resources values in the attributes table
   */
  public static final JBColor RESOURCE_ITEM_COLOR = new JBColor(new Color(0x6F6F6F)/*light*/, new Color(0xAAAAAA)/*dark*/);

  /** Label template for the selected variant in the variants combobox */
  public static final String CURRENT_VARIANT_TEMPLATE = "<html><nobr><font color=\"#%1$s\">%2$s";
  /** Label template for the not selected variant in the variants combobox */
  public static final String NOT_SELECTED_VARIANT_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s</font></b><font color=\"#9B9B9B\"> %3$s</font>";
  @SuppressWarnings("UseJBColor") // LIGHT_GRAY works also in Darcula
  public static final Color CURRENT_VARIANT_COLOR = Color.LIGHT_GRAY;
  @SuppressWarnings("UseJBColor")
  public static final Color NOT_SELECTED_VARIANT_COLOR = new Color(0x70ABE3);

  public static final Dimension ATTRIBUTES_PANEL_COMBO_MIN_SIZE = JBUI.size(0, 34);

  /** Attribute cell label template */
  public static final String ATTRIBUTE_LABEL_TEMPLATE = "<html><nobr><b><font color=\"#%1$s\">%2$s";
  /** Attribute cell gap between the label and the swatch */
  public static final int ATTRIBUTE_ROW_GAP = JBUI.scale(7);
  /** Attribute cell top + bottom margins */
  public static final int ATTRIBUTE_MARGIN = JBUI.scale(16);
}
