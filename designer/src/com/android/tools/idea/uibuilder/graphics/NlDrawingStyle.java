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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.ui.Gray;

import java.awt.*;

/**
 * Drawing styles are used to distinguish the visual appearance of selection,
 * hovers, anchors, etc. Each style may have different colors, line thickness,
 * dashing style, transparency, etc.
 */
@SuppressWarnings("UseJBColor") // The designer canvas is not using light/dark themes; colors match Android theme rendering
public class NlDrawingStyle {
  /** Whether we should show a static grid of all the linear layout insert positions or not
   * (if false, it is shown only during an active drag) */
  public static final boolean SHOW_STATIC_GRID = false;

  /** Whether we should show a static border around selected views */
  public static final boolean SHOW_STATIC_BORDERS = false;

  /**
   * The maximum number of pixels will be considered a "match" when snapping
   * resize or move positions to edges or other constraints
   */
  public static final int MAX_MATCH_DISTANCE = 20;

  public static final BasicStroke THIN_SOLID_STROKE = new BasicStroke(0.5f);
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
  public static final Gray DESIGNER_BACKGROUND_COLOR = Gray._150;


  /**
   * The style used to draw the selected views
   */
  public static final NlDrawingStyle
    SELECTION = new NlDrawingStyle(new Color(0x00, 0x99, 0xFF, 192),
                                                                new Color(0x00, 0x99, 0xFF, 96), SOLID_STROKE);

  /**
   * The style used to draw guidelines - overlay lines which indicate
   * significant geometric positions.
   */
  public static final NlDrawingStyle
    //GUIDELINE = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), SOLID_STROKE);
    GUIDELINE = new NlDrawingStyle(new Color(0x61, 0x96, 0xC8, 192), SOLID_STROKE);

  /**
   * The style used to guideline shadows
   */
  public static final NlDrawingStyle
    //GUIDELINE_SHADOW = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), SOLID_STROKE);
    GUIDELINE_SHADOW = new NlDrawingStyle(new Color(0x61, 0x96, 0xC8, 192), SOLID_STROKE);

  /**
   * The style used to draw guidelines, in particular shared edges and center lines; this
   * is a dashed edge.
   */
  public static final NlDrawingStyle
    //GUIDELINE_DASHED = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), PATTERN_STROKE);
    GUIDELINE_DASHED = new NlDrawingStyle(new Color(0x61, 0x96, 0xC8, 192), PATTERN_STROKE);

  /**
   * The style used to draw distance annotations
   */
  public static final NlDrawingStyle
    DISTANCE = new NlDrawingStyle(new Color(0xFF, 0x00, 0x00, 192 - 32), SOLID_STROKE);

  /**
   * The style used to draw grids
   */
  public static final NlDrawingStyle
    GRID = new NlDrawingStyle(new Color(0xAA, 0xAA, 0xAA, 128), DOTTED_STROKE);

  /**
   * The style used for hovered views (e.g. when the mouse is directly on top
   * of the view)
   */
  public static final NlDrawingStyle
    HOVER = new NlDrawingStyle(new Color(0x7F, 0x7F, 0x7F, 100), new Color(0xFF, 0xFF, 0xFF, 40),
                                                            DOTTED_STROKE);

  /**
   * The style used for hovered views (e.g. when the mouse is directly on top
   * of the view), when the hover happens to be the same object as the selection
   */
  public static final NlDrawingStyle
    HOVER_SELECTION = new NlDrawingStyle(new Color(0x7F, 0x7F, 0x7F, 100), new Color(0xFF, 0xFF, 0xFF, 10),
                                                                      DOTTED_STROKE);

  /**
   * The style used to draw anchors (lines to the other views the given view
   * is anchored to)
   */
  public static final NlDrawingStyle
    ANCHOR = new NlDrawingStyle(new Color(0x00, 0x99, 0xFF, 96), SOLID_STROKE);

  /**
   * The style used to draw outlines (the structure of views)
   */
  public static final NlDrawingStyle
    OUTLINE = new NlDrawingStyle(new Color(0x88, 0xFF, 0x88, 160), SOLID_STROKE);

  /**
   * The style used to draw the recipient/target View of a drop. This is
   * typically going to be the bounding-box of the view into which you are
   * adding a new child.
   */
  public static final NlDrawingStyle
    DROP_RECIPIENT = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255),
                                                                     new Color(0xFF, 0x99, 0x00, 160), THICK_SOLID_STROKE);

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
                                                                new Color(0x55, 0xAA, 0x00, 64), SOLID_STROKE);

  /**
   * The style used to draw a currently active drop zone within a drop
   * recipient. See the documentation for {@link #DROP_ZONE} for details on
   * the distinction between {@link #DROP_RECIPIENT}, {@link #DROP_ZONE} and
   * {@link #DROP_ZONE_ACTIVE}.
   */
  public static final NlDrawingStyle
    DROP_ZONE_ACTIVE = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 220),
                                                                       new Color(0x00, 0xAA, 0x00, 64), THICK_SOLID_STROKE);

  /**
   * The style used to draw a preview of where a dropped view would appear if
   * it were to be dropped at a given location.
   */
  public static final NlDrawingStyle
    DROP_PREVIEW = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255), null, THICK_PATTERN_STROKE);

  /**
   * The style used to preview a resize operation. Similar to {@link #DROP_PREVIEW}
   * but usually fainter to work better in combination with guidelines which
   * are often overlaid during resize.
   */
  public static final NlDrawingStyle
    RESIZE_PREVIEW = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255), null, THICK_SOLID_STROKE);

  /**
   * The style used to show a proposed resize bound which is being rejected (for example,
   * because there is no near edge to attach to in a RelativeLayout).
   */
  public static final NlDrawingStyle
    RESIZE_FAIL = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255), null, THICK_PATTERN_STROKE);

  /**
   * The style used to draw help/hint text.
   */
  public static final NlDrawingStyle
    HELP = new NlDrawingStyle(new Color(0xFF, 0xFF, 0xFF, 255),
                                                           new Color(0x00, 0x00, 0x00, 128), SOLID_STROKE);

  /**
   * The style used to draw illegal/error/invalid markers
   */
  public static final NlDrawingStyle
    INVALID = new NlDrawingStyle(new Color(0xFF, 0xFF, 0xFF, 192),
                                                              new Color(0xFF, 0x00, 0x00, 64), THICK_SOLID_STROKE);

  /**
   * The style used to highlight dependencies
   */
  public static final NlDrawingStyle
    DEPENDENCY = new NlDrawingStyle(new Color(0xFF, 0xFF, 0xFF, 255),
                                                                 new Color(0xFF, 0xFF, 0x00, 24), THICK_SOLID_STROKE);

  /**
   * The style used to draw an invalid cycle
   */
  public static final NlDrawingStyle
    CYCLE = new NlDrawingStyle(new Color(0xFF, 0x00, 0x00, 192), null, SOLID_STROKE);

  /**
   * The style used to highlight the currently dragged views during a layout
   * move (if they are not hidden)
   */
  public static final NlDrawingStyle
    DRAGGED = new NlDrawingStyle(new Color(0xFF, 0xFF, 0xFF, 255),
                                                              new Color(0x00, 0xFF, 0x00, 16), THICK_SOLID_STROKE);

  /**
   * The style used to draw empty containers of zero bounds (which are padded
   * a bit to make them visible during a drag or selection).
   */
  public static final NlDrawingStyle
    EMPTY = new NlDrawingStyle(new Color(0x00, 0x00, 0x00, 128),
                                                            new Color(0xFF, 0xFF, 0x55, 255), DASHED_STROKE);

  /**
   * A style used for unspecified purposes; can be used by a client to have
   * yet another color that is domain specific; using this color constant
   * rather than your own hardcoded value means that you will be guaranteed to
   * pick up a color that is themed properly and will look decent with the
   * rest of the colors
   */
  public static final NlDrawingStyle
    CUSTOM1 = new NlDrawingStyle(new Color(0xFF, 0x00, 0xFF, 255), null, SOLID_STROKE);

  /**
   * A second styled used for unspecified purposes; see {@link #CUSTOM1} for
   * details.
   */
  public static final NlDrawingStyle
    CUSTOM2 = new NlDrawingStyle(new Color(0x00, 0xFF, 0xFF, 255), null, DOTTED_STROKE);

  /** Style used to draw wrap_content resize feedback */
  public static final NlDrawingStyle
    RESIZE_WRAP = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), DASHED_STROKE);

  /** Style used to edit margins bounds */
  public static final NlDrawingStyle
    MARGIN_BOUNDS = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), new Color(0x00, 0xAA, 0x00, 64),
                                                              DASHED_STROKE);

  /** Style used to edit margin resizing bars */
  public static final NlDrawingStyle
    MARGIN_HANDLE = new NlDrawingStyle(new Color(0x00, 0xAA, 0x00, 192), new Color(0x00, 0xAA, 0x00, 64),
                                                              SOLID_STROKE);

  /** Style used to edit margins bounds */
  public static final NlDrawingStyle
    PADDING_BOUNDS = new NlDrawingStyle(new Color(0xFF, 0x99, 0x00, 255),
                                                                     new Color(0xFF, 0x99, 0x00, 160), DASHED_STROKE);

  /** Style used to show gravity locations */
  public static final NlDrawingStyle
    GRAVITY = new NlDrawingStyle(new Color(0xFF, 0x00, 0x00, 192), null, THICK_PATTERN_STROKE);

  /** Resizing by weights */
  public static final NlDrawingStyle
    RESIZE_WEIGHTS = new NlDrawingStyle(new Color(0x00, 0xFF, 0xFF, 192), new Color(0x00, 0xFF, 0xFF, 64),
                                                                     SOLID_STROKE);

  /** Resizing by spans (column span, row span) */
  public static final NlDrawingStyle
    RESIZE_SPAN = new NlDrawingStyle(new Color(0x00, 0xFF, 0xFF, 192), new Color(0x00, 0xFF, 0xFF, 64),
                                                                     SOLID_STROKE);

  /**
   * Construct a new style value with the given foreground, background, width,
   * line style and transparency.
   *
   * @param stroke A color descriptor for the foreground color, or null if no
   *            foreground color should be set
   * @param fill A color descriptor for the background color, or null if no
   *            foreground color should be set
   * @param stroke The line style - such as {@link #SOLID_STROKE}.
   */
  public NlDrawingStyle(@Nullable Color strokeColor, @Nullable Color fill, @NotNull BasicStroke stroke) {
    myStrokeColor = strokeColor;
    myStroke = stroke;
    myFillColor = fill;
    myLineWidth = (int)stroke.getLineWidth();
  }

  /**
   * Convenience constructor for typical drawing styles, which do not specify
   * a fill and use a standard thickness line
   *
   * @param strokeColor Stroke color to be used (e.g. for the border/foreground)
   * @param stroke The line style - such as {@link #SOLID_STROKE}.
   */
  public NlDrawingStyle(@Nullable Color strokeColor, @NotNull BasicStroke stroke) {
    this(strokeColor, null, stroke);
  }

  /**
   * Returns the thickness of the line as an integer
   * @return the line thickness
   */
  public int getLineWidth() {
    return myLineWidth;
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

  private final int myLineWidth;

  /** Stroke/foreground/border color */
  private final Color myStrokeColor;

  /** Stroke type */
  private final Stroke myStroke;

  /** Fill/foreground/interior color */
  private final Color myFillColor;
}
