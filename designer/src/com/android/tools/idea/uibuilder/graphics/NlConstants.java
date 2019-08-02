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
package com.android.tools.idea.uibuilder.graphics;

import com.android.tools.adtui.common.SwingCoordinate;
import com.intellij.ui.JBColor;
import java.awt.BasicStroke;
import java.awt.Color;

@SuppressWarnings("UseJBColor")
public class NlConstants {
  public static final int RULER_SIZE_PX = 20;
  public static final int BOUNDS_RECT_DELTA = 20;

  public static final int DEFAULT_SCREEN_OFFSET_X = 50;
  public static final int DEFAULT_SCREEN_OFFSET_Y = 50;
  /** Distance between blueprint screen and regular screen */
  public static final int SCREEN_DELTA = 10;

  /**
   * Distance between the bottom bound of model name and top bound of SceneView.
   */
  @SwingCoordinate
  public static final int NAME_LABEL_BOTTOM_MARGIN_PX = 5;

  /**
   * The maximum number of pixels will be considered a "match" when snapping
   * resize or move positions to edges or other constraints
   */
  public static final int MAX_MATCH_DISTANCE = 20;

  public static final int RESIZING_CORNER_SIZE = 32;
  public static final int RESIZING_HOVERING_SIZE = 48;
  public static final Color DARK_LIST_FOREGROUND = new Color(0xbbbbbb);

  public static final Color UNAVAILABLE_ZONE_COLOR = new Color(0, 0, 0, 13);
  public static final Color RESIZING_CONTOUR_COLOR = new Color(0x5082db);
  public static final Color RESIZING_CORNER_COLOR = new Color(0x03a9f4);
  public static final Color RESIZING_CUE_COLOR = new Color(0x757575);
  public static final Color RESIZING_TEXT_COLOR = new JBColor(new Color(0x80000000, true), DARK_LIST_FOREGROUND);
  public static final Color RESIZING_BUCKET_COLOR = new Color(0x03, 0xa9, 0xf4, 26);

  public static final Color CYAN_100 = new Color(178, 235, 242, 100);
  public static final Color CYAN_200 = new Color(128, 222, 234, 100);
  public static final Color CYAN_300 = new Color(77, 208, 225, 100);
  public static final Color CYAN_400 = new Color(38, 198, 218, 100);
  public static final Color CYAN_500 = new Color(0, 188, 212, 100);
  public static final Color CYAN_600 = new Color(0, 172, 193, 100);
  public static final Color CYAN_700 = new Color(0, 151, 167, 100);
  public static final Color CYAN_800 = new Color(0, 131, 143, 100);
  public static final Color CYAN_900 = new Color(0, 96, 100, 100);
  public static final Color[] RESIZING_OTHER_CONFIG_COLOR_ARRAY =
    {CYAN_100, CYAN_200, CYAN_300, CYAN_400, CYAN_500, CYAN_600, CYAN_700, CYAN_800, CYAN_900};

  public static final BasicStroke SOLID_STROKE = new BasicStroke(1.0f);
  public static final BasicStroke THICK_SOLID_STROKE = new BasicStroke(2.0f);
  public static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                                                                   new float[] { 4, 4 }, 0.0f);
  
}
