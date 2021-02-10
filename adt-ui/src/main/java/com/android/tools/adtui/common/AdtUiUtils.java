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

import static com.intellij.util.ui.SwingHelper.ELLIPSIS;
import static java.awt.event.InputEvent.CTRL_DOWN_MASK;
import static java.awt.event.InputEvent.META_DOWN_MASK;

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.event.NestedScrollPaneMouseWheelListener;
import com.intellij.openapi.keymap.MacKeymapUtil;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.InputEvent;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.border.Border;
import org.intellij.lang.annotations.JdkConstants;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * ADT-UI utility class to hold constants and function used across the ADT-UI framework.
 */
public final class AdtUiUtils {
  /**
   * Default font to be used in the profiler UI.
   */
  public static final JBFont DEFAULT_FONT = JBUI.Fonts.label(10f);

  /**
   * Default font to be used in an empty tool window.
   * eg Device File Explorer when no device is connected and Sqlite Explorer when no database has been opened.
   */
  public static final JBFont EMPTY_TOOL_WINDOW_FONT = JBUI.Fonts.label(16f);

  /**
   * Default font color of charts, and component labels.
   */
  public static final Color DEFAULT_FONT_COLOR = JBColor.foreground();

  public static final Color DEFAULT_BORDER_COLOR = StudioColorsKt.getBorder();
  public static final Border DEFAULT_TOP_BORDER = BorderFactory.createMatteBorder(1, 0, 0, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_LEFT_BORDER = BorderFactory.createMatteBorder(0, 1, 0, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_BOTTOM_BORDER = BorderFactory.createMatteBorder(0, 0, 1, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_RIGHT_BORDER = BorderFactory.createMatteBorder(0, 0, 0, 1, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_HORIZONTAL_BORDERS = BorderFactory.createMatteBorder(1, 0, 1, 0, DEFAULT_BORDER_COLOR);
  public static final Border DEFAULT_VERTICAL_BORDERS = BorderFactory.createMatteBorder(0, 1, 0, 1, DEFAULT_BORDER_COLOR);

  public static final GridBagConstraints GBC_FULL =
    new GridBagConstraints(0, 0, 1, 1, 1, 1, GridBagConstraints.BASELINE, GridBagConstraints.BOTH, new Insets(0, 0, 0, 0), 0, 0);

  private AdtUiUtils() {
  }

  /**
   * Collapse a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param text           the original text.
   * @param metrics        the {@link FontMetrics} used to measure the text's width.
   * @param availableSpace the available space to render the text.
   * @param spaceThreshold if availableSpace is not larger than this threshold, return an empty string.
   * @return the fitted text
   */
  @NotNull
  public static String shrinkToFit(@NotNull String text, @NotNull FontMetrics metrics, float availableSpace, float spaceThreshold) {
    // FontMetrics#stringWidth(String) has some runtime overhead so the threshold is a performance optimization.
    return shrinkToFit(text, s -> availableSpace > spaceThreshold && availableSpace >= metrics.stringWidth(s));
  }

  /**
   * Similar to {@link #shrinkToFit(String, Predicate)},
   * but instead of a predicate to fit space it uses the font metrics compared to available space.
   */
  @NotNull
  public static String shrinkToFit(@NotNull String text, @NotNull FontMetrics metrics, float availableSpace) {
    return shrinkToFit(text, metrics, availableSpace, 0.0f);
  }

  /**
   * Collapses a line of text to fit the availableSpace by truncating the string and pad the end with ellipsis.
   *
   * @param text             the original text.
   * @param textFitPredicate predicate to test if text fits.
   * @return the fitted text.
   */
  @NotNull
  public static String shrinkToFit(@NotNull String text, @NotNull Predicate<String> textFitPredicate) {
    if (textFitPredicate.test(text)) {
      // Enough space - early return.
      return text;
    }
    else if (!textFitPredicate.test(ELLIPSIS)) {
      // No space to fit "..." - early return.
      return "";
    }

    int smallestLength = 0;
    int largestLength = text.length();
    int bestLength = smallestLength;
    do {
      int midLength = smallestLength + (largestLength - smallestLength) / 2;
      if (textFitPredicate.test(text.substring(0, midLength) + ELLIPSIS)) {
        bestLength = midLength;
        smallestLength = midLength + 1;
      }
      else {
        largestLength = midLength - 1;
      }
    }
    while (smallestLength <= largestLength);

    // Note: Don't return "..." if that's all we could show
    return (bestLength > 0) ? text.substring(0, bestLength) + ELLIPSIS : "";
  }

  /**
   * Does the reverse of {@link JBUI#scale(int) }
   */
  public static int unscale(int i) {
    return Math.round(i / JBUI.scale(1.0f));
  }

  /**
   * Returns the resulting sRGB color (no alpha) by overlaying a foregrond color with a given opacity over a background color.
   *
   * @param backgroundRgb     the sRGB color of the background.
   * @param foregroundRbg     the sRGB color of the foreground.
   * @param foregroundOpacity the opaicty of the foreground, in the range of 0.0 - 1.0
   * @return
   */
  public static Color overlayColor(int backgroundRgb, int foregroundRbg, float foregroundOpacity) {
    Color background = new Color(backgroundRgb);
    Color forground = new Color(foregroundRbg);
    return new Color(
      Math.round(background.getRed() * (1 - foregroundOpacity) + forground.getRed() * foregroundOpacity),
      Math.round(background.getGreen() * (1 - foregroundOpacity) + forground.getGreen() * foregroundOpacity),
      Math.round(background.getBlue() * (1 - foregroundOpacity) + forground.getBlue() * foregroundOpacity));
  }

  /**
   * Returns if the action key is held by the user for the given event. The action key is defined as the
   * meta key on mac, and control on other platforms.
   */
  public static boolean isActionKeyDown(@NotNull InputEvent event) {
    return SystemInfo.isMac ? event.isMetaDown() : event.isControlDown();
  }

  /**
   * Returns the action mask for the current platform.<br/>
   * On mac it's {@link InputEvent#META_DOWN_MASK} everything else is {@link InputEvent#CTRL_DOWN_MASK}.
   */
  @JdkConstants.InputEventMask
  @MagicConstant(valuesFromClass = InputEvent.class)
  public static int getActionMask() {
    return SystemInfo.isMac ? META_DOWN_MASK : CTRL_DOWN_MASK;
  }

  /**
   * returns the action mask text for the current platform. On mac, we try to display the unicode char for cmd button.
   */
  public static String getActionKeyText() {
    if (SystemInfo.isMac) {
      Font labelFont = UIUtil.getLabelFont();
      return (labelFont != null && labelFont.canDisplayUpTo(MacKeymapUtil.COMMAND) == -1) ? MacKeymapUtil.COMMAND : "Cmd";
    }
    return "Ctrl";
  }

  /**
   * Returns a separator that is vertically centered. It has a consistent size among Mac and Linux platforms, as {@link JSeparator} on
   * different platforms has different UI and different sizes.
   */
  public static JComponent createHorizontalSeparator() {
    JPanel separatorWrapper = new JPanel(new TabularLayout("*", "*,Fit,*"));
    separatorWrapper.add(new JSeparator(), new TabularLayout.Constraint(1, 0));
    Dimension size = new Dimension(1, 2);
    separatorWrapper.setMinimumSize(size);
    separatorWrapper.setPreferredSize(size);
    separatorWrapper.setOpaque(false);
    return separatorWrapper;
  }

  /**
   * Creates a scroll pane that the vertical scrolling is delegated to upper level scroll pane and its own vertical scroll bar is hidden.
   * This is needed when the outer pane has vertical scrolling and the inner pane has horizontal scrolling.
   */
  @NotNull
  public static JBScrollPane createNestedVScrollPane(@NotNull JComponent component) {
    JBScrollPane scrollPane = new JBScrollPane(component);
    NestedScrollPaneMouseWheelListener.installOn(scrollPane);
    return scrollPane;
  }
}
