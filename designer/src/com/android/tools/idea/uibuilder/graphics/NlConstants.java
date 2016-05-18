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

import com.intellij.ui.ColorUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;

import java.awt.*;

public class NlConstants {
  public static final int RULER_SIZE_PX = 20;
  public static final int RULER_MAJOR_TICK_PX = 19;
  public static final int RULER_MINOR_TICK_PX = 6;
  public static final int BOUNDS_RECT_DELTA = 20;

  public static final int DEFAULT_SCREEN_OFFSET_X = 50;
  public static final int DEFAULT_SCREEN_OFFSET_Y = 50;
  /** Distance between blueprint screen and regular screen */
  public static final int SCREEN_DELTA = 10;

  /**
   * The maximum number of pixels will be considered a "match" when snapping
   * resize or move positions to edges or other constraints
   */
  public static final int MAX_MATCH_DISTANCE = 20;

  @SuppressWarnings("UseJBColor")
  public static final JBColor RULER_BG = new JBColor(Color.WHITE, ColorUtil.brighter(UIUtil.getListBackground(), 1));
  @SuppressWarnings("UseJBColor")
  public static final JBColor BOUNDS_RECT_COLOR = new JBColor(Color.GRAY, UIUtil.getListForeground());
  public static final JBColor RULER_TICK_COLOR = new JBColor(0xdbdbdb, UIUtil.getListForeground().darker().getRGB());
  public static final JBColor RULER_TEXT_COLOR = new JBColor(0x959595, UIUtil.getListForeground().getRGB());
  public static final Font RULER_TEXT_FONT = JBUI.Fonts.miniFont();

  public static final Font BLUEPRINT_TEXT_FONT = RULER_TEXT_FONT;
  @SuppressWarnings("UseJBColor")
  public static final Color BLUEPRINT_BG_COLOR = new Color(0x133572);
  @SuppressWarnings("UseJBColor")
  public static final Color BLUEPRINT_GRID_COLOR = new Color(0x17397b);
  @SuppressWarnings("UseJBColor") public static final Color BLUEPRINT_FG_COLOR = new Color(0x6196c8);
  @SuppressWarnings("UseJBColor")
  public static final Color BLUEPRINT_COMPONENT_BG_COLOR = new Color(0, 0, 0, 0);
  @SuppressWarnings("UseJBColor")
  public static final Color BLUEPRINT_COMPONENT_FG_COLOR = new Color(81, 103, 163, 100);
  public static final Stroke BLUEPRINT_COMPONENT_STROKE = NlDrawingStyle.THIN_SOLID_STROKE;
  @SuppressWarnings("UseJBColor")
  public static final Color UNAVAILABLE_ZONE_COLOR = new Color(0, 0, 0, 100);


  public static final JBColor DESIGN_SURFACE_BG = new JBColor(0xf2f2f2, UIUtil.getListBackground().getRGB());

  public static final BasicStroke SOLID_STROKE = new BasicStroke(1.0f);
  public static final BasicStroke THICK_SOLID_STROKE = new BasicStroke(2.0f);
  public static final BasicStroke DOTTED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                                                                   new float[] { 2, 2 }, 0.0f);
  public static final BasicStroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                                                                   new float[] { 4, 4 }, 0.0f);
  public static final BasicStroke PATTERN_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                                                                    new float[] { 8, 4 }, 0.0f);
  public static final BasicStroke THICK_PATTERN_STROKE = new BasicStroke(2.0f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f,
                                                                          new float[] { 8, 4 }, 0.0f);
  
}
