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
package com.android.tools.idea.wizard;

import com.android.sdklib.repository.FullRevision;
import com.google.common.collect.Lists;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.JBColor;
import com.intellij.util.ResourceUtil;
import com.intellij.util.ui.GraphicsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Chart of distributions
 */
public class DistributionChartComponent extends JPanel {
  private static final Logger LOG = Logger.getInstance(DistributionChartComponent.class);

  // Because this text overlays colored components, it must stay white/gray, and does not change for dark themes. 
  private static final Color TEXT_COLOR = new Color(0xFEFEFE);
  private static final Color API_LEVEL_COLOR = new Color(0xCCCCCC);

  private static final int INTER_SECTION_SPACING = 1;

  private static final double MIN_PERCENTAGE_HEIGHT = 0.08;
  private static final int TOP_PADDING = 40;
  private static final int LEFT_PADDING = 10;
  private static final int NAME_OFFSET = 5;
  private static final int RIGHT_GUTTER_SIZE = 150;
  public static final int MIN_API_FONT_SIZE = 24;
  public static final int MAX_API_FONT_SIZE = 40;
  public static final int MIN_TEXT_FONT_SIZE = 14;
  public static final int MAX_TEXT_FONT_SIZE = 20;
  public static final int API_OFFSET = 120;
  private static final int TITLE_HEIGHT = 14;

  // These colors do not change for dark vs light theme.
  private static final Color[] COLORS = new Color[] {
    new Color(0xDB4437),
    new Color(0x9C27B0),
    new Color(0x3F51B5),
    new Color(0x4285F4),
    new Color(0x03A9F4),
    new Color(0x0F9D58),
    new Color(0xDC9C00),
    new Color(0xEF4712),
    new Color(0x607D8B),
    new Color(0x795548)
  };


  private List<Distribution> myDistributions = Lists.newArrayList();

  private int[] myCurrentBottoms;
  private Distribution mySelectedDistribution;
  private DistributionSelectionChangedListener myListener;

  public DistributionChartComponent() {
    addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent mouseEvent) {
        int y = mouseEvent.getY();
        int i = 0;
        while (i < myCurrentBottoms.length && y > myCurrentBottoms[i]) {
          ++i;
        }
        if (i < myCurrentBottoms.length) {
          mySelectedDistribution = myDistributions.get(i);
          if (myListener != null) {
            myListener.onDistributionSelected(mySelectedDistribution);
          }
          repaint();
        }
      }
    });
    try {
      String jsonString = ResourceUtil.loadText(ResourceUtil.getResource(this.getClass(), "wizardData", "distributions.json"));
      loadDistributionsFromJson(jsonString);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  private void loadDistributionsFromJson(String jsonString) {
    Type fullRevisionType = new TypeToken<FullRevision>(){}.getType();
    GsonBuilder gsonBuilder = new GsonBuilder()
      .registerTypeAdapter(fullRevisionType, new JsonDeserializer<FullRevision>() {
        @Override
        public FullRevision deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
          return FullRevision.parseRevision(json.getAsString());
        }
      });
    Gson gson = gsonBuilder.create();
    Type listType = new TypeToken<ArrayList<Distribution>>() {}.getType();
    try {
      myDistributions = gson.fromJson(jsonString, listType);
    } catch (JsonParseException e) {
      LOG.error(e);
    }
    myCurrentBottoms = new int[myDistributions.size()];
  }

  public void registerDistributionSelectionChangedListener(@NotNull DistributionSelectionChangedListener listener) {
    myListener = listener;
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(300, 300);
  }

  @Override
  public void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paintComponent(g);

    // Draw the proportioned rectangles
    int startY = TOP_PADDING;
    int totalWidth = getBounds().width;
    int width = totalWidth - RIGHT_GUTTER_SIZE;

    // Draw the titles
    Font titleFont = new Font("SansSerif", Font.BOLD, 14);
    g.setFont(titleFont);
    g.drawString("Api Level", LEFT_PADDING, TITLE_HEIGHT);
    FontMetrics metrics = g.getFontMetrics(titleFont);
    String distributionTitle = "Distribution";
    g.drawString(distributionTitle, width - metrics.stringWidth(distributionTitle), TITLE_HEIGHT);
    String supportedTitle = "Supported";
    g.drawString(supportedTitle, totalWidth - metrics.stringWidth(supportedTitle), TITLE_HEIGHT);
    g.drawString(distributionTitle, totalWidth - metrics.stringWidth(distributionTitle), TITLE_HEIGHT + metrics.getHeight());

    // We want a padding in between every element
    int heightToDistribute = getBounds().height - INTER_SECTION_SPACING * (myDistributions.size() - 1) - TOP_PADDING;

    // Keep track of how much of the distribution we've covered so far
    double percentageSum = 0;

    int smallItemCount = 0;
    for (Distribution d : myDistributions) {
      if (d.distributionPercentage < MIN_PERCENTAGE_HEIGHT) {
        smallItemCount++;
      }
    }
    heightToDistribute -= (int)Math.round(smallItemCount * MIN_PERCENTAGE_HEIGHT * heightToDistribute);

    int i = 0;
    boolean underSelected = false;
    for (Distribution d : myDistributions) {
      if (d.color == null) {
        d.color = COLORS[i % COLORS.length];
      }

      // Draw the colored rectangle
      g.setColor(d.color);
      double effectivePercentage = Math.max(d.distributionPercentage, MIN_PERCENTAGE_HEIGHT);
      int calculatedHeight = (int)Math.round(effectivePercentage * heightToDistribute);
      int bottom = startY + calculatedHeight;

      if (d.equals(mySelectedDistribution) || underSelected) {
        g.fillRect(0, startY, totalWidth, calculatedHeight);
      } else {
        g.fillRect(LEFT_PADDING, startY, width, calculatedHeight);
      }

      // Size our fonts according to the rectangle size
      Font apiLevelFont = new Font("SansSerif", Font.BOLD, logistic(effectivePercentage, MIN_API_FONT_SIZE, MAX_API_FONT_SIZE));
      Font textFont = new Font("SansSerif", Font.PLAIN, logistic(effectivePercentage, MIN_TEXT_FONT_SIZE, MAX_TEXT_FONT_SIZE));

      // Measure our font heights so we can center text
      metrics = g.getFontMetrics(apiLevelFont);
      int halfApiFontHeight = (metrics.getHeight() - metrics.getDescent()) / 2;
      metrics = g.getFontMetrics(textFont);
      int textFontHeight = (metrics.getHeight() - metrics.getDescent());

      int currentMidY = startY + calculatedHeight/2;
      // Write the name
      g.setColor(TEXT_COLOR);
      g.setFont(textFont);
      myCurrentBottoms[i] = bottom;
      g.drawString(d.name, LEFT_PADDING + NAME_OFFSET, Math.min(currentMidY, bottom));

      // Write the version number
      g.setColor(API_LEVEL_COLOR);
      g.setFont(textFont);
      String versionString = d.version.toShortString();
      // Right align name and version string
      int leftOffset = LEFT_PADDING + NAME_OFFSET + metrics.stringWidth(d.name) - metrics.stringWidth(versionString);
      g.drawString(versionString, Math.max(leftOffset, LEFT_PADDING + NAME_OFFSET), Math.min(currentMidY + textFontHeight, bottom));

      // Write the API level
      g.setColor(API_LEVEL_COLOR);
      g.setFont(apiLevelFont);
      g.drawString(Integer.toString(d.apiLevel), width - API_OFFSET, Math.min(currentMidY + halfApiFontHeight, bottom));

      // Write the distribution percentage
      g.setFont(textFont);
      String percentageString = new DecimalFormat("0.0%").format(d.distributionPercentage);
      int percentStringWidth = metrics.stringWidth(percentageString);
      g.drawString(percentageString, width - percentStringWidth, bottom - 3);

      // Write the selected supported distribution
      if (d.equals(mySelectedDistribution)) {
        // Set the flag for the percentage selection
        underSelected = true;
        // Write the percentage sum
        g.setColor(TEXT_COLOR);
        g.setFont(textFont);
        percentageString = new DecimalFormat("0.0%").format(.999 - percentageSum);
        percentStringWidth = metrics.stringWidth(percentageString);
        g.drawString(percentageString, totalWidth - percentStringWidth, startY + metrics.getHeight());
      }

      startY += calculatedHeight + INTER_SECTION_SPACING;
      percentageSum += d.distributionPercentage;
      i++;
    }
  }

  /**
   * Get an S-Curve value between min and max
   * @param normalizedValue a value between 0 and 1
   * @return an integer between the given min and max value
   */
  private static int logistic(double normalizedValue, int min, int max) {
    int k = max;
    int p0 = min;
    int r = p0;
    double t = normalizedValue * 1;
    double result =  (k * p0 * Math.exp(r * t)) / (k + p0 * Math.exp(r * t));
    return (int)Math.round(result);
  }

  protected static class Distribution implements Comparable<Distribution> {
    public static class TextBlock {
      public String title;
      public String body;
    }

    public int apiLevel;
    public FullRevision version;
    public double distributionPercentage;
    public String name;
    public Color color;
    public String description;
    public List<TextBlock> descriptionBlocks;

    private Distribution() {
      // Private default for json conversion
    }

    @Override
    public int compareTo(Distribution other) {
      return Integer.valueOf(apiLevel).compareTo(other.apiLevel);
    }
  }

  public interface DistributionSelectionChangedListener {
    void onDistributionSelected(Distribution d);
  }
}
