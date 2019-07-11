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

import com.android.resources.ResourceType;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import java.awt.*;
import java.util.EnumSet;

public class ThemeEditorConstants {

  /** Scale of attribute font in the ThemeEditorTable */
  public static final float ATTRIBUTES_FONT_SCALE = 0.9f;

  /**
   * Color used to display resources values in the attributes table
   */
  public static final JBColor RESOURCE_ITEM_COLOR = new JBColor(new Color(0x6F6F6F)/*light*/, new Color(0xAAAAAA)/*dark*/);

  /** Attribute cell gap between the label and the swatch */
  public static final int ATTRIBUTE_ROW_GAP = JBUI.scale(7);
  /** Attribute cell top + bottom margins */
  public static final int ATTRIBUTE_MARGIN = JBUI.scale(16);

  /*
   * Definitions of which resource types belong into the colors, drawables or both categories
   */
  public static final EnumSet<ResourceType> COLORS_ONLY = EnumSet.of(ResourceType.COLOR);
  public static final EnumSet<ResourceType> DRAWABLES_ONLY = EnumSet.of(ResourceType.DRAWABLE, ResourceType.MIPMAP);
  public static final EnumSet<ResourceType> COLORS_AND_DRAWABLES = EnumSet.of(ResourceType.COLOR, ResourceType.DRAWABLE, ResourceType.MIPMAP);
}
