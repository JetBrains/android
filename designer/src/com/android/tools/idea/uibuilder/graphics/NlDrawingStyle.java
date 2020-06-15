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

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Stroke;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Drawing styles are used to distinguish the visual appearance of selection,
 * hovers, anchors, etc. Each style may have different colors, line thickness,
 * dashing style, transparency, etc.
 */
@SuppressWarnings("UseJBColor") // The designer canvas is not using light/dark themes; colors match Android theme rendering
public class NlDrawingStyle {

  /**
   * The style used to draw the recipient/target View of a drop. This is
   * typically going to be the bounding-box of the view into which you are
   * adding a new child.
   */
  public static final NlDrawingStyle
    DROP_RECIPIENT = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255),
                                        new Color(0xFF, 0x99, 0x00, 160), NlConstants.THICK_SOLID_STROKE);

  /**
   * The style used to draw a potential drop area <b>within</b> a
   * {@link #DROP_RECIPIENT}. For example, if you are dragging into a view
   * with a LinearLayout, the {@link #DROP_RECIPIENT} will be the view itself,
   * whereas each possible insert position between two children will be a
   * {@link #DROP_ZONE}. If the mouse is over a {@link #DROP_ZONE} it should
   * be drawn using the style {@link #DROP_ZONE_ACTIVE}.
   */
  public static final NlDrawingStyle
    DROP_ZONE = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 220),
                                   new Color(0x55, 0xAA, 0x00, 64), NlConstants.SOLID_STROKE);

  /**
   * The style used to draw a currently active drop zone within a drop
   * recipient. See the documentation for {@link #DROP_ZONE} for details on
   * the distinction between {@link #DROP_RECIPIENT}, {@link #DROP_ZONE} and
   * {@link #DROP_ZONE_ACTIVE}.
   */
  public static final NlDrawingStyle
    DROP_ZONE_ACTIVE = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 220),
                                          new Color(0x00, 0xAA, 0x00, 64), NlConstants.THICK_SOLID_STROKE);

  /**
   * The style used to draw a preview of where a dropped view would appear if
   * it were to be dropped at a given location.
   */
  public static final NlDrawingStyle
    DROP_PREVIEW = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255), null, NlConstants.THICK_PATTERN_STROKE);

  /**
   * The style used to draw illegal/error/invalid markers
   */
  public static final NlDrawingStyle
    INVALID = new NlDrawingStyle(new Color(0xFF, 0xFF, 0xFF, 192),
                                 new Color(0xFF, 0x00, 0x00, 64), NlConstants.THICK_SOLID_STROKE);

  /**
   * Construct a new style value with the given foreground, background, width,
   * line style and transparency.
   *
   * @param stroke A color descriptor for the foreground color, or null if no
   *            foreground color should be set
   * @param fill A color descriptor for the background color, or null if no
   *            foreground color should be set
   * @param stroke The line style - such as {@link NlConstants#SOLID_STROKE}.
   */
  public NlDrawingStyle(@Nullable Color strokeColor, @Nullable Color fill, @NotNull BasicStroke stroke) {
    myStrokeColor = strokeColor;
    myStroke = stroke;
    myFillColor = fill;
  }

  /**
   * Convenience constructor for typical drawing styles, which do not specify
   * a fill and use a standard thickness line
   *
   * @param strokeColor Stroke color to be used (e.g. for the border/foreground)
   * @param stroke The line style - such as {@link NlConstants#SOLID_STROKE}.
   */
  public NlDrawingStyle(@Nullable Color strokeColor, @NotNull BasicStroke stroke) {
    this(strokeColor, null, stroke);
  }

  /**
   * Return the stroke/foreground/border color to be used for
   * this style, or null if none
   */
  @Nullable
  public Color getStrokeColor() {
    return myStrokeColor;
  }

  /** Return the line stroke style */
  @NotNull
  public Stroke getStroke() {
    return myStroke;
  }

  /**
   * Return the fill/background/interior color to be used for
   * this style, or null if none
   */
  @Nullable
  public Color getFillColor() {
    return myFillColor;
  }

  /** Stroke/foreground/border color */
  private final Color myStrokeColor;

  /** Stroke type */
  private final Stroke myStroke;

  /** Fill/foreground/interior color */
  private final Color myFillColor;
}
