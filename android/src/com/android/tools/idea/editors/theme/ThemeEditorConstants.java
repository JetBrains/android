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

import java.awt.Color;

public class ThemeEditorConstants {
  public static final int ROUNDED_BORDER_ARC_SIZE = 10;

  /**
   * Color used to display resources values in the attributes table
   */
  public static final JBColor RESOURCE_ITEM_COLOR = new JBColor(new Color(0x6F6F6F)/*light*/, new Color(0xAAAAAA)/*dark*/);
}
