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
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.DecimalFormat;
import java.util.List;

/**
 * Chart of distributions
 */
@SuppressWarnings("UseJBColor")
public class DistributionChartComponent extends JPanel {
  // Because this text overlays colored components, it must stay white/gray, and does not change for dark themes. 
  private static final Color TEXT_COLOR = new Color(0xFEFEFE);
  private static final Color API_LEVEL_COLOR = new Color(0, 0, 0, 77);

  private static final int INTER_SECTION_SPACING = 1;

  private static final double MIN_PERCENTAGE_HEIGHT = 0.06;
  private static final double EXPANSION_ON_SELECTION = 1.063882064;
  private static final double RIGHT_GUTTER_PERCENTAGE = 0.209708738;
  private static final int TOP_PADDING = 40;
  private static final int NAME_OFFSET = 50;
  private static final int MIN_API_FONT_SIZE = JBUI.scale(18);
  private static final int MAX_API_FONT_SIZE = JBUI.scale(45);
  private static final int API_OFFSET = 120;
  private static final int NUMBER_OFFSET = 10;

  private static Font MEDIUM_WEIGHT_FONT;
  private static Font REGULAR_WEIGHT_FONT;

  private static Font VERSION_NAME_FONT;
  private static Font VERSION_NUMBER_FONT;
  private static Font TITLE_FONT;


  // These colors do not change for dark vs light theme.
  // These colors come from our UX team and they are very adamant
  // about their exactness. Hardcoding them is a pain.
  private static final Color[] RECT_COLORS = new Color[] {
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
  private DistributionSelectionChangedListener myListener;

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
        int i = 0;
        while (i < myCurrentBottoms.length && y > myCurrentBottoms[i]) {
          ++i;
        }
        if (i < myCurrentBottoms.length) {
          selectDistribution(distributions.get(i));
        }
      }
    });
    loadFonts();
  }

  @Nullable
  private static List<Distribution> getDistributions() {
    return DistributionService.getInstance().getDistributions();
  }

  public void selectDistribution(Distribution d) {
    mySelectedDistribution = d;
    if (myListener != null) {
      myListener.onDistributionSelected(mySelectedDistribution);
    }
    repaint();
  }

  private static void loadFonts() {
    if (MEDIUM_WEIGHT_FONT == null) {
      REGULAR_WEIGHT_FONT = new Font("Sans", Font.PLAIN, 12);
      MEDIUM_WEIGHT_FONT = new Font("Sans", Font.BOLD, 12);
      VERSION_NAME_FONT = REGULAR_WEIGHT_FONT.deriveFont(JBUI.scale((float)16.0));
      VERSION_NUMBER_FONT = REGULAR_WEIGHT_FONT.deriveFont(JBUI.scale((float)20.0));
      TITLE_FONT = MEDIUM_WEIGHT_FONT.deriveFont(JBUI.scale((float)16.0));
    }
  }

  public void registerDistributionSelectionChangedListener(@NotNull DistributionSelectionChangedListener listener) {
    myListener = listener;
  }

  @Override
  public Dimension getMinimumSize() {
    return JBUI.size(300, 300);
  }

  @SuppressWarnings("StringToUpperCaseOrToLowerCaseWithoutLocale")
  @Override
  public void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);

    List<Distribution> distributions = getDistributions();
    if (distributions == null) {
      Runnable update = new Runnable() {
        @Override
        public void run() {
          repaint();
        }
      };
      DistributionService.getInstance().refresh(update, update);
      g.setFont(VERSION_NAME_FONT);
      g.drawString("Loading distribution data ...", NAME_OFFSET, TOP_PADDING);
      return;
    }

    if (myCurrentBottoms == null) {
      myCurrentBottoms = new int[distributions.size()];
    }

    // Draw the proportioned rectangles
    int startY = TOP_PADDING;
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

    g.drawString("Android Platform".toUpperCase(), leftGutter, titleHeight);
    g.drawString("Version".toUpperCase(), leftGutter, titleHeight * 2);
    g.drawString("API Level".toUpperCase(), width - API_OFFSET, titleHeight);
    String accumulativeTitle = "Cumulative".toUpperCase();
    String distributionTitle = "Distribution".toUpperCase();
    g.drawString(accumulativeTitle, totalWidth - titleMetrics.stringWidth(accumulativeTitle), titleHeight);
    g.drawString(distributionTitle, totalWidth - titleMetrics.stringWidth(distributionTitle), titleHeight * 2);

    // We want a padding in between every element
    int heightToDistribute = getBounds().height - INTER_SECTION_SPACING * (distributions.size() - 1) - TOP_PADDING;

    // Keep track of how much of the distribution we've covered so far
    double percentageSum = 0;

    int smallItemCount = 0;
    for (Distribution d : distributions) {
      if (d.getDistributionPercentage() < MIN_PERCENTAGE_HEIGHT) {
        smallItemCount++;
      }
    }
    heightToDistribute -= (int)Math.round(smallItemCount * MIN_PERCENTAGE_HEIGHT * heightToDistribute);

    int i = 0;
    for (Distribution d : distributions) {
      // Draw the colored rectangle
      g.setColor(RECT_COLORS[i % RECT_COLORS.length]);
      double effectivePercentage = Math.max(d.getDistributionPercentage(), MIN_PERCENTAGE_HEIGHT);
      int calculatedHeight = (int)Math.round(effectivePercentage * heightToDistribute);
      int bottom = startY + calculatedHeight;

      if (d.equals(mySelectedDistribution)) {
        g.fillRect(0, bottom - calculatedHeight, width, calculatedHeight);
      } else {
        g.fillRect(leftGutter, bottom - calculatedHeight, normalBoxSize, calculatedHeight);
      }

      // Size our fonts according to the rectangle size
      Font apiLevelFont = REGULAR_WEIGHT_FONT.deriveFont(logistic(effectivePercentage, MIN_API_FONT_SIZE, MAX_API_FONT_SIZE));

      // Measure our font heights so we can center text
      FontMetrics apiLevelMetrics = g.getFontMetrics(apiLevelFont);
      int halfApiFontHeight = (apiLevelMetrics.getHeight() - apiLevelMetrics.getDescent()) / 2;


      int currentMidY = startY + calculatedHeight/2;
      // Write the name
      g.setColor(TEXT_COLOR);
      g.setFont(VERSION_NAME_FONT);
      myCurrentBottoms[i] = bottom;
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
      if (i < distributions.size() - 1) {
        g.setColor(JBColor.foreground());
        g.setFont(VERSION_NUMBER_FONT);
        String percentageString;
        if (percentageSum > 0.999) {
          percentageString = "< 0.1%";
        } else {
          percentageString = new DecimalFormat("0.0%").format(1.0 - percentageSum);
        }
        int percentStringWidth = versionNumberMetrics.stringWidth(percentageString);
        g.drawString(percentageString, totalWidth - percentStringWidth - 2, bottom - 2);
        g.setColor(JBColor.darkGray);
        g.drawLine(leftGutter + normalBoxSize, startY + calculatedHeight, totalWidth, startY + calculatedHeight);
      }


      startY += calculatedHeight + INTER_SECTION_SPACING;
      i++;
    }
  }

  /**
   * Get an S-Curve value between min and max
   * @param normalizedValue a value between 0 and 1
   * @return an integer between the given min and max value
   */
  private static float logistic(double normalizedValue, int min, int max) {
    double t = normalizedValue * 1;
    double result =  (max * min * Math.exp(min * t)) / (max + min * Math.exp(min * t));
    return (float)result;
  }

  public interface DistributionSelectionChangedListener {
    void onDistributionSelected(Distribution d);
  }
}
