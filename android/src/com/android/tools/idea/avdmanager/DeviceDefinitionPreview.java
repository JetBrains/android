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

import com.android.annotations.NonNull;
import com.android.resources.Density;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.tools.idea.ui.properties.InvalidationListener;
import com.android.tools.idea.ui.properties.ObservableValue;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBUI;
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

  private static final int FIGURE_PADDING = JBUI.scale(3);
  private static final DecimalFormat FORMAT = new DecimalFormat(".##\"");
  public static final int DIMENSION_LINE_WIDTH = JBUI.scale(1); // px
  public static final int OUTLINE_LINE_WIDTH = JBUI.scale(5);   // px
  double myMaxOutlineHeight;
  double myMaxOutlineWidth;
  double myMinOutlineHeightIn;
  double myMinOutlineWidthIn;
  private static final int PADDING = JBUI.scale(20);
  private final AvdDeviceData myDeviceData;

  private static final JBColor OUR_GRAY = new JBColor(Gray._192, Gray._96);

  private InvalidationListener myRepaintListener = new InvalidationListener() {
    @Override
    public void onInvalidated(@NotNull ObservableValue<?> sender) {
      repaint();
    }
  };

  public DeviceDefinitionPreview(@NotNull AvdDeviceData deviceData) {
    myDeviceData = deviceData;
    addListeners();
  }

  /**
   * @return an icon representing the given device's form factor. Defaults to Mobile if the form factor
   * can not be detected.
   */
  @NotNull
  public static Icon getIcon(@Nullable AvdDeviceData deviceData) {
    Icon icon = AndroidIcons.FormFactors.Mobile_32;
    if (deviceData != null) {
      if (deviceData.isTv().get()) {
        icon = AndroidIcons.FormFactors.Tv_32;
      }
      else if (deviceData.isWear().get()) {
        icon = AndroidIcons.FormFactors.Wear_32;
      }
    }
    return icon;
  }

  private void addListeners() {
    myDeviceData.supportsLandscape().addWeakListener(myRepaintListener);
    myDeviceData.supportsPortrait().addWeakListener(myRepaintListener);
    myDeviceData.name().addWeakListener(myRepaintListener);
    myDeviceData.screenResolutionWidth().addWeakListener(myRepaintListener);
    myDeviceData.screenResolutionHeight().addWeakListener(myRepaintListener);
    myDeviceData.deviceType().addWeakListener(myRepaintListener);
    myDeviceData.diagonalScreenSize().addWeakListener(myRepaintListener);
    myDeviceData.isScreenRound().addWeakListener(myRepaintListener);
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

    boolean isCircular = myDeviceData.isWear().get() && myDeviceData.isScreenRound().get();

    // Paint our icon
    Icon icon = getIcon(myDeviceData);
    icon.paintIcon(this, g, PADDING / 2, PADDING / 2);

    // Paint the device name
    g2d.setFont(TITLE_FONT);
    FontMetrics metrics = g.getFontMetrics(TITLE_FONT);
    g2d.drawString(myDeviceData.name().get(), JBUI.scale(50), PADDING + metrics.getHeight() / 2);
    g2d.drawLine(0, JBUI.scale(50), getWidth(), JBUI.scale(50));

    // Paint the device outline with dimensions labelled
    Dimension screenSize = getScaledDimension();
    Dimension pixelScreenSize = getScreenDimension(getDefaultDeviceOrientation());
    if (screenSize != null) {
      if (screenSize.getHeight() <= 0) {
        screenSize.height = 1;
      }
      if (screenSize.getWidth() <= 0) {
        screenSize.width = 1;
      }
      RoundRectangle2D roundRect =
        new RoundRectangle2D.Double(PADDING, JBUI.scale(100), screenSize.width, screenSize.height, JBUI.scale(10), JBUI.scale(10));
      g2d.setStroke(new BasicStroke(DIMENSION_LINE_WIDTH));
      g2d.setColor(OUR_GRAY);

      g2d.setFont(FIGURE_FONT);
      metrics = g2d.getFontMetrics(FIGURE_FONT);
      int stringHeight = metrics.getHeight() - metrics.getDescent();

      // Paint the width dimension
      String widthString = Integer.toString(pixelScreenSize.width) + "px";
      int widthLineY = JBUI.scale(95) - (metrics.getHeight() - metrics.getDescent()) / 2;
      g2d.drawLine(PADDING, widthLineY, round(PADDING + screenSize.width), widthLineY);

      // Erase the part of the line that the text overlays
      g2d.setColor(JBColor.background());
      int widthStringWidth = metrics.stringWidth(widthString);
      int widthTextX = round(PADDING + (screenSize.width - widthStringWidth) / 2);
      g2d.drawLine(widthTextX - FIGURE_PADDING, widthLineY, widthTextX + widthStringWidth + FIGURE_PADDING, widthLineY);


      // Paint the width text
      g2d.setColor(JBColor.foreground());
      g2d.drawString(widthString, widthTextX, JBUI.scale(95));

      // Paint the height dimension
      g2d.setColor(OUR_GRAY);
      String heightString = Integer.toString(pixelScreenSize.height) + "px";
      int heightLineX = round(PADDING + screenSize.width + JBUI.scale(15));
      g2d.drawLine(heightLineX, JBUI.scale(100), heightLineX, round(JBUI.scale(100) + screenSize.height));

      // Erase the part of the line that the text overlays
      g2d.setColor(JBColor.background());
      int heightTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2);
      g2d.drawLine(heightLineX, heightTextY + FIGURE_PADDING, heightLineX, heightTextY - stringHeight - FIGURE_PADDING);

      // Paint the height text
      g2d.setColor(JBColor.foreground());
      g2d.drawString(heightString, heightLineX - JBUI.scale(10), heightTextY);

      // Paint the diagonal dimension
      g2d.setColor(OUR_GRAY);
      String diagString = FORMAT.format(myDeviceData.diagonalScreenSize().get());
      int diagTextX = round(PADDING + (screenSize.width - metrics.stringWidth(diagString)) / 2);
      int diagTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2);

      double chin = (double)myDeviceData.screenChinSize().get();
      chin *= screenSize.getWidth() / getScreenDimension(getDefaultDeviceOrientation()).getWidth();
      Line2D diagLine = new Line2D.Double(PADDING, JBUI.scale(100) + screenSize.height + chin, PADDING + screenSize.width, JBUI.scale(100));
      if (isCircular) {
        // Move the endpoints of the line to within the circle. Each endpoint must move towards the center axis of the circle by
        // 0.5 * (l - l/sqrt(2)) where l is the diameter of the circle.
        double dist = 0.5 * (screenSize.width - screenSize.width / Math.sqrt(2));
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
        double x = roundRect.getX();
        double y = roundRect.getY();
        Ellipse2D circle = new Ellipse2D.Double(x, y, screenSize.width, screenSize.height + chin);
        g2d.draw(circle);
        if (chin > 0) {
          erasureRect = new Rectangle((int)x, (int)(y + screenSize.height + OUTLINE_LINE_WIDTH / 2 + 1), screenSize.width,
                                      (int)chin + OUTLINE_LINE_WIDTH / 2 + 1);
          g2d.setColor(JBColor.background());
          g2d.fill(erasureRect);
          g2d.setColor(JBColor.foreground());
          double halfChinWidth = Math.sqrt(chin * (screenSize.width - chin)) - OUTLINE_LINE_WIDTH / 2;
          int chinX = (int)(x + screenSize.width / 2 - halfChinWidth);
          g2d.drawLine(chinX, (int)(y + screenSize.height), (int)(chinX + halfChinWidth * 2), (int)(y + screenSize.height));
        }
      }
      else {
        g2d.draw(roundRect);
      }

      // Paint the details. If it's a portrait phone, then paint to the right of the rect.
      // If it's a landscape tablet/tv, paint below.
      g2d.setFont(STANDARD_FONT);
      metrics = g2d.getFontMetrics(STANDARD_FONT);
      stringHeight = metrics.getHeight();
      int infoSegmentX;
      int infoSegmentY;
      if (getDefaultDeviceOrientation().equals(ScreenOrientation.PORTRAIT)) {
        infoSegmentX = round(PADDING + screenSize.width + metrics.stringWidth(heightString) + PADDING);
        infoSegmentY = JBUI.scale(100);
      }
      else {
        infoSegmentX = PADDING;
        infoSegmentY = round(JBUI.scale(100) + screenSize.height + PADDING);
      }
      infoSegmentY += stringHeight;
      ScreenSize size = AvdScreenData.getScreenSize(myDeviceData.diagonalScreenSize().get());

      g2d.drawString("Size:      " + size.getResourceValue(), infoSegmentX, infoSegmentY);
      infoSegmentY += stringHeight;

      ScreenRatio ratio =
        AvdScreenData.getScreenRatio(myDeviceData.screenResolutionWidth().get(), myDeviceData.screenResolutionHeight().get());
      g2d.drawString("Ratio:    " + ratio.getResourceValue(), infoSegmentX, infoSegmentY);
      infoSegmentY += stringHeight;

      Density pixelDensity = (myDeviceData.isTv().get()) ? Density.TV : AvdScreenData.getScreenDensity(myDeviceData.screenDpi().get());

      g2d.drawString("Density: " + pixelDensity.getResourceValue(), infoSegmentX, infoSegmentY);
    }
  }

  private static int round(double d) {
    return (int)Math.round(d);
  }

  private ScreenOrientation getDefaultDeviceOrientation() {
    return (myDeviceData.supportsPortrait().get())
           ? ScreenOrientation.PORTRAIT : (myDeviceData.supportsLandscape().get()) ? ScreenOrientation.LANDSCAPE : ScreenOrientation.SQUARE;
  }

  /**
   * @return A scaled dimension of the given device's screen that will fit within this component's bounds.
   */
  @Nullable
  private Dimension getScaledDimension() {
    Dimension pixelSize = getScreenDimension(getDefaultDeviceOrientation());
    if (pixelSize == null) {
      return null;
    }
    double diagonalIn = myDeviceData.diagonalScreenSize().get();
    double sideRatio = pixelSize.getWidth() / pixelSize.getHeight();
    double heightIn = diagonalIn / Math.sqrt(1 + sideRatio);
    double widthIn = sideRatio * heightIn;

    double maxHeightIn = myMaxOutlineHeight == 0 ? heightIn : myMaxOutlineHeight;
    double maxWidthIn = myMaxOutlineWidth == 0 ? widthIn : myMaxOutlineWidth;
    double maxDimIn = Math.max(maxHeightIn, maxWidthIn);

    double desiredMaxWidthPx = getWidth() / 2;
    double desiredMaxHeightPx = getHeight() / 2;
    double desiredMaxPx = Math.min(desiredMaxHeightPx, desiredMaxWidthPx);

    double scalingFactorPxToIn = maxDimIn / desiredMaxPx;

    // Test if we have to scale the min as well
    double desiredMinWidthPx = getWidth() / 10;
    double desiredMinHeightPx = getHeight() / 10;
    double desiredMinIn = Math.max(desiredMinWidthPx, desiredMinHeightPx) * scalingFactorPxToIn;

    double minDimIn = Math.min(myMinOutlineHeightIn, myMinOutlineWidthIn);
    if (minDimIn < desiredMinIn) {
      // compute F and C such that F * minDimIn + C = desiredMinIn and F * maxDimIn + C = maxDimIn
      double f = (maxDimIn - desiredMinIn) / (maxDimIn - minDimIn);
      double c = (desiredMinIn * myMaxOutlineWidth - maxDimIn * minDimIn) / (maxDimIn - minDimIn);

      // scale the diagonal and then recompute the edges, since the edges need to be scaled evenly
      diagonalIn = myDeviceData.diagonalScreenSize().get() * f + c;
      heightIn = diagonalIn / Math.sqrt(1 + sideRatio);
      widthIn = sideRatio * heightIn;
    }

    return new Dimension((int)(widthIn / scalingFactorPxToIn), (int)(heightIn / scalingFactorPxToIn));
  }

  @SuppressWarnings("SuspiciousNameCombination") // We sometimes deliberately swap x/width y/height relationships depending on orientation
  private Dimension getScreenDimension(@NonNull ScreenOrientation orientation) {
    // compute width and height to take orientation into account.
    int x = myDeviceData.screenResolutionWidth().get();
    int y = myDeviceData.screenResolutionHeight().get();
    int screenWidth, screenHeight;

    if (x > y) {
      if (orientation == ScreenOrientation.LANDSCAPE) {
        screenWidth = x;
        screenHeight = y;
      }
      else {
        screenWidth = y;
        screenHeight = x;
      }
    }
    else {
      if (orientation == ScreenOrientation.LANDSCAPE) {
        screenWidth = y;
        screenHeight = x;
      }
      else {
        screenWidth = x;
        screenHeight = y;
      }
    }

    return new Dimension(screenWidth, screenHeight);
  }

  @Override
  public void onCategorySelectionChanged(@Nullable String category, @Nullable List<Device> devices) {
    if (devices == null) {
      myMaxOutlineHeight = 0;
      myMaxOutlineWidth = 0;
      myMinOutlineHeightIn = 0;
      myMinOutlineWidthIn = 0;
    }
    else {
      double maxHeight = 0;
      double maxWidth = 0;
      double minHeight = Double.MAX_VALUE;
      double minWidth = Double.MAX_VALUE;
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
        minWidth = Math.min(minWidth, widthIn);
        minHeight = Math.min(minHeight, heightIn);
      }
      myMaxOutlineHeight = maxHeight;
      myMaxOutlineWidth = maxWidth;
      myMinOutlineHeightIn = minHeight;
      myMinOutlineWidthIn = minWidth;
    }
  }
}
