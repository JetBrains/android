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
package com.android.tools.idea.avdmanager.ui;

import static com.android.sdklib.devices.Device.isRollable;

import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.tools.idea.observable.InvalidationListener;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.GraphicsUtil;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import icons.StudioIcons;
import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.text.DecimalFormat;
import javax.swing.Icon;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A preview component for displaying information about
 * a device definition. This panel displays the dimensions of the device
 * (both physical and in pixels) and some information about the screen
 * size and shape.
 */
public class DeviceDefinitionPreview extends JPanel {

  /**
   * Constant string used to signal the panel not to preview a null device
   */
  public static final String DO_NOT_DISPLAY = "DO_NOT_DISPLAY";

  private static final Font STANDARD_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 12));
  private static final Font FIGURE_FONT = JBFont.create(new Font("DroidSans", Font.PLAIN, 10));
  private static final Font TITLE_FONT = JBFont.h3().asBold();

  private static int figurePadding() {
    return JBUI.scale(3);
  }

  private static final DecimalFormat FORMAT = new DecimalFormat(".##\"");

  public static int dimensionLineWidth() {
    return JBUI.scale(1);
  }

  public static int outlineLineWidth() {
    return JBUI.scale(5);
  }

  private static final String NO_DEVICE_SELECTED = "No Device Selected";
  double myMaxOutlineWidth;
  double myMinOutlineWidthIn;

  private static int padding() {
    return JBUI.scale(20);
  }


  private final AvdDeviceData myDeviceData;

  private static final JBColor OUR_GRAY = new JBColor(Gray._192, Gray._96);

  // Do not inline this. If you do, it'll be weakly reachable and eligible for garbage collection. I think. I am not a WeakReference expert.
  private final InvalidationListener myRepaintListener = this::repaint;

  public DeviceDefinitionPreview(@NotNull AvdDeviceData deviceData) {
    myDeviceData = deviceData;
    addListeners();
  }

  @NotNull
  public AvdDeviceData getDeviceData() {
    return myDeviceData;
  }

  /**
   * @return an icon representing the given device's form factor. Defaults to Mobile if the form factor
   * can not be detected.
   */
  @NotNull
  public static Icon getIcon(@Nullable AvdDeviceData deviceData) {
    Icon icon = StudioIcons.Avd.DEVICE_MOBILE;
    if (deviceData != null) {
      if (deviceData.isAutomotive().get()) {
        icon = StudioIcons.Avd.DEVICE_CAR;
      }
      // TODO(256106470): Uncomment the following 3 lines when DEVICE_DESKTOP becomes available.
      //else if (deviceData.isDesktop().get()) {
      //  icon = StudioIcons.Avd.DEVICE_DESKTOP;
      //}
      else if (deviceData.isTv().get()) {
        icon = StudioIcons.Avd.DEVICE_TV;
      }
      else if (deviceData.isWear().get()) {
        icon = StudioIcons.Avd.DEVICE_WEAR;
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
    myDeviceData.screenDpi().addWeakListener(myRepaintListener);
  }

  @Override
  protected void paintComponent(Graphics g) {
    GraphicsUtil.setupAntialiasing(g, true, true);
    GraphicsUtil.setupAAPainting(g);
    super.paintComponent(g);
    Graphics2D g2d = (Graphics2D)g;
    g2d.setColor(JBColor.background());
    g2d.fillRect(0, 0, getWidth(), getHeight());
    g2d.setColor(JBColor.foreground());
    g2d.setFont(STANDARD_FONT);

    if (myDeviceData.name().get().equals(DO_NOT_DISPLAY)) {
      FontMetrics metrics = g2d.getFontMetrics();
      g2d.drawString(NO_DEVICE_SELECTED,
                     (getWidth() - metrics.stringWidth(NO_DEVICE_SELECTED)) / 2,
                     (getHeight() - metrics.getHeight()) / 2);
      return;
    }

    boolean isCircular = myDeviceData.isWear().get() && myDeviceData.isScreenRound().get();

    // Paint our icon
    Icon icon = getIcon(myDeviceData);
    icon.paintIcon(this, g, JBUI.scale(16), JBUI.scale(16));

    // Paint the device name
    g2d.setFont(TITLE_FONT);
    FontMetrics metrics = g.getFontMetrics(TITLE_FONT);
    g2d.drawString(myDeviceData.name().get(), JBUI.scale(40), padding() + metrics.getHeight() / 2);
    g2d.drawLine(0, JBUI.scale(50), getWidth(), JBUI.scale(50));

    // Paint the device outline with dimensions labelled
    Dimension screenSize = getScaledDimension();
    Dimension pixelScreenSize = myDeviceData.getDeviceScreenDimension();
    if (screenSize.getHeight() <= 0) {
      screenSize.height = 1;
    }
    if (screenSize.getWidth() <= 0) {
      screenSize.width = 1;
    }
    RoundRectangle2D roundRect =
      new RoundRectangle2D.Double(padding(), JBUI.scale(100), screenSize.width, screenSize.height, JBUI.scale(10), JBUI.scale(10));
    Stroke normalStroke = new BasicStroke(dimensionLineWidth());
    g2d.setStroke(normalStroke);
    g2d.setColor(OUR_GRAY);

    g2d.setFont(FIGURE_FONT);
    metrics = g2d.getFontMetrics(FIGURE_FONT);
    int stringHeight = metrics.getHeight() - metrics.getDescent();

    if (isRollable(myDeviceData.deviceId().get())) {
      // Show the boundary of the folded region using dashed lines
      // Get the location and size of the preview of the folded region
      double displayFactor = screenSize.height / (double)myDeviceData.screenResolutionHeight().get();
      int foldedX = (int)(myDeviceData.screenFoldedXOffset().get() * displayFactor + 0.5);
      int foldedY = (int)(myDeviceData.screenFoldedYOffset().get() * displayFactor + 0.5);
      int foldedWidth = (int)(myDeviceData.screenFoldedWidth().get() * displayFactor + 0.5);
      int foldedHeight = (int)(myDeviceData.screenFoldedHeight().get() * displayFactor + 0.5);
      int foldedX2 = (int)(myDeviceData.screenFoldedXOffset2().get() * displayFactor + 0.5);
      int foldedY2 = (int)(myDeviceData.screenFoldedYOffset2().get() * displayFactor + 0.5);
      int foldedWidth2 = (int)(myDeviceData.screenFoldedWidth2().get() * displayFactor + 0.5);
      int foldedHeight2 = (int)(myDeviceData.screenFoldedHeight2().get() * displayFactor + 0.5);
      int foldedX3 = (int)(myDeviceData.screenFoldedXOffset3().get() * displayFactor + 0.5);
      int foldedY3 = (int)(myDeviceData.screenFoldedYOffset3().get() * displayFactor + 0.5);
      int foldedWidth3 = (int)(myDeviceData.screenFoldedWidth3().get() * displayFactor + 0.5);
      int foldedHeight3 = (int)(myDeviceData.screenFoldedHeight3().get() * displayFactor + 0.5);

      foldedX += padding();
      foldedY += JBUI.scale(100);
      foldedX2 += padding();
      foldedY2 += JBUI.scale(100);
      foldedX3 += padding();
      foldedY3 += JBUI.scale(100);

      g2d.setStroke(new BasicStroke(outlineLineWidth(), BasicStroke.CAP_BUTT,
                                    BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0));
      // Show a side of the folded region if it does not coincide with the
      // corresponding side of the full region
      if (myDeviceData.screenFoldedXOffset().get() != 0) {
        // Show the left boundary
        g2d.drawLine(foldedX, foldedY, foldedX, foldedY + foldedHeight);
      }
      if (myDeviceData.screenFoldedYOffset().get() != 0) {
        // Show the top boundary
        g2d.drawLine(foldedX, foldedY, foldedX + foldedWidth, foldedY);
      }
      if ((myDeviceData.screenFoldedXOffset().get() + myDeviceData.screenFoldedWidth().get())
          != myDeviceData.screenResolutionWidth().get()) {
        // Show the right boundary
        g2d.drawLine(foldedX + foldedWidth, foldedY, foldedX + foldedWidth, foldedY + foldedHeight);
      }
      if ((myDeviceData.screenFoldedYOffset().get() + myDeviceData.screenFoldedHeight().get())
          != myDeviceData.screenResolutionHeight().get()) {
        // Show the bottom boundary
        g2d.drawLine(foldedX, foldedY + foldedHeight, foldedX + foldedWidth, foldedY + foldedHeight);
      }
      if (myDeviceData.screenFoldedXOffset2().get() != 0) {
        // Show the left boundary
        g2d.drawLine(foldedX2, foldedY2, foldedX2, foldedY2 + foldedHeight2);
      }
      if (myDeviceData.screenFoldedYOffset2().get() != 0) {
        // Show the top boundary
        g2d.drawLine(foldedX2, foldedY2, foldedX2 + foldedWidth2, foldedY2);
      }
      if ((myDeviceData.screenFoldedWidth2().get() != 0) &&
          (myDeviceData.screenFoldedXOffset2().get() + myDeviceData.screenFoldedWidth2().get())
          != myDeviceData.screenResolutionWidth().get()) {
        // Show the right boundary
        g2d.drawLine(foldedX2 + foldedWidth2, foldedY2, foldedX2 + foldedWidth2, foldedY2 + foldedHeight2);
      }
      if ((myDeviceData.screenFoldedHeight2().get() != 0) &&
          (myDeviceData.screenFoldedYOffset2().get() + myDeviceData.screenFoldedHeight2().get())
          != myDeviceData.screenResolutionHeight().get()) {
        // Show the bottom boundary
        g2d.drawLine(foldedX2, foldedY2 + foldedHeight2, foldedX2 + foldedWidth2, foldedY2 + foldedHeight2);
      }
      if (myDeviceData.screenFoldedXOffset3().get() != 0) {
        // Show the left boundary
        g2d.drawLine(foldedX3, foldedY3, foldedX3, foldedY3 + foldedHeight3);
      }
      if (myDeviceData.screenFoldedYOffset3().get() != 0) {
        // Show the top boundary
        g2d.drawLine(foldedX3, foldedY3, foldedX3 + foldedWidth3, foldedY3);
      }
      if ((myDeviceData.screenFoldedWidth3().get() != 0)
          && (myDeviceData.screenFoldedXOffset3().get() + myDeviceData.screenFoldedWidth3().get())
             != myDeviceData.screenResolutionWidth().get()) {
        // Show the right boundary
        g2d.drawLine(foldedX3 + foldedWidth3, foldedY3, foldedX3 + foldedWidth3, foldedY3 + foldedHeight3);
      }
      if ((myDeviceData.screenFoldedHeight3().get() != 0) &&
          (myDeviceData.screenFoldedYOffset3().get() + myDeviceData.screenFoldedHeight3().get())
          != myDeviceData.screenResolutionHeight().get()) {
        // Show the bottom boundary
        g2d.drawLine(foldedX3, foldedY3 + foldedHeight3, foldedX3 + foldedWidth3, foldedY3 + foldedHeight3);
      }
      g2d.setStroke(normalStroke);
    }

    // Paint the width dimension
    String widthString = pixelScreenSize.width + "px";
    int widthLineY = JBUI.scale(95) - (metrics.getHeight() - metrics.getDescent()) / 2;
    g2d.drawLine(padding(), widthLineY, round(padding() + screenSize.width), widthLineY);

    // Erase the part of the line that the text overlays
    g2d.setColor(JBColor.background());
    int widthStringWidth = metrics.stringWidth(widthString);
    int widthTextX = round(padding() + (screenSize.width - widthStringWidth) / 2.0);
    g2d.drawLine(widthTextX - figurePadding(), widthLineY, widthTextX + widthStringWidth + figurePadding(), widthLineY);

    // Paint the width text
    g2d.setColor(JBColor.foreground());
    g2d.drawString(widthString, widthTextX, JBUI.scale(95));

    // Paint the height dimension
    g2d.setColor(OUR_GRAY);
    String heightString = pixelScreenSize.height + "px";
    int heightLineX = round(padding() + screenSize.width + JBUI.scale(15));
    g2d.drawLine(heightLineX, JBUI.scale(100), heightLineX, round(JBUI.scale(100) + screenSize.height));

    // Erase the part of the line that the text overlays
    g2d.setColor(JBColor.background());
    int heightTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2.0);
    g2d.drawLine(heightLineX, heightTextY + figurePadding(), heightLineX, heightTextY - stringHeight - figurePadding());

    // Paint the height text
    g2d.setColor(JBColor.foreground());
    g2d.drawString(heightString, heightLineX - JBUI.scale(10), heightTextY);

    // Paint the diagonal dimension
    g2d.setColor(OUR_GRAY);
    String diagString = FORMAT.format(myDeviceData.diagonalScreenSize().get());
    int diagonalTextX = round(padding() + (screenSize.width - metrics.stringWidth(diagString)) / 2.0);
    int diagonalTextY = round(JBUI.scale(100) + (screenSize.height + stringHeight) / 2.0);

    double chin = (double)myDeviceData.screenChinSize().get();
    chin *= screenSize.getWidth() / myDeviceData.getDeviceScreenDimension().getWidth();
    Line2D diagonalLine =
      new Line2D.Double(padding(), JBUI.scale(100) + screenSize.height + chin, padding() + screenSize.width, JBUI.scale(100));
    if (isCircular) {
      // Move the endpoints of the line to within the circle. Each endpoint must move towards the center axis of the circle by
      // 0.5 * (l - l/sqrt(2)) where l is the diameter of the circle.
      double dist = 0.5 * (screenSize.width - screenSize.width / Math.sqrt(2));
      diagonalLine.setLine(diagonalLine.getX1() + dist, diagonalLine.getY1() - dist, diagonalLine.getX2() - dist,
                           diagonalLine.getY2() + dist);
    }
    g2d.draw(diagonalLine);

    // Erase the part of the line that the text overlays
    g2d.setColor(JBColor.background());
    Rectangle erasureRect = new Rectangle(diagonalTextX - figurePadding(), diagonalTextY - stringHeight - figurePadding(),
                                          metrics.stringWidth(diagString) + figurePadding() * 2, stringHeight + figurePadding() * 2);
    g2d.fill(erasureRect);

    // Paint the diagonal text
    g2d.setColor(JBColor.foreground());
    g2d.drawString(diagString, diagonalTextX, diagonalTextY);

    // Finally, paint the outline
    g2d.setStroke(new BasicStroke(outlineLineWidth()));
    g2d.setColor(JBColor.foreground());

    if (isCircular) {
      double x = roundRect.getX();
      double y = roundRect.getY();
      Ellipse2D circle = new Ellipse2D.Double(x, y, screenSize.width, screenSize.height + chin);
      g2d.draw(circle);
      if (chin > 0) {
        erasureRect = new Rectangle((int)x, (int)(y + screenSize.height + outlineLineWidth() / 2.0 + 1), screenSize.width,
                                    (int)chin + outlineLineWidth() / 2 + 1);
        g2d.setColor(JBColor.background());
        g2d.fill(erasureRect);
        g2d.setColor(JBColor.foreground());
        double halfChinWidth = Math.sqrt(chin * (screenSize.width - chin)) - outlineLineWidth() / 2.0;
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
    if (myDeviceData.getDefaultDeviceOrientation().equals(ScreenOrientation.PORTRAIT)) {
      infoSegmentX = round(padding() + screenSize.width + metrics.stringWidth(heightString) + padding());
      infoSegmentY = JBUI.scale(100);
    }
    else {
      infoSegmentX = padding();
      infoSegmentY = round(JBUI.scale(100) + screenSize.height + padding());
    }

    int valueOffsetX = metrics.stringWidth("Density:") + metrics.stringWidth(" ");
    infoSegmentY += stringHeight;

    ScreenSize size = ScreenSize.getScreenSize(myDeviceData.diagonalScreenSize().get());
    g2d.drawString("Size:", infoSegmentX, infoSegmentY);
    g2d.drawString(size.getResourceValue(), infoSegmentX + valueOffsetX, infoSegmentY);
    infoSegmentY += stringHeight;

    ScreenRatio ratio =
      ScreenRatio.create(myDeviceData.screenResolutionWidth().get(), myDeviceData.screenResolutionHeight().get());
    g2d.drawString("Ratio:", infoSegmentX, infoSegmentY);
    g2d.drawString(ratio.getResourceValue(), infoSegmentX + valueOffsetX, infoSegmentY);
    infoSegmentY += stringHeight;

    g2d.drawString("Density:", infoSegmentX, infoSegmentY);
    g2d.drawString(myDeviceData.density().get().getResourceValue(), infoSegmentX + valueOffsetX, infoSegmentY);

    // Foldable / Rollable
    if (myDeviceData.isFoldable().get()) {
      infoSegmentY += stringHeight;
      if (myDeviceData.screenFoldedWidth2().get() == 0 &&
          myDeviceData.screenFoldedWidth3().get() == 0) {
        g2d.drawString("Folded: " + myDeviceData.screenFoldedWidth().get() +
                       "x" + myDeviceData.screenFoldedHeight(), infoSegmentX, infoSegmentY);
      }
      else {
        g2d.drawString("Rolled in: " + myDeviceData.screenFoldedWidth().get() +
                       "x" + myDeviceData.screenFoldedHeight(), infoSegmentX, infoSegmentY);
        if (myDeviceData.screenFoldedWidth2().get() != 0) {
          infoSegmentY += stringHeight;
          g2d.drawString("Intermediate: " + myDeviceData.screenFoldedWidth2().get() +
                         "x" + myDeviceData.screenFoldedHeight2(), infoSegmentX, infoSegmentY);
        }
        if (myDeviceData.screenFoldedWidth3().get() != 0) {
          infoSegmentY += stringHeight;
          g2d.drawString("Intermediate2: " + myDeviceData.screenFoldedWidth3().get() +
                         "x" + myDeviceData.screenFoldedHeight3(), infoSegmentX, infoSegmentY);
        }
        infoSegmentY += stringHeight;
        g2d.drawString("Rolled out: " + pixelScreenSize.width +
                       "x" + pixelScreenSize.height, infoSegmentX, infoSegmentY);
      }
    }

    // Resizable
    if (myDeviceData.deviceId().get().equals("resizable")) {
      infoSegmentY += stringHeight * 2;
      g2d.drawString("This device resizes to:", infoSegmentX, infoSegmentY);
      infoSegmentY += stringHeight;
      g2d.drawString("Phone (1080 x 2400 @ 420dpi)", infoSegmentX, infoSegmentY);
      infoSegmentY += stringHeight;
      g2d.drawString("Foldable (2208 x 1840 @ 420dpi)", infoSegmentX, infoSegmentY);
      infoSegmentY += stringHeight;
      g2d.drawString("Tablet (1920 x 1200 @ 240dpi)", infoSegmentX, infoSegmentY);
    }
  }

  private static int round(double d) {
    return (int)Math.round(d);
  }

  /**
   * @return A scaled dimension of the given device's screen that will fit within this component's bounds.
   */
  @NotNull
  private Dimension getScaledDimension() {
    Dimension pixelSize = myDeviceData.getDeviceScreenDimension();
    double diagonalIn = myDeviceData.diagonalScreenSize().get();
    double sideRatio = pixelSize.getWidth() / pixelSize.getHeight();
    double heightIn = diagonalIn / Math.sqrt(1 + sideRatio * sideRatio);
    double widthIn = sideRatio * heightIn;

    double maxWidthIn = myMaxOutlineWidth == 0 ? widthIn : myMaxOutlineWidth;
    double desiredMaxWidthPx = getWidth() * 0.40;
    double desiredMinWidthPx = getWidth() * 0.10;

    // This is the scaled width we want to use.
    double widthPixels = widthIn * desiredMaxWidthPx / maxWidthIn;

    // However a search result can contain both very small devices (wear) and very
    // large devices (TV). When this is the case use this alternate scaling
    // algorithm to avoid the wear devices to show up as a dot.
    if (myMinOutlineWidthIn * desiredMaxWidthPx / maxWidthIn < desiredMinWidthPx) {
      widthPixels =
        desiredMinWidthPx + (widthIn - myMinOutlineWidthIn) * (desiredMaxWidthPx - desiredMinWidthPx) / (maxWidthIn - myMinOutlineWidthIn);
    }
    double heightPixels = widthPixels / widthIn * heightIn;
    return new Dimension((int)widthPixels, (int)heightPixels);
  }
}
