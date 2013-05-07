/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.ddms.screenshot.DeviceArtPainter;
import org.jetbrains.android.uipreview.AndroidLayoutPreviewToolWindowSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.image.BufferedImage;

import static com.android.tools.idea.rendering.ShadowPainter.SHADOW_SIZE;
import static com.android.tools.idea.rendering.ShadowPainter.SMALL_SHADOW_SIZE;
import static java.awt.RenderingHints.KEY_INTERPOLATION;
import static java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;

/** A rendered image from layoutlib, which can be zoomed */
public class ScalableImage {
  @NotNull private final BufferedImage myImage;
  @Nullable private BufferedImage myScaledImage;
  @NotNull private Configuration myConfiguration;
  @Nullable private Rectangle myImageBounds;
  private final boolean myAlphaChannelImage;
  private double myScale = 1;
  private int myMaxWidth;
  private int myMaxHeight;
  private boolean myUseLargeShadows = true;
  private boolean myDeviceFrameEnabled = true;
  /** Whether current thumbnail actually has a device frame */
  private boolean myThumbnailHasFrame;

  public ScalableImage(RenderSession session, @NotNull Configuration configuration) {
    myImage = session.getImage();
    myConfiguration = configuration;
    myAlphaChannelImage = session.isAlphaChannelImage();
  }

  /**
   * Returns whether this image overlay should be painted with a drop shadow.
   * This is usually the case, but not for transparent themes like the dialog
   * theme (Theme.*Dialog), which already provides its own shadow.
   *
   * @return true if the image overlay should be shown with a drop shadow.
   */
  public boolean getShowDropShadow() {
    return !myAlphaChannelImage;
  }

  public double getScale() {
    return myScale;
  }

  public void setScale(double scale) {
    if (myMaxWidth > 0) {
      // If we have a fixed size, ignore scale factor
      assert myMaxHeight > 0;
      double imageWidth = myImage.getWidth();
      double imageHeight = myImage.getHeight();
      scale = Math.min(myMaxWidth / imageWidth, myMaxHeight / imageHeight);
    }

    if (myScale != scale) {
      myScaledImage = null;
      myScale = scale;

      // Normalize the scale:
      // Some operations are faster if the zoom is EXACTLY 1.0 rather than ALsMOST 1.0.
      // (This is because there is a fast-path when image copying and the scale is 1.0;
      // in that case it does not have to do any scaling).
      //
      // If you zoom out 10 times and then back in 10 times, small rounding errors mean
      // that you end up with a scale=1.0000000000000004. In the cases, when you get close
      // to 1.0, just make the zoom an exact 1.0.
      if (Math.abs(myScale - 1.0) < 0.01) {
        myScale = 1.0;
      }
    }
  }

  /**
   * Returns the bounds of the image itself, if it is surrounded by a device frame.
   * Null otherwise.
   *
   * @return the image bounds, or null
   */
  @Nullable
  public Rectangle getImageBounds() {
    return myImageBounds;
  }

  /**
   * Zooms the view to fit.
   *
   * @param availableWidth the available view width
   * @param availableHeight the available view height
   * @param allowZoomIn if true, apply the scale such that it always fills the available space; if
   *                     false, allow zoom out, but never zoom in more than 100% (the real size)
   * @param horizontalMargin optional horizontal margin to reserve room for
   * @param verticalMargin optional vertical margin to reserve room for
   */
  public void zoomToFit(int availableWidth, int availableHeight, boolean allowZoomIn, int horizontalMargin, int verticalMargin) {
    int sceneWidth = myImage.getWidth();
    int sceneHeight = myImage.getHeight();

    int shadowSize = getShowDropShadow() ? myUseLargeShadows ? SHADOW_SIZE : SMALL_SHADOW_SIZE : 0;
    availableWidth -= shadowSize;
    availableHeight -= shadowSize;

    if (sceneWidth > 0 && sceneHeight > 0) {
      // Reduce the margins if necessary
      int hDelta = availableWidth - sceneWidth;
      int xMargin = 0;
      if (hDelta > 2 * horizontalMargin) {
        xMargin = horizontalMargin;
      } else if (hDelta > 0) {
        xMargin = hDelta / 2;
      }

      int vDelta = availableHeight - sceneHeight;
      int yMargin = 0;
      if (vDelta > 2 * verticalMargin) {
        yMargin = verticalMargin;
      } else if (vDelta > 0) {
        yMargin = vDelta / 2;
      }

      double hScale = (availableWidth - 2 * xMargin) / (double) sceneWidth;
      double vScale = (availableHeight - 2 * yMargin) / (double) sceneHeight;

      double scale = Math.min(hScale, vScale);

      if (!allowZoomIn) {
        scale = Math.min(1.0, scale);
      }

      setScale(scale);
    }
  }

  private static final double ZOOM_FACTOR = 1.2;

  public void zoomIn() {
    setScale(myScale * ZOOM_FACTOR);
  }

  public void zoomOut() {
    setScale(myScale / ZOOM_FACTOR);
  }

  public void zoomActual() {
    setScale(1);
  }

  /** Returns the original full size rendered image */
  @NotNull
  public BufferedImage getOriginalImage() {
    return myImage;
  }

  /** Returns the original width of the image itself, not scaled */
  public int getOriginalWidth() {
    return myImage.getWidth();
  }

  /** Returns the original height of the image itself, not scaled */
  public int getOriginalHeight() {
    return myImage.getHeight();
  }

  /** Returns the width of the image itself, when scaled */
  public int getScaledWidth() {
    return (int)(myScale * myImage.getWidth());
  }

  /** Returns the height of the image itself, when scaled */
  public int getScaledHeight() {
    return (int)(myScale * myImage.getHeight());
  }

  /** Returns the required width to show the scaled image, including drop shadows if applicable */
  public int getRequiredWidth() {
    int width = (int)(myScale * myImage.getWidth());
    if (myThumbnailHasFrame) {
      DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
      Device device = myConfiguration.getDevice();
      if (device != null) {
          State deviceState = myConfiguration.getDeviceState();
          if (deviceState != null) {
            double frameFactor = framePainter.getFrameWidthOverhead(device, deviceState.getOrientation());
            width *= frameFactor;
            return width;
          }
      }
    }

    if (getShowDropShadow()) {
      width += myUseLargeShadows ? SHADOW_SIZE : SMALL_SHADOW_SIZE;
    }

    return width;
  }

  /** Returns the required height to show the scaled image, including drop shadows if applicable */
  public int getRequiredHeight() {
    int height = (int)(myScale * myImage.getHeight());
    if (myThumbnailHasFrame) {
      DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
      Device device = myConfiguration.getDevice();
      if (device != null) {
        State deviceState = myConfiguration.getDeviceState();
        if (deviceState != null) {
          double frameFactor = framePainter.getFrameHeightOverhead(device, deviceState.getOrientation());
          height *= frameFactor;
          return height;
        }
      }
    }

    if (getShowDropShadow()) {
      height += myUseLargeShadows ? SHADOW_SIZE : SMALL_SHADOW_SIZE;
    }

    return height;
  }

  /** Returns the required size to show the scaled image, including drop shadows if applicable */
  public Dimension getRequiredSize() {
    return new Dimension(getRequiredWidth(), getRequiredHeight());
  }

  public void paint(@NotNull Graphics g, int x, int y) {
    if (myScaledImage == null) {
      // Special cases myScale=1 to be fast
      myImageBounds = null;
      myThumbnailHasFrame = false;

      if (myScale <= 1 && myDeviceFrameEnabled) {
        DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
        Device device = myConfiguration.getDevice();
        if (device != null) {
          AndroidLayoutPreviewToolWindowSettings.GlobalState settings =
            AndroidLayoutPreviewToolWindowSettings.getInstance(myConfiguration.getModule().getProject()).getGlobalState();
          if (settings.isShowDeviceFrames()) {
            boolean showEffects = settings.isShowEffects();
            State deviceState = myConfiguration.getDeviceState();
            if (deviceState != null) {
              double scale = myScale;
              ScreenOrientation orientation = deviceState.getOrientation();
              double frameFactor = framePainter.getFrameMaxOverhead(device, orientation);
              scale /= frameFactor;
              myImageBounds = new Rectangle();
              myScaledImage = framePainter.createFrame(myImage, device, orientation, showEffects, scale, myImageBounds);
              myThumbnailHasFrame = true;
              paint(g, x, y);
              return;
            }
          }
        }
      }

      if (myScale == 1) {
        // Scaling to 100% is easy!
        myScaledImage = myImage;

        if (getShowDropShadow()) {
          // Just need to draw drop shadows
          myScaledImage = ShadowPainter.createRectangularDropShadow(myImage);
        }
        g.drawImage(myScaledImage, x, y, null);
      } else if (myScale < 1) {
        // When scaling down we need to do an expensive scaling to ensure that
        // the thumbnails look good
        if (getShowDropShadow()) {
          int shadowSize = myUseLargeShadows ? SHADOW_SIZE : SMALL_SHADOW_SIZE;
          myScaledImage = ImageUtils.scale(myImage, myScale, myScale,
                                           shadowSize, shadowSize);
          if (myUseLargeShadows) {
            ShadowPainter.drawRectangleShadow(myScaledImage, 0, 0, myScaledImage.getWidth() - shadowSize,
                                            myScaledImage.getHeight() - shadowSize);
          } else {
            ShadowPainter.drawSmallRectangleShadow(myScaledImage, 0, 0, myScaledImage.getWidth() - shadowSize,
                                                   myScaledImage.getHeight() - shadowSize);
          }
        } else {
          myScaledImage = ImageUtils.scale(myImage, myScale, myScale);
        }
        g.drawImage(myScaledImage, x, y, null);
      } else {
        // Do a direct scaled paint when scaling up; we don't want to create giant internal images
        // for a zoomed in version of the canvas, since only a small portion is typically shown on the screen
        // (without this, you can easily zoom in 10 times and hit an OOM exception)
        double scale = myScale;
        int w = myImage.getWidth();
        int h = myImage.getHeight();
        int scaledWidth = (int)(scale * w);
        int scaledHeight = (int)(scale * h);
        myThumbnailHasFrame = false;

        Graphics2D g2 = (Graphics2D)g.create();
        try {
          g2.setRenderingHint(KEY_INTERPOLATION, VALUE_INTERPOLATION_BILINEAR);
          g2.drawImage(myImage, x, y, x + scaledWidth, y + scaledHeight, 0, 0, w, h, null);
        } finally {
          g2.dispose();
        }

        myThumbnailHasFrame = false;
        if (myDeviceFrameEnabled) {
          DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
          Device device = myConfiguration.getDevice();
          if (device != null) {
            AndroidLayoutPreviewToolWindowSettings.GlobalState settings =
              AndroidLayoutPreviewToolWindowSettings.getInstance(myConfiguration.getModule().getProject()).getGlobalState();
            if (settings.isShowDeviceFrames()) {
              State state = myConfiguration.getDeviceState();
              if (state != null) {
                // Scaling larger than 1
                framePainter.paintFrame(g, device, state.getOrientation(), true, x + 1, y, scaledHeight);
                myThumbnailHasFrame = true;
                return;
              } // else: fall through and do usual drop shadow painting
            }
          }
        }

        if (getShowDropShadow()) {
          ShadowPainter.drawRectangleShadow(g, x, y, scaledWidth, scaledHeight);
        }
      }
    } else {
      g.drawImage(myScaledImage, x, y, null);
    }
  }

  public void setMaxSize(int width, int height) {
    myMaxWidth = width;
    myMaxHeight = height;
    setScale(1);
  }

  public int getMaxWidth() {
    return myMaxWidth;
  }

  public int getMaxHeight() {
    return myMaxHeight;
  }

  public void setUseLargeShadows(boolean useLargeShadows) {
    myUseLargeShadows = useLargeShadows;
  }

  public void setDeviceFrameEnabled(boolean deviceFrameEnabled) {
    if (myDeviceFrameEnabled != deviceFrameEnabled) {
      myDeviceFrameEnabled = deviceFrameEnabled;
      myScaledImage = null;
    }
  }

  /** Does the current image have a device frame around it? Returns true, false, or null if no image computed yet */
  @Nullable
  public Boolean isFramed() {
    if (myScaledImage == null) {
      return null;
    }
    return myThumbnailHasFrame;
  }
}
