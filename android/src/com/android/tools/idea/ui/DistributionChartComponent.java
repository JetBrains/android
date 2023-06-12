/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.ui;

import com.android.tools.idea.stats.Distribution;
import com.android.tools.idea.stats.DistributionService;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chart of distributions
 */
@SuppressWarnings("UseJBColor")
public class DistributionChartComponent extends JPanel {
  public interface SelectionChangedListener {
    void onDistributionSelected(@NotNull Distribution d);
  }

  // Because this text overlays colored components, it must stay white/gray, and does not change for dark themes.
  private static final Color TEXT_COLOR = new Color(0xFEFEFE);
  private static final Color API_LEVEL_COLOR = new Color(0, 0, 0, 77);

  private final int INTER_SECTION_SPACING = JBUIScale.scale(1);
  private final int HEADER_TO_BODY_SPACING = JBUIScale.scale(4);

  /* Strings appearing in the header of the distribution table */
  private static final String STR_ANDROID_PLATFORM = "ANDROID PLATFORM";
  private static final String STR_VERSION = "VERSION";
  private static final String STR_API_LEVEL = "API LEVEL";
  private static final String STR_CUMULATIVE = "CUMULATIVE";
  private static final String STR_DISTRIBUTION = "DISTRIBUTION";
  private static final String STR_LOADING = "Loading distribution data ...";

  private static final double MIN_PERCENTAGE_HEIGHT = 0.01;
  private static final double EXPANSION_ON_SELECTION = 1.06;
  private static final double RIGHT_GUTTER_PERCENTAGE = 0.21;
  private final int TOP_PADDING = JBUIScale.scale(40);
  private final int NAME_OFFSET = JBUIScale.scale(50);
  private final int MIN_API_FONT_SIZE = JBUIScale.scale(18);
  private final int MAX_API_FONT_SIZE = JBUIScale.scale(45);
  private final int API_OFFSET = JBUIScale.scale(120);
  private final int NUMBER_OFFSET = JBUIScale.scale(10);

  private static Font MEDIUM_WEIGHT_FONT;
  private static Font REGULAR_WEIGHT_FONT;

  private static Font VERSION_NAME_FONT;
  private static Font VERSION_NUMBER_FONT;
  private static Font TITLE_FONT;


  // These colors do not change for dark vs light theme.
  // These colors come from our UX team and they are very adamant
  // about their exactness. Hardcoding them is a pain.
  private static final Color[] RECT_COLORS = {
    new Color(0xcbdfcb),
    new Color(0x7dc691),
    new Color(0x92b2b7),
    new Color(0xdeba40),
    new Color(0xe55d5f),
    new Color(0x6ec0d2),
    new Color(0xd88d63),
    new Color(0xff9229),
    new Color(0xeabd2d)
  };

  private int[] myCurrentBottoms;
  private Distribution mySelectedDistribution;
  private SelectionChangedListener myListener;
  private int mySelectedApiLevel = -1;

  public void init() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        List<Distribution> distributions = getDistributions();
        assert distributions != null;
        if (myCurrentBottoms == null || myCurrentBottoms.length != distributions.size()) {
          return;
        }
        int y = mouseEvent.getY();
        for (int i = 0; i < myCurrentBottoms.length; i++) {
          if (y <= myCurrentBottoms[i]) {
            selectDistribution(distributions.get(i));
            break;
          }
        }
      }
    });
    loadFonts();
  }

  @Nullable
  private static List<Distribution> getDistributions() {
    return DistributionService.getInstance().getDistributions();
  }

  /**
   * Selects the distribution with the given API level. If the distributions
   * are not yet loaded, this call will not cause them to be loaded, but once
   * they are loaded the distribution with the given api level will be selected.
   */
  public void selectDistributionApiLevel(int api) {
    mySelectedApiLevel = api;
    List<Distribution> distributions = getDistributions();
    if (distributions != null) {
      for (Distribution d : distributions) {
        if (d.getApiLevel() == api) {
          selectDistribution(d);
          break;
        }
      }
    }
  }

  public void selectDistribution(@NotNull Distribution d) {
    mySelectedDistribution = d;
    mySelectedApiLevel = d.getApiLevel();
    if (myListener != null) {
      myListener.onDistributionSelected(mySelectedDistribution);
    }
    repaint();
  }

  private static void loadFonts() {
    if (MEDIUM_WEIGHT_FONT == null) {
      REGULAR_WEIGHT_FONT = new Font("DroidSans", Font.PLAIN, 12);
      MEDIUM_WEIGHT_FONT = new Font("DroidSans", Font.BOLD, 12);
      VERSION_NAME_FONT = REGULAR_WEIGHT_FONT.deriveFont(JBUIScale.scale((float)14));
      VERSION_NUMBER_FONT = REGULAR_WEIGHT_FONT.deriveFont(JBUIScale.scale((float)14.0));
      TITLE_FONT = MEDIUM_WEIGHT_FONT.deriveFont(JBUIScale.scale((float)14.0));
    }
  }

  public void registerDistributionSelectionChangedListener(@NotNull SelectionChangedListener listener) {
    myListener = listener;
  }

  @Override
  public Dimension getMinimumSize() {
    return JBUI.size(450, 450);
  }

  @Override
  public void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g, true, true);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);

    List<Distribution> distributions = getDistributions();
    if (distributions == null) {
      final DistributionService service = DistributionService.getInstance();
      Runnable update = () -> {
        if (mySelectedApiLevel > -1 && mySelectedDistribution == null) {
          final Distribution distribution = service.getDistributionForApiLevel(mySelectedApiLevel);
          if (distribution != null) {
            UIUtil.invokeLaterIfNeeded(() -> selectDistribution(distribution));
          }
        }
        repaint();
      };
      service.refresh(update, update);
      g.setFont(VERSION_NAME_FONT);
      g.drawString(STR_LOADING, NAME_OFFSET, TOP_PADDING);
      return;
    }

    if (myCurrentBottoms == null) {
      myCurrentBottoms = new int[distributions.size()];
    }

    // Draw the proportioned rectangles
    int totalWidth = getBounds().width;
    int rightGutter = (int)Math.round(totalWidth * RIGHT_GUTTER_PERCENTAGE);
    int width = totalWidth - rightGutter;
    int normalBoxSize = (int)Math.round((float)width/EXPANSION_ON_SELECTION);
    int leftGutter = (width - normalBoxSize) / 2;

    // Measure our fonts
    FontMetrics titleMetrics = g.getFontMetrics(TITLE_FONT);
    int titleHeight = titleMetrics.getHeight();
    FontMetrics versionNumberMetrics = g.getFontMetrics(VERSION_NUMBER_FONT);
    int halfVersionNumberHeight = (versionNumberMetrics.getHeight() - versionNumberMetrics.getDescent()) / 2;
    FontMetrics versionNameMetrics = g.getFontMetrics(VERSION_NAME_FONT);
    int halfVersionNameHeight = (versionNameMetrics.getHeight() - versionNameMetrics.getDescent()) / 2;

    // Draw the titles
    g.setFont(TITLE_FONT);
    g.drawString(STR_ANDROID_PLATFORM,
                 leftGutter + API_OFFSET - (titleMetrics.stringWidth(STR_ANDROID_PLATFORM) / 2),
                 titleHeight);
    g.drawString(STR_VERSION,
                 leftGutter + API_OFFSET - (titleMetrics.stringWidth(STR_VERSION) / 2),
                 titleHeight * 2);
    g.drawString(STR_API_LEVEL, width - API_OFFSET, titleHeight);

    int lastColumnLeftOffset = totalWidth - Math.max(titleMetrics.stringWidth(STR_CUMULATIVE), titleMetrics.stringWidth(STR_DISTRIBUTION));
    g.drawString(STR_CUMULATIVE, (lastColumnLeftOffset + totalWidth - titleMetrics.stringWidth(STR_CUMULATIVE)) / 2, titleHeight);
    g.drawString(STR_DISTRIBUTION, (lastColumnLeftOffset + totalWidth - titleMetrics.stringWidth(STR_DISTRIBUTION)) / 2, titleHeight * 2);

    double percentageToDistribute = 0;
    for (Distribution d : distributions) {
      double percentageAboveMin = d.getDistributionPercentage() - MIN_PERCENTAGE_HEIGHT;
      if (percentageAboveMin > 0) {
        percentageToDistribute += percentageAboveMin;
      }
    }

    // We want a padding in between every element
    int heightToDistribute = getBounds().height - INTER_SECTION_SPACING * (distributions.size() - 1) - TOP_PADDING;

    Font minApiLevelFont = REGULAR_WEIGHT_FONT.deriveFont(logistic(MIN_PERCENTAGE_HEIGHT));
    int minBoxHeight = Math.max((int)Math.round(MIN_PERCENTAGE_HEIGHT * heightToDistribute), g.getFontMetrics(minApiLevelFont).getHeight());

    heightToDistribute = Math.max(0, heightToDistribute - distributions.size() * minBoxHeight);

    // Keep track of how much of the distribution we've covered so far
    double percentageSum = 0;
    int startY = (2 * titleHeight) + HEADER_TO_BODY_SPACING;
    int recColorIdx = 0;
    for (Distribution d : distributions) {
      int calculatedHeight;
      Font apiLevelFont;
      if (d.getDistributionPercentage() <= MIN_PERCENTAGE_HEIGHT) {
        calculatedHeight = minBoxHeight;
        apiLevelFont = minApiLevelFont;
      }
      else {
        double extraPercentage = d.getDistributionPercentage() - MIN_PERCENTAGE_HEIGHT;
        calculatedHeight = minBoxHeight + (int)Math.round(heightToDistribute * extraPercentage / percentageToDistribute);
        apiLevelFont = REGULAR_WEIGHT_FONT.deriveFont(logistic(MIN_PERCENTAGE_HEIGHT + extraPercentage / percentageToDistribute));
      }

      int bottom = startY + calculatedHeight;

      // Draw the colored rectangle
      g.setColor(RECT_COLORS[recColorIdx % RECT_COLORS.length]);
      if (d.equals(mySelectedDistribution)) {
        g.fillRect(0, bottom - calculatedHeight, width, calculatedHeight);
      } else {
        g.fillRect(leftGutter, bottom - calculatedHeight, normalBoxSize, calculatedHeight);
      }

      // Measure our font heights so we can center text
      FontMetrics apiLevelMetrics = g.getFontMetrics(apiLevelFont);
      int halfApiFontHeight = apiLevelMetrics.getAscent() * 43 / 100; // Slightly less than half for better vertical alignment.

      int currentMidY = startY + calculatedHeight / 2;
      // Write the name
      g.setColor(TEXT_COLOR);
      g.setFont(VERSION_NAME_FONT);
      myCurrentBottoms[recColorIdx] = bottom;
      g.drawString(d.getName(), leftGutter + NAME_OFFSET, currentMidY + halfVersionNameHeight);

      // Write the version number
      g.setColor(API_LEVEL_COLOR);
      g.setFont(VERSION_NUMBER_FONT);
      String versionString = d.getVersion().toString().substring(0, 3);
      g.drawString(versionString, leftGutter + NUMBER_OFFSET, currentMidY + halfVersionNumberHeight);

      // Write the API level
      g.setFont(apiLevelFont);
      g.drawString(Integer.toString(d.getApiLevel()), width - API_OFFSET, currentMidY + halfApiFontHeight);

      // Write the supported distribution
      percentageSum += d.getDistributionPercentage();
      // Write the percentage sum
      if (recColorIdx < distributions.size() - 1) {
        g.setColor(JBColor.foreground());
        g.setFont(VERSION_NUMBER_FONT);
        String percentageStr = (percentageSum > 0.999) ? "< 0.1%" : new DecimalFormat("0.0%").format(1.0 - percentageSum);
        int percentStringWidth = versionNumberMetrics.stringWidth(percentageStr);
        g.drawString(percentageStr, totalWidth - percentStringWidth - 2, versionNameMetrics.getAscent() + bottom);
        g.setColor(JBColor.darkGray);
        g.drawLine(leftGutter + normalBoxSize, startY + calculatedHeight, totalWidth, startY + calculatedHeight);
      }

      startY += calculatedHeight + INTER_SECTION_SPACING;
      recColorIdx++;
    }
  }

  /**
   * Get an S-Curve value between min and max
   * @param normalizedValue a value between 0 and 1
   * @return an integer between the given MIN_API_FONT_SIZE and MAX_API_FONT_SIZE value
   */
  private float logistic(double normalizedValue) {
    double exp = Math.exp(MIN_API_FONT_SIZE * normalizedValue);
    return (float)((MAX_API_FONT_SIZE * MIN_API_FONT_SIZE * exp) /
                   (MAX_API_FONT_SIZE + MIN_API_FONT_SIZE * exp));
  }
}
