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

package com.android.tools.adtui;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashMap;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import sun.swing.SwingUtilities2;

import javax.swing.*;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  /**
   * Space, in pixels, between vertical legends.
   */
  private static final int LEGEND_VERT_MARGIN_PX = JBUI.scale(10);

  /**
   * Space, in pixels, between horizontal legends.
   */
  private final static int LEGEND_HORIZ_MARGIN_PX = JBUI.scale(12);

  /**
   * Space between a legend icon and its text
   */
  private static final int ICON_MARGIN_PX = JBUI.scale(7);

  private final int myLeftPadding;
  private final int myRightPadding;
  private final int myVerticalPadding;
  private final LegendComponentModel myModel;
  /**
   * The visual configuration of the legends
   */
  private final Map<Legend, LegendConfig> myConfigs;

  /**
   * In order to prevent legend text jumping as values change, we keep the max text width
   * encountered so far and never let the text get smaller as long as the legend is in use.
   */
  private final Map<Legend, Integer> myMinWidths = new HashMap<>();

  @NotNull
  private final Orientation myOrientation;

  @NotNull
  private final List<LegendInstruction> myInstructions = new ArrayList<>();

  /**
   * Convenience method for creating a default, horizontal legend component based on a target
   * model. If you want to override defaults, use a {@link Builder} instead.
   */
  public LegendComponent(@NotNull LegendComponentModel model) {
    this(new Builder(model));
  }

  /**
   * Legend component that renders a label, and icon for each series in a chart.
   */
  private LegendComponent(@NotNull Builder builder) {
    myModel = builder.myModel;
    myConfigs = new HashMap<>();
    myOrientation = builder.myOrientation;
    myLeftPadding = builder.myLeftPadding;
    myRightPadding = builder.myRightPadding;
    myVerticalPadding = builder.myVerticalPadding;
    myModel.addDependency(myAspectObserver)
      .onChange(LegendComponentModel.Aspect.LEGEND, this::modelChanged);
    setFont(AdtUiUtils.DEFAULT_FONT);
    modelChanged();
  }

  public void configure(Legend legend, LegendConfig config) {
    myConfigs.put(legend, config);
  }

  @NotNull
  public LegendComponentModel getModel() {
    return myModel;
  }

  /**
   * A list of instructions for composing this legend. It can be iterated through either to render
   * the legend or figure out its bounds.
   */
  @VisibleForTesting
  @NotNull
  List<LegendInstruction> getInstructions() {
    return myInstructions;
  }

  @NotNull
  private LegendConfig getConfig(Legend data) {
    LegendConfig config = myConfigs.get(data);
    if (config == null) {
      config = new LegendConfig(LegendConfig.IconType.NONE, Color.RED);
      myConfigs.put(data, config);
    }
    return config;
  }

  @Override
  public Dimension getPreferredSize() {
    int width = 0;
    int height = 0;

    LegendState state = new LegendState(myInstructions);
    LegendCursor cursor = new LegendCursor();
    for (LegendInstruction instruction : myInstructions) {
      instruction.moveCursor(state, cursor);
      width = Math.max(cursor.x, width);
      height = Math.max(cursor.y + state.rowHeight, height);
    }

    return new Dimension(width + myLeftPadding + myRightPadding, height + 2 * myVerticalPadding);
  }

  @Override
  public Dimension getMinimumSize() {
    return this.getPreferredSize();
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    LegendState state = new LegendState(myInstructions);
    LegendCursor cursor = new LegendCursor();
    LegendBounds bounds = new LegendBounds(myLeftPadding, myVerticalPadding);
    for (LegendInstruction instruction : myInstructions) {
      bounds.update(state, cursor, instruction.getSize());
      instruction.render(this, g2d, bounds);
      instruction.moveCursor(state, cursor);
    }
  }

  private void modelChanged() {
    Dimension prevSize = getPreferredSize();

    myInstructions.clear();

    for (Legend legend : myModel.getLegends()) {
      if (legend != myModel.getLegends().get(0)) {
        if (myOrientation == Orientation.HORIZONTAL) {
          myInstructions.add(new GapInstruction(LEGEND_HORIZ_MARGIN_PX));
        }
        else {
          myInstructions.add(new NewRowInstruction());
        }
      }

      LegendConfig config = getConfig(legend);
      if (config.getIcon() != LegendConfig.IconType.NONE) {
        myInstructions.add(new IconInstruction(config.getIcon(), config.getColor()));
        myInstructions.add(new GapInstruction(ICON_MARGIN_PX));
      }

      String name = legend.getName();
      String value = legend.getValue();
      if (!name.isEmpty() && StringUtil.isNotEmpty(value)) {
        name += ": ";
      }

      myInstructions.add(new TextInstruction(getFont(), name));
      if (StringUtil.isNotEmpty(value)) {
        TextInstruction valueInstruction = new TextInstruction(getFont(), value);
        if (myOrientation != Orientation.VERTICAL) {
          // In order to prevent one legend's value changing causing the other legends from jumping
          // around, we remember the text's largest size and never shrink.
          Integer minWidth = myMinWidths.getOrDefault(legend, 0);
          if (valueInstruction.getSize().w < minWidth) {
            // Add a gap, effectively right-justifying the text
            myInstructions.add(new GapInstruction(minWidth - valueInstruction.getSize().w));
          }
          else {
            myMinWidths.put(legend, valueInstruction.getSize().w);
          }
        }
        myInstructions.add(valueInstruction);
      }
    }

    if (!getPreferredSize().equals(prevSize)) {
      revalidate();
    }
  }

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  public static final class Builder {
    private static final int DEFAULT_PADDING_X_PX = JBUI.scale(5);
    private static final int DEFAULT_PADDING_Y_PX = JBUI.scale(5);

    private final LegendComponentModel myModel;
    private int myLeftPadding = DEFAULT_PADDING_X_PX;
    private int myRightPadding = DEFAULT_PADDING_X_PX;
    private int myVerticalPadding = DEFAULT_PADDING_Y_PX;
    private Orientation myOrientation = Orientation.HORIZONTAL;

    public Builder(@NotNull LegendComponentModel model) {
      myModel = model;
    }

    @NotNull
    public Builder setVerticalPadding(int verticalPadding) {
      myVerticalPadding = verticalPadding;
      return this;
    }

    @NotNull
    public Builder setLeftPadding(int leftPadding) {
      myLeftPadding = leftPadding;
      return this;
    }

    @NotNull
    public Builder setRightPadding(int rightPadding) {
      myRightPadding = rightPadding;
      return this;
    }

    @NotNull
    public Builder setHorizontalPadding(int padding) {
      setLeftPadding(padding);
      setRightPadding(padding);
      return this;
    }

    @NotNull
    public Builder setOrientation(@NotNull Orientation orientation) {
      myOrientation = orientation;
      return this;
    }

    @NotNull
    public LegendComponent build() {
      return new LegendComponent(this);
    }
  }

  /**
   * State global to all instructions which needs to be pre-calculated before rendering them.
   */
  private static final class LegendState {
    private int rowHeight = 0;

    public LegendState(List<LegendInstruction> instructions) {
      for (LegendInstruction instruction : instructions) {
        rowHeight = Math.max(instruction.getSize().h, rowHeight);
      }
    }
  }

  /**
   * Maintains current pixel positions of legend elements as we run through instructions.
   */
  private static final class LegendCursor {
    public int x;
    public int y;
  }

  /**
   * The size of a legend's area represented by an instruction.
   */
  private static final class LegendSize {
    private static final LegendSize EMPTY = new LegendSize(0, 0);

    public final int w;
    public final int h;

    private LegendSize(int w, int h) {
      this.w = w;
      this.h = h;
    }
  }

  /**
   * Bounds area used when rendering legend elements.
   */
  private static final class LegendBounds {
    private final int myLeftX;
    private final int myTopY;

    private int x;
    private int y;
    private int w;
    private int h;

    public LegendBounds(int leftX, int topY) {
      myLeftX = leftX;
      myTopY = topY;
    }

    public void update(@NotNull LegendState state, @NotNull LegendCursor cursor, @NotNull LegendSize size) {
      x = myLeftX + cursor.x;
      y = myTopY + cursor.y;
      w = size.w;
      h = state.rowHeight;
    }
  }

  /**
   * Base class for instructions used in updating state and rendering legend elements.
   *
   * To render a legend, you can iterate through a list of instructions, creating the current
   * {@link LegendBounds} from the current {@link LegendCursor} position, and then moving the
   * cursor to the next instruction using {@link #moveCursor(LegendState, LegendCursor)}
   */
  static abstract class LegendInstruction {
    @NotNull
    public LegendSize getSize() {
      return LegendSize.EMPTY;
    }

    public void moveCursor(@NotNull LegendState state, @NotNull LegendCursor cursor) {
      LegendSize size = getSize();
      cursor.x += size.w;
    }

    public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull LegendBounds bounds) {
    }
  }

  /**
   * An instruction to render an {@link LegendConfig.IconType} icon.
   */
  @VisibleForTesting
  static final class IconInstruction extends LegendInstruction {
    private static final int ICON_HEIGHT_PX = 15;
    private static final int LINE_THICKNESS = 3;

    // Non-even size chosen because that centers well (e.g. (15 - 11) / 2, vs. (15 - 10) / 2)
    private static final LegendSize BOX_SIZE = new LegendSize(11, 11);
    private static final LegendSize BOX_BOUNDS = new LegendSize(11, ICON_HEIGHT_PX);
    private static final LegendSize LINE_SIZE = new LegendSize(12, LINE_THICKNESS);
    private static final LegendSize LINE_BOUNDS = new LegendSize(12, ICON_HEIGHT_PX);

    private static final BasicStroke LINE_STROKE = new BasicStroke(LINE_THICKNESS);
    private static final BasicStroke DASH_STROKE = new BasicStroke(LINE_THICKNESS,
                                                                   BasicStroke.CAP_BUTT,
                                                                   BasicStroke.JOIN_BEVEL,
                                                                   10.0f,  // Miter limit, Swing's default
                                                                   new float[]{5.0f, 2f},  // Dash pattern in pixel
                                                                   0.0f);  // Dash phase - just starts at zero.
    private static final BasicStroke BORDER_STROKE = new BasicStroke(1);

    @VisibleForTesting
    @NotNull
    final LegendConfig.IconType myType;
    @NotNull private final Color myColor;
    @NotNull private final Color myBorderColor;

    public IconInstruction(@NotNull LegendConfig.IconType type, @NotNull Color color) {
      switch (type) {
        case BOX:
        case LINE:
        case DASHED_LINE:
          break;
        default:
          throw new IllegalArgumentException(type.toString());
      }

      myType = type;
      myColor = color;

      // TODO: make the border customizable. Profilers, for instance, shouldn't have a border in Darcula.
      int r = (int)(myColor.getRed() * .8f);
      int g = (int)(myColor.getGreen() * .8f);
      int b = (int)(myColor.getBlue() * .8f);
      myBorderColor = new Color(r, g, b);
    }

    @NotNull
    @Override
    public LegendSize getSize() {
      switch (myType) {
        case BOX:
          return BOX_BOUNDS;
        case LINE:
        case DASHED_LINE:
          return LINE_BOUNDS;
        default:
          throw new IllegalStateException(myType.toString());
      }
    }

    @Override
    public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull LegendBounds bounds) {
      // The legend icon is a short, straight line, or a small box.
      // Turning off anti-aliasing makes it look sharper in a good way.
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      Stroke prevStroke = g2d.getStroke();
      switch (myType) {
        case BOX:
          assert (BOX_SIZE.w <= bounds.w);
          assert (BOX_SIZE.h <= bounds.h);
          int boxX = bounds.x;
          int boxY = bounds.y + ((bounds.h - BOX_SIZE.h) / 2);
          g2d.setColor(myColor);
          g2d.fillRect(boxX, boxY, BOX_SIZE.w, BOX_SIZE.h);

          g2d.setColor(myBorderColor);
          g2d.setStroke(BORDER_STROKE);
          g2d.drawRect(boxX, boxY, BOX_SIZE.w, BOX_SIZE.h);
          break;

        case LINE:
        case DASHED_LINE:
          assert (LINE_SIZE.w <= bounds.w);
          assert (LINE_SIZE.h <= bounds.h);
          g2d.setColor(myColor);
          g2d.setStroke(myType == LegendConfig.IconType.LINE ? LINE_STROKE : DASH_STROKE);
          int lineX = bounds.x;
          int lineY = bounds.y + (bounds.h / 2);
          g2d.drawLine(lineX, lineY, lineX + LINE_SIZE.w, lineY);
          break;

        default:
          throw new IllegalStateException(myType.toString());
      }
      g2d.setStroke(prevStroke);
    }
  }

  /**
   * Instruction for rendering text.
   */
  @VisibleForTesting
  static final class TextInstruction extends LegendInstruction {
    @VisibleForTesting
    @NotNull
    final String myText;
    @NotNull private final Font myFont;
    @NotNull private final LegendSize mySize;

    private TextInstruction(@NotNull Font font, @NotNull String text) {
      myFont = font;
      myText = text;

      Rectangle2D bounds = myFont.getStringBounds(myText, new FontRenderContext(null, true, true));
      int w = (int)bounds.getWidth();
      int h = (int)bounds.getHeight();

      mySize = new LegendSize(w, h);
    }

    @NotNull
    @Override
    public LegendSize getSize() {
      return mySize;
    }

    @Override
    public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull LegendBounds bounds) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setColor(c.getForeground());
      FontMetrics metrics = SwingUtilities2.getFontMetrics(c, myFont);
      assert (mySize.h <= bounds.h);
      int textY = bounds.y + metrics.getAscent() + ((bounds.h - mySize.h) / 2);
      g2d.drawString(myText, bounds.x, textY);
    }
  }

  /**
   * Instruction to skip a bit of horizontal space, useful for margins between elements.
   */
  @VisibleForTesting
  static final class GapInstruction extends LegendInstruction {
    private final LegendSize mySize;

    private GapInstruction(int w) {
      mySize = new LegendSize(w, 0);
    }

    @NotNull
    @Override
    public LegendSize getSize() {
      return mySize;
    }
  }

  /**
   * Instruction to create a new row in a {@link Orientation#VERTICAL} legend; this has the effect
   * of moving the cursor back to the left.
   */
  @VisibleForTesting
  static final class NewRowInstruction extends LegendInstruction {
    @Override
    public void moveCursor(@NotNull LegendState state, @NotNull LegendCursor cursor) {
      cursor.y += state.rowHeight + LEGEND_VERT_MARGIN_PX;
      cursor.x = 0;
    }
  }
}