/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.adtui.common;

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.UIUtil;

import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Font;

/**
 * ADT-UI utility class to hold constants and function used across the ADT-UI framework.
 */
public final class AdtUIUtils {

  /**
   * Default font to be used in the profiler UI.
   */
  public static final JBFont DEFAULT_FONT = JBFont.create(new Font(null, Font.PLAIN, 10));

  /**
   * Default font color of charts, and component labels.
   */
  public static final Color DEFAULT_FONT_COLOR = JBColor.foreground();

  public static final Color DEFAULT_BORDER_COLOR = new JBColor(Gray._96, Gray._192);

  public static final Color GRID_COLOR = new JBColor(Gray._192, Gray._96);

  public static final Color SELECTION_HANDLE = JBColor.GRAY;

  // TODO need Darcula color.
  public static final Color SELECTION_FOREGROUND = new JBColor(new Color(0x88aae2), new Color(0x88aae2));

  // TODO need Darcula color.
  public static final Color SELECTION_BACKGROUND = new JBColor(new Color(0x5588aae2, true), new Color(0x5588aae2, true));

  public static final Color OVERLAY_INFO_BACKGROUND = JBColor.WHITE;


  private AdtUIUtils() {
  }
}
