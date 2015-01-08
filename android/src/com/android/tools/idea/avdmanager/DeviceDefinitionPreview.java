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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import java.util.List;

import static com.android.tools.idea.avdmanager.AvdWizardConstants.*;

/**
 * A preview component for displaying information about
 * a device definition. This panel displays the dimensions of the device
 * (both physical and in pixels) and some information about the screen
 * size and shape.
 */
public class DeviceDefinitionPreview extends JPanel implements DeviceDefinitionList.DeviceCategorySelectionListener {

  private static final double PIXELS_PER_INCH = 45;
  private static final String NO_DEVICE_SELECTED = "No Device Selected";
  private static final int FIGURE_PADDING = 3;
  private static final DecimalFormat FORMAT = new DecimalFormat(".##\"");
  public static final int DIMENSION_LINE_WIDTH = 1; // px
  public static final int OUTLINE_LINE_WIDTH = 5;   // px
  private Device myDevice;
  double myMaxOutlineHeight;
  double myMaxOutlineWidth;
  private static final int PADDING = 20;

  private static final JBColor OUR_GRAY = new JBColor(Gray._192, Gray._96);

  public void setDevice(@Nullable Device device) {
    myDevice = device;
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
    g2d.setFont(STANDARD_FONT);

    if (myDevice == null) {
      FontMetrics metrics = g2d.getFontMetrics();
      g2d.drawString(NO_DEVICE_SELECTED,
                   (getWidth() - metrics.stringWidth(NO_DEVICE_SELECTED)) / 2,
                   (getHeight() - metrics.getHeight()) / 2 );
      return;
    }

    boolean isCircular = isCircular(myDevice);

    // Paint our icon
    Icon icon = getIcon(myDevice);
    icon.paintIcon(this, g, PADDING/2, PADDING/2);

    // Paint the device name
    g2d.setFont(TITLE_FONT);
    FontMetrics metrics = g.getFontMetrics(TITLE_FONT);
    g2d.drawString(myDevice.getDisplayName(), 50, PADDING + metrics.getHeight() / 2);
    g2d.drawLine(0, 50, getWidth(), 50);

    // Paint the device outline with dimensions labelled
    Dimension screenSize = getScaledDimension(myDevice);
    Dimension pixelScreenSize = myDevice.getScreenSize(myDevice.getDefaultState().getOrientation());
    if (screenSize != null) {
      if (screenSize.getHeight() <= 0) {
        screenSize.height = 1;
      }
      if (screenSize.getWidth() <= 0) {
        screenSize.width = 1;
      }
      RoundRectangle2D roundRect = new RoundRectangle2D.Double(PADDING, 100, screenSize.width, screenSize.height, 10, 10);
      g2d.setStroke(new BasicStroke(DIMENSION_LINE_WIDTH));
      g2d.setColor(OUR_GRAY);

      g2d.setFont(FIGURE_FONT);
      metrics = g2d.getFontMetrics(FIGURE_FONT);
      int stringHeight = metrics.getHeight() - metrics.getDescent();

      // Paint the width dimension
      String widthString = Integer.toString(pixelScreenSize.width) + "px";
      int widthLineY = 95 - (metrics.getHeight() - metrics.getDescent()) / 2;
      g2d.drawLine(PADDING, widthLineY, round(PADDING + roundRect.getWidth()), widthLineY);

      // Erase the part of the line that the text overlays
      g2d.setColor(JBColor.background());
      int widthStringWidth = metrics.stringWidth(widthString);
      int widthTextX = round(PADDING + (roundRect.getWidth() - widthStringWidth) / 2);
      g2d.drawLine(widthTextX - FIGURE_PADDING, widthLineY, widthTextX + widthStringWidth + FIGURE_PADDING, widthLineY);


      // Paint the width text
      g2d.setColor(JBColor.foreground());
      g2d.drawString(widthString, widthTextX, 95);

      // Paint the height dimension
      g2d.setColor(OUR_GRAY);
      String heightString = Integer.toString(pixelScreenSize.height) + "px";
      int heightLineX = round(PADDING + roundRect.getWidth() + 15);
      g2d.drawLine(heightLineX, 100, heightLineX, round(100 + roundRect.getHeight()));

      // Erase the part of the line that the text overlays
      g2d.setColor(JBColor.background());
      int heightTextY = round(100 + (roundRect.getHeight() + stringHeight) / 2);
      g2d.drawLine(heightLineX, heightTextY + FIGURE_PADDING, heightLineX, heightTextY - stringHeight - FIGURE_PADDING);

      // Paint the height text
      g2d.setColor(JBColor.foreground());
      g2d.drawString(heightString, heightLineX - 10, heightTextY);

      // Paint the diagonal dimension
      g2d.setColor(OUR_GRAY);
      String diagString = FORMAT.format(myDevice.getDefaultHardware().getScreen().getDiagonalLength());
      int diagTextX = round(PADDING + (roundRect.getWidth() - metrics.stringWidth(diagString)) / 2);
      int diagTextY = round(100 + (roundRect.getHeight() + stringHeight) / 2);

      Line2D diagLine = new Line2D.Double(PADDING, round(100 + roundRect.getHeight()), round(PADDING + roundRect.getWidth()), 100);
      if (isCircular) {
        // Move the endpoints of the line to within the circle. Each endpoint must move towards the center axis of the circle by
        // 0.5 * (l - l/sqrt(2)) where l is the diameter of the circle.
        double dist = 0.5 * (roundRect.getWidth() - roundRect.getWidth()/Math.sqrt(2));
        diagLine.setLine(diagLine.getX1() + dist, diagLine.getY1() - dist, diagLine.getX2() - dist, diagLine.getY2() + dist);
      }
      g2d.draw(diagLine);

      // Erase the part of the line that the text overlays
      g2d.setColor(JBColor.background());
      Rectangle erasureRect = new Rectangle(diagTextX - FIGURE_PADDING, diagTextY - stringHeight - FIGURE_PADDING,
                                            metrics.stringWidth(diagString) + FIGURE_PADDING * 2, stringHeight + FIGURE_PADDING * 2);
      g2d.fill(erasureRect);

      // Paint the diagonal text
      g2d.setColor(JBColor.foreground());
      g2d.drawString(diagString, diagTextX, diagTextY);

      // Finally, paint the outline
      g2d.setStroke(new BasicStroke(OUTLINE_LINE_WIDTH));
      g2d.setColor(JBColor.foreground());

      if (isCircular) {
        Ellipse2D circle = new Ellipse2D.Double(roundRect.getX(), roundRect.getY(), roundRect.getWidth(), roundRect.getHeight());
        g2d.draw(circle);
      } else {
        g2d.draw(roundRect);
      }

      // Paint the details. If it's a portrait phone, then paint to the right of the rect.
      // If it's a landscape tablet/tv, paint below.
      g2d.setFont(STANDARD_FONT);
      metrics = g2d.getFontMetrics(STANDARD_FONT);
      stringHeight = metrics.getHeight();
      int infoSegmentX;
      int infoSegmentY;
      if (myDevice.getDefaultState().getOrientation().equals(ScreenOrientation.PORTRAIT)) {
        infoSegmentX = round(PADDING + roundRect.getWidth() + metrics.stringWidth(heightString) + PADDING);
        infoSegmentY = 100;
      } else {
        infoSegmentX = PADDING;
        infoSegmentY = round(100 + roundRect.getHeight() + PADDING);
      }
      infoSegmentY += stringHeight;
      ScreenSize size = myDevice.getDefaultHardware().getScreen().getSize();
      if (size != null) {
        g2d.drawString("Size:      " + size.getResourceValue(), infoSegmentX, infoSegmentY);
        infoSegmentY += stringHeight;
      }
      ScreenRatio ratio = myDevice.getDefaultHardware().getScreen().getRatio();
      if (ratio != null) {
        g2d.drawString("Ratio:    " + ratio.getResourceValue(), infoSegmentX, infoSegmentY);
        infoSegmentY += stringHeight;
      }
      Density pixelDensity = myDevice.getDefaultHardware().getScreen().getPixelDensity();
      if (pixelDensity != null) {
        g2d.drawString("Density: " + pixelDensity.getResourceValue(), infoSegmentX, infoSegmentY);
      }
    }
  }

  /**
   * Return true iff the given device has the circular boot prop.
   */
  public static boolean isCircular(@NotNull Device device) {
    String circularProp = device.getBootProps().get("ro.emulator.circular");
    return "true".equals(circularProp);
  }

  private static int round(double d) {
    return (int)Math.round(d);
  }

  /**
   * @return A scaled dimension of the given device's screen that will fit within this component's bounds.
   */
  @Nullable
  private Dimension getScaledDimension(@NotNull Device device) {
    Dimension pixelSize = device.getScreenSize(device.getDefaultState().getOrientation());
    if (pixelSize == null) {
      return null;
    }
    double diagonal = device.getDefaultHardware().getScreen().getDiagonalLength();
    double sideRatio = pixelSize.getWidth() / pixelSize.getHeight();
    double pixelHeight = diagonal / Math.sqrt(1 + sideRatio);
    double pixelWidth = sideRatio * pixelHeight;
    double scalingFactor = 2 * Math.max(myMaxOutlineHeight / getHeight(), myMaxOutlineWidth / getWidth());
    return new Dimension((int)(pixelWidth / scalingFactor), (int)(pixelHeight / scalingFactor));
  }

  /**
   * @return an icon representing the given device's form factor. Defaults to Mobile if the form factor
   * can not be detected.
   */
  @NotNull
  public static Icon getIcon(@Nullable Device device) {
    if (device == null) {
      return AndroidIcons.FormFactors.Mobile_32;
    }
    if (HardwareConfigHelper.isTv(device)) {
      return AndroidIcons.FormFactors.Tv_32;
    } else if (HardwareConfigHelper.isWear(device)) {
      return AndroidIcons.FormFactors.Wear_32;
    } else {
      return AndroidIcons.FormFactors.Mobile_32;
    }
  }

  @Override
  public void onCategorySelectionChanged(@Nullable String category, @Nullable List<Device> devices) {
    if (devices == null) {
      myMaxOutlineHeight = 0;
      myMaxOutlineWidth = 0;
    }
    double maxHeight = 0;
    double maxWidth = 0;
    for (Device d : devices) {
      Dimension pixelSize = d.getScreenSize(d.getDefaultState().getOrientation());
      if (pixelSize == null) {
        continue;
      }
      double diagonal = d.getDefaultHardware().getScreen().getDiagonalLength();
      double sideRatio = pixelSize.getWidth() / pixelSize.getHeight();
      double heightIn = diagonal / Math.sqrt(1 + sideRatio * sideRatio);
      double widthIn = sideRatio * heightIn;

      maxWidth = Math.max(maxWidth, widthIn);
      maxHeight = Math.max(maxHeight, heightIn);
    }
    myMaxOutlineHeight = maxHeight;
    myMaxOutlineWidth = maxWidth;
  }
}
