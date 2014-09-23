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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.SdkVersionInfo;
import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.UIUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.SystemImageDescription;

/**
 * Displays information about a {@link com.android.sdklib.SystemImage}, including its
 * launch graphic, platform and API level, and target CPU architecture.
 */
public class SystemImagePreview extends JPanel {
  private static final String NO_SYSTEM_IMAGE_SELECTED = "No System Image Selected";
  private static final int FIGURE_PADDING = 3;
  private SystemImageDescription myImageDescription;
  private static final int PADDING = 20;

  /**
   * Set the image to display.
   */
  public void setImage(@Nullable SystemImageDescription image) {
    myImageDescription = image;
    repaint();
  }

  @Override
  protected void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setColor(JBColor.background());
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setColor(JBColor.foreground());
    g2d.setFont(AvdWizardConstants.STANDARD_FONT);

    if (myImageDescription == null) {
      FontMetrics metrics = g2d.getFontMetrics();
      g2d.drawString(NO_SYSTEM_IMAGE_SELECTED,
                   (getWidth() - metrics.stringWidth(NO_SYSTEM_IMAGE_SELECTED)) / 2,
                   (getHeight() - metrics.getHeight()) / 2 );
      return;
    }

    // Paint the device name
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    FontMetrics metrics = g.getFontMetrics(AvdWizardConstants.TITLE_FONT);
    String codeName = getCodeName(myImageDescription);
    g2d.drawString(codeName, PADDING, PADDING + metrics.getHeight() / 2);
    g2d.drawLine(0, 50, getWidth(), 50);

    // Paint our icon
    Icon icon = getIcon(codeName);
    if (icon != null) {
      icon.paintIcon(this, g, FIGURE_PADDING, PADDING + 50);
    }

    // Paint the details.
    int stringHeight = g2d.getFontMetrics(AvdWizardConstants.TITLE_FONT).getHeight();
    int figureHeight = g2d.getFontMetrics(AvdWizardConstants.FIGURE_FONT).getHeight();
    int infoSegmentX = FIGURE_PADDING + PADDING + 128;
    int infoSegmentY = PADDING + 75;

    // Paint the API Level
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("API Level", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.target.getVersion().getApiString(), infoSegmentX, infoSegmentY);
    infoSegmentY += PADDING;

    // Paint the platform version
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("Android", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.target.getVersionName(), infoSegmentX, infoSegmentY);

    // Paint the vendor name
    String vendorName = myImageDescription.target.getVendor();
    if (metrics.stringWidth(vendorName) > 128) {
      // Split into two lines
      Iterable<String> parts = Splitter.on(CharMatcher.WHITESPACE).omitEmptyStrings().split(vendorName);
      String currentLine = "";
      for (String part : parts) {
        if (metrics.stringWidth(currentLine) >= 128) {
          infoSegmentY += stringHeight;
          g2d.drawString(currentLine, infoSegmentX, infoSegmentY);
          currentLine = "";
        }
        currentLine += part + " ";
      }
      if (!currentLine.isEmpty()) {
        infoSegmentY += stringHeight;
        g2d.drawString(currentLine, infoSegmentX, infoSegmentY);
      }
    } else {
      infoSegmentY += stringHeight;
      g2d.drawString(vendorName, infoSegmentX, infoSegmentY);
    }
    infoSegmentY += PADDING;

    // Paint the CPU architecture
    infoSegmentY += figureHeight;
    g2d.setFont(AvdWizardConstants.FIGURE_FONT);
    g2d.drawString("System Image", infoSegmentX, infoSegmentY);
    infoSegmentY += stringHeight;
    g2d.setFont(AvdWizardConstants.TITLE_FONT);
    g2d.drawString(myImageDescription.systemImage.getAbiType(), infoSegmentX, infoSegmentY);

    // If this API level is deprecated, paint a warning
    if (myImageDescription.target.getVersion().getApiLevel() < SdkVersionInfo.LOWEST_ACTIVE_API) {
      infoSegmentY += stringHeight * 2;
      g2d.setFont(AvdWizardConstants.TITLE_FONT);
      g2d.drawString("This API Level is Deprecated", PADDING, infoSegmentY);
    }
  }

  /**
   * @return the codename for the given System Image's API level
   */
  public static String getCodeName(@NotNull SystemImageDescription description) {
    return SdkVersionInfo.getCodeName(description.target.getVersion().getApiLevel());
  }

  /**
   * Get the launch graphic which corresponds with the given codename, or a question mark
   * if we don't have an icon for that codename.
   */
  @Nullable
  public static Icon getIcon(@Nullable String codename) {
    if (codename == null) {
      return null;
    }
    try {
      return IconLoader.getIcon(String.format("/icons/versions/%1$s.png", codename), AndroidIcons.class);
    } catch (RuntimeException e) {
      int size = 128;
      Image image = UIUtil.createImage(size, size, BufferedImage.TYPE_INT_ARGB);
      Graphics g = image.getGraphics();
      GraphicsUtil.setupAntialiasing(g);
      GraphicsUtil.setupAAPainting(g);
      Font f = UIUtil.getLabelFont();
      Font font = new Font(f.getName(), f.getStyle() | Font.BOLD, 100);
      g.setColor(JBColor.background());
      g.fillRect(0, 0, size, size);
      g.setColor(JBColor.foreground());
      g.setFont(font);
      int height = g.getFontMetrics().getHeight();
      int width = g.getFontMetrics().stringWidth("?");
      g.drawString("?", (size - width) / 2, height + (size - height) / 2);
      return new ImageIcon(image);
    }
  }
}
