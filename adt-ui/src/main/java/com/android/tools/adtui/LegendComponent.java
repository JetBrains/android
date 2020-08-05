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

import com.android.tools.adtui.common.AdtUiUtils;
import com.android.tools.adtui.instructions.GapInstruction;
import com.android.tools.adtui.instructions.IconInstruction;
import com.android.tools.adtui.instructions.InstructionsRenderer;
import com.android.tools.adtui.instructions.NewRowInstruction;
import com.android.tools.adtui.instructions.RenderInstruction;
import com.android.tools.adtui.instructions.TextInstruction;
import com.android.tools.adtui.model.legend.Legend;
import com.android.tools.adtui.model.legend.LegendComponentModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A label component that updates its value based on the reporting series passed to it.
 */
public class LegendComponent extends AnimatedComponent {

  /**
   * Space, in pixels, between vertical legends.
   */
  private static final int LEGEND_VERT_MARGIN_PX = JBUI.scale(8);

  /**
   * Space, in pixels, between horizontal legends.
   */
  private final static int LEGEND_HORIZ_MARGIN_PX = JBUI.scale(10);

  /**
   * Space between a legend icon and its text
   */
  private static final int ICON_MARGIN_PX = JBUI.scale(6);

  /**
   * A cache for cropped icons.
   */
  private static final Map<Icon, Icon> myCroppedIconCache = new HashMap<>();

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
  private final List<RenderInstruction> myInstructions = new ArrayList<>();

  private final Map<Legend, String> myValuesCache = new HashMap<>();

  private final boolean myShowValues;

  private final Set<String> myExcludedLegends;

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
    myShowValues = builder.myShowValues;
    myExcludedLegends = builder.myExcludedLegends;
    myModel.addDependency(myAspectObserver).onChange(LegendComponentModel.Aspect.LEGEND, this::modelChanged);
    setFont(AdtUiUtils.DEFAULT_FONT.deriveFont(builder.myTextSize));
    modelChanged();
  }

  public void configure(@NotNull Legend legend, @NotNull LegendConfig config) {
    myConfigs.put(legend, config);
    modelChanged();
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
  List<RenderInstruction> getInstructions() {
    return myInstructions;
  }

  @NotNull
  private LegendConfig getConfig(@NotNull Legend data) {
    LegendConfig config = myConfigs.get(data);
    if (config == null) {
      config = new LegendConfig(LegendConfig.IconType.NONE, Color.RED);
      myConfigs.put(data, config);
    }
    return config;
  }

  @Override
  public Dimension getPreferredSize() {
    InstructionsRenderer state = new InstructionsRenderer(myInstructions, InstructionsRenderer.HorizontalAlignment.LEFT);
    Dimension renderSize = state.getRenderSize();
    return new Dimension(renderSize.width + myLeftPadding + myRightPadding, renderSize.height + 2 * myVerticalPadding);
  }

  @Override
  public Dimension getMinimumSize() {
    return this.getPreferredSize();
  }

  @Override
  protected void draw(Graphics2D g2d, Dimension dim) {
    g2d.translate(myLeftPadding, myVerticalPadding);
    InstructionsRenderer state = new InstructionsRenderer(myInstructions, InstructionsRenderer.HorizontalAlignment.LEFT);
    state.draw(this, g2d);
    g2d.translate(-myLeftPadding, -myVerticalPadding);
  }

  private void modelChanged() {
    if (!isShowing()) {
      return;
    }

    boolean valuesChanged = false;
    // Check for new/modified legends.
    for (Legend legend : myModel.getLegends()) {
      if (myExcludedLegends.contains(legend.getName())) {
        continue;
      }

      boolean isValueCached = myValuesCache.containsKey(legend);

      String value = legend.getValue();
      String oldValue = myValuesCache.put(legend, value);

      if (!isValueCached || !Objects.equals(value, oldValue)) {
        valuesChanged = true;
      }
    }
    // Check for stale cached legend values whose Legends are no longer in the model.
    int cacheSize = myValuesCache.size();
    myValuesCache.keySet().retainAll(myModel.getLegends());
    if (myValuesCache.size() != cacheSize) {
      valuesChanged = true;
    }
    if (!valuesChanged) {
      return;
    }

    Dimension prevSize = getPreferredSize();

    myInstructions.clear();

    for (Legend legend : myModel.getLegends()) {
      if (myExcludedLegends.contains(legend.getName())) {
        continue;
      }

      String name = legend.getName();
      String value = legend.getValue();
      LegendConfig config = getConfig(legend);
      if (value == null) {
        // We'll skip any legend that returns a null value.
        continue;
      }

      if (legend != myModel.getLegends().get(0)) {
        if (myOrientation == Orientation.HORIZONTAL) {
          myInstructions.add(new GapInstruction(LEGEND_HORIZ_MARGIN_PX));
        }
        else {
          myInstructions.add(new NewRowInstruction(LEGEND_VERT_MARGIN_PX));
        }
      }

      if (config.getIconType() != LegendConfig.IconType.NONE) {
        RenderInstruction iconInstruction;
        int gapAdjust;
        if (config.getIconType() == LegendConfig.IconType.CUSTOM) {
          assert config.getIconGetter() != null;
          iconInstruction = new IconInstruction(cropAndCacheIcon(config.getIconGetter().apply(value)), 0, config.getColor());
          gapAdjust = 0;
        }
        else {
          iconInstruction = new LegendIconInstruction(config.getIconType(), config.getColor());
          // For vertical legends, Components after icons need be aligned to left, so adjust the gap width after icon.
          gapAdjust = myOrientation == Orientation.VERTICAL ? LegendIconInstruction.ICON_MAX_WIDTH - iconInstruction.getSize().width : 0;
        }
        myInstructions.add(iconInstruction);
        myInstructions.add(new GapInstruction(ICON_MARGIN_PX + gapAdjust));
      }

      if (myShowValues && !name.isEmpty() && StringUtil.isNotEmpty(value)) {
        name += ": ";
      }

      if (StringUtil.isNotEmpty(name)) {
        myInstructions.add(new TextInstruction(UIUtilities.getFontMetrics(this, getFont()), name));
      }
      if (myShowValues && StringUtil.isNotEmpty(value)) {
        TextInstruction valueInstruction = new TextInstruction(UIUtilities.getFontMetrics(this, getFont()), value);
        myInstructions.add(valueInstruction);
        if (myOrientation != Orientation.VERTICAL) {
          // In order to prevent one legend's value changing causing the other legends from jumping
          // around, we remember the text's largest size and never shrink.
          Integer minWidth = myMinWidths.getOrDefault(legend, 0);
          if (valueInstruction.getSize().width < minWidth) {
            // Add a gap, effectively right-justifying the text
            myInstructions.add(new GapInstruction(minWidth - valueInstruction.getSize().width));
          }
          else {
            myMinWidths.put(legend, valueInstruction.getSize().width);
          }
        }
      }
    }

    if (!getPreferredSize().equals(prevSize)) {
      revalidate();
    }
    repaint();
  }

  /**
   * Crop an icon and store it in a cache so we can retrieve it later without performing another expensive cropping operation.
   *
   * @return An icon cropped to match a LegendIconInstruction. If called with an icon cropped before will return one from cache.
   */
  @VisibleForTesting
  static Icon cropAndCacheIcon(Icon icon) {
    if (!myCroppedIconCache.containsKey(icon)) {
      myCroppedIconCache.put(icon, IconUtil.cropIcon(icon, LegendIconInstruction.ICON_MAX_WIDTH, LegendIconInstruction.ICON_HEIGHT_PX));
    }
    return myCroppedIconCache.get(icon);
  }

  public enum Orientation {
    HORIZONTAL,
    VERTICAL,
  }

  public static final class Builder {
    private static final int DEFAULT_PADDING_X_PX = JBUI.scale(1); // Should at least be 1 to avoid borders getting clipped.
    private static final int DEFAULT_PADDING_Y_PX = JBUI.scale(5);
    private static final float DEFAULT_TEXT_SIZE = JBUI.scale(12);

    private final LegendComponentModel myModel;
    private int myLeftPadding = DEFAULT_PADDING_X_PX;
    private int myRightPadding = DEFAULT_PADDING_X_PX;
    private int myVerticalPadding = DEFAULT_PADDING_Y_PX;
    private float myTextSize = DEFAULT_TEXT_SIZE;
    private Orientation myOrientation = Orientation.HORIZONTAL;
    private boolean myShowValues = true;
    private Set<String> myExcludedLegends = new TreeSet<>();

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
    public Builder setTextSize(int size) {
      myTextSize = size;
      return this;
    }

    @NotNull
    public Builder setOrientation(@NotNull Orientation orientation) {
      myOrientation = orientation;
      return this;
    }

    @NotNull
    public Builder setShowValues(boolean showValues) {
      myShowValues = showValues;
      return this;
    }

    @NotNull
    public Builder setExcludedLegends(String ... legendNames) {
      myExcludedLegends.addAll(Arrays.asList(legendNames));
      return this;
    }

    @NotNull
    public LegendComponent build() {
      return new LegendComponent(this);
    }
  }

  /**
   * An instruction to render an {@link LegendConfig.IconType} icon.
   */
  @VisibleForTesting
  static final class LegendIconInstruction extends RenderInstruction {
    private static final int ICON_HEIGHT_PX = 15;
    private static final int LINE_THICKNESS = 3;

    // Non-even size chosen because that centers well (e.g. (15 - 11) / 2, vs. (15 - 10) / 2)
    private static final Dimension BOX_SIZE = new Dimension(11, 11);
    private static final Dimension BOX_BOUNDS = new Dimension(11, ICON_HEIGHT_PX);
    private static final Dimension LINE_SIZE = new Dimension(12, LINE_THICKNESS);
    private static final Dimension LINE_BOUNDS = new Dimension(12, ICON_HEIGHT_PX);

    private static final BasicStroke LINE_STROKE = new BasicStroke(LINE_THICKNESS);
    private static final BasicStroke DASH_STROKE = new BasicStroke(LINE_THICKNESS,
                                                                   BasicStroke.CAP_BUTT,
                                                                   BasicStroke.JOIN_BEVEL,
                                                                   10.0f,  // Miter limit, Swing's default
                                                                   new float[]{5.0f, 2f},  // Dash pattern in pixel
                                                                   0.0f);  // Dash phase - just starts at zero.
    private static final BasicStroke BORDER_STROKE = new BasicStroke(1);
    private static int ICON_MAX_WIDTH = Math.max(BOX_SIZE.width, LINE_SIZE.width);
    private static final Color BOX_BORDER_COLOR = new JBColor(new Color(0, 0, 0, 0.1f), new Color(1, 1, 1, 0.1f));

    @VisibleForTesting
    @NotNull
    final LegendConfig.IconType myType;
    @NotNull private final Color myColor;

    public LegendIconInstruction(@NotNull LegendConfig.IconType type, @NotNull Color color) {
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
    }

    @NotNull
    @Override
    public Dimension getSize() {
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
    public void render(@NotNull JComponent c, @NotNull Graphics2D g2d, @NotNull Rectangle bounds) {
      // The legend icon is a short, straight line, or a small box.
      // Turning off anti-aliasing makes it look sharper in a good way.
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

      Stroke prevStroke = g2d.getStroke();
      switch (myType) {
        case BOX:
          assert (BOX_SIZE.width <= bounds.width);
          assert (BOX_SIZE.width <= bounds.height);
          int boxX = bounds.x;
          int boxY = bounds.y + ((bounds.height - BOX_SIZE.height) / 2);
          g2d.setColor(myColor);
          g2d.fillRect(boxX, boxY, BOX_SIZE.width, BOX_SIZE.height);

          g2d.setColor(BOX_BORDER_COLOR);
          g2d.setStroke(BORDER_STROKE);
          g2d.drawRect(boxX, boxY, BOX_SIZE.width - 1, BOX_SIZE.height - 1);
          break;

        case LINE:
        case DASHED_LINE:
          assert (LINE_SIZE.width <= bounds.width);
          assert (LINE_SIZE.height <= bounds.height);
          g2d.setColor(myColor);
          g2d.setStroke(myType == LegendConfig.IconType.LINE ? LINE_STROKE : DASH_STROKE);
          int lineX = bounds.x;
          int lineY = bounds.y + (bounds.height / 2);
          g2d.drawLine(lineX, lineY, lineX + LINE_SIZE.width, lineY);
          break;

        default:
          throw new IllegalStateException(myType.toString());
      }
      g2d.setStroke(prevStroke);
    }
  }
}