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
  private static final double EXPANSION_ON_SELECTION = 1.063882064;
  private static final double RIGHT_GUTTER_PERCENTAGE = 0.209708738;
  private static final int TOP_PADDING = 40;
  private static final int NAME_OFFSET = 10;
  private static final int MIN_API_FONT_SIZE = 18;
  private static final int MAX_API_FONT_SIZE = 45;
  private static final int API_OFFSET = 120;

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
    new Color(0x0),
    new Color(0xdeba40),
    new Color(0xe55d5f),
    new Color(0x6ec0d2),
    new Color(0xd88d63),
    new Color(0xff9229)
  };

  private static final Color[] TEXT_COLORS = new Color[] {
    new Color(0xb4ccb9),
    new Color(0x4f8a60),
    new Color(0x0),
    new Color(0x9c8023),
    new Color(0xbd2e2e),
    new Color(0x428a9c),
    new Color(0xa7643f),
    new Color(0xca7019)
  };


  private List<Distribution> myDistributions = Lists.newArrayList();

  private int[] myCurrentBottoms;
  private Distribution mySelectedDistribution;
  private DistributionSelectionChangedListener myListener;

  public void init() {
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
      throw new RuntimeException(e);
    }
    loadFonts();
  }

  private static void loadFonts() {
    if (MEDIUM_WEIGHT_FONT == null) {
      REGULAR_WEIGHT_FONT = new Font("Sans", Font.PLAIN, 12);
      MEDIUM_WEIGHT_FONT = new Font("Sans", Font.BOLD, 12);
      VERSION_NAME_FONT = REGULAR_WEIGHT_FONT.deriveFont((float)16.0);
      VERSION_NUMBER_FONT = REGULAR_WEIGHT_FONT.deriveFont((float)20.0);
      TITLE_FONT = MEDIUM_WEIGHT_FONT.deriveFont((float)16.0);
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
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);

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
    int versionNumberHeight = versionNumberMetrics.getHeight() - versionNumberMetrics.getDescent();
    FontMetrics versionNameMetrics = g.getFontMetrics(VERSION_NAME_FONT);

    // Draw the titles
    g.setFont(TITLE_FONT);

    g.drawString("API Level".toUpperCase(), leftGutter, titleHeight);
    String distributionTitle = "Distribution".toUpperCase();
    String accumulativeTitle = "Cumulative".toUpperCase();
    g.drawString(accumulativeTitle, totalWidth - titleMetrics.stringWidth(accumulativeTitle), titleHeight);
    g.drawString(distributionTitle, totalWidth - titleMetrics.stringWidth(distributionTitle), titleHeight * 2);

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
    for (Distribution d : myDistributions) {
      if (d.color == null) {
        d.color = RECT_COLORS[i % RECT_COLORS.length];
      }

      // Draw the colored rectangle
      g.setColor(d.color);
      double effectivePercentage = Math.max(d.distributionPercentage, MIN_PERCENTAGE_HEIGHT);
      int calculatedHeight = (int)Math.round(effectivePercentage * heightToDistribute);
      int boxHeight = Math.max(1, (int)Math.round(d.distributionPercentage * heightToDistribute));
      int bottom = startY + calculatedHeight;

      if (d.equals(mySelectedDistribution)) {
        g.fillRect(0, bottom - boxHeight, width, boxHeight);
      } else {
        g.fillRect(leftGutter, bottom - boxHeight, normalBoxSize, boxHeight);
      }

      // Size our fonts according to the rectangle size
      Font apiLevelFont = REGULAR_WEIGHT_FONT.deriveFont(logistic(effectivePercentage, MIN_API_FONT_SIZE, MAX_API_FONT_SIZE));

      // Measure our font heights so we can center text
      FontMetrics apiLevelMetrics = g.getFontMetrics(apiLevelFont);
      int halfApiFontHeight = (apiLevelMetrics.getHeight() - apiLevelMetrics.getDescent()) / 2;


      int currentMidY = startY + calculatedHeight/2;
      // Write the name
      if (effectivePercentage == d.distributionPercentage) {
        g.setColor(TEXT_COLOR);
      } else {
        g.setColor(TEXT_COLORS[i % RECT_COLORS.length]);
      }
      g.setFont(VERSION_NAME_FONT);
      myCurrentBottoms[i] = bottom;
      g.drawString(d.name, leftGutter + NAME_OFFSET, Math.min(currentMidY, bottom));

      // Write the version number
      g.setColor(TEXT_COLORS[i % RECT_COLORS.length]);
      g.setFont(VERSION_NUMBER_FONT);
      String versionString = d.version.toShortString();
      g.drawString(versionString, leftGutter + NAME_OFFSET, Math.min(currentMidY + versionNumberHeight, bottom));

      // Write the API level
      g.setFont(apiLevelFont);
      g.drawString(Integer.toString(d.apiLevel), width - API_OFFSET, Math.min(currentMidY + halfApiFontHeight, bottom));

      // Write the supported distribution
      percentageSum += d.distributionPercentage;
      // Write the percentage sum
      if (i < myDistributions.size() - 1) {
        g.setColor(JBColor.foreground());
        g.setFont(VERSION_NUMBER_FONT);
        String percentageString = new DecimalFormat("0.0%").format(.999 - percentageSum);
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

  public double getSupportedDistributionForApiLevel(int apiLevel) {
    double unsupportedSum = 0;
    for (Distribution d : myDistributions) {
      if (d.apiLevel >= apiLevel) {
        break;
      }
      unsupportedSum += d.distributionPercentage;
    }
    return 1 - unsupportedSum;
  }
}
