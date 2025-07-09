/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface.interaction;

import static com.android.resources.Density.DEFAULT_DENSITY;

import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationUtilKt;
import com.android.tools.configurations.Configurations;
import com.android.tools.configurations.ConversionUtil;
import com.android.tools.configurations.DeviceSize;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.MousePressedEvent;
import com.android.tools.idea.uibuilder.analytics.ResizeTracker;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CanvasResizeInteraction implements Interaction {
  /**
   * Maximum size (in dp) for resizing. This value should be larger than any reasonable Android device size.
   * Resizing is capped because layoutlib creates an image of the device's size, which can cause OutOfMemoryError
   * if the dimensions are too large.
   */
  private static final int MAX_ANDROID_SIZE_DP = 1500;
  /**
   * Minimum size (in dp) for resizing.
   */
  private static final int MIN_ANDROID_SIZE_DP = 10;

  /** The {@link NlDesignSurface} where the interaction happens. */
  @NotNull private final NlDesignSurface myDesignSurface;
  /** The {@link ScreenView} that is being resized. */
  @NotNull private final ScreenView myScreenView;
  /** The {@link Configuration} of the resized {@link ScreenView}. */
  @NotNull private final Configuration myConfiguration;
  /** The original {@link Device} before the resize started. Used to revert the resize on cancel or invalid input. */
  private final Device myOriginalDevice;
  /** The original {@link State} of the device before the resize started. */
  private final State myOriginalDeviceState;
  /** A {@link MergingUpdateQueue} to handle live rendering of the resize. */
  private final MergingUpdateQueue myUpdateQueue;
  /** The maximum allowed size for resizing in pixels, converted from {@link #MAX_ANDROID_SIZE_DP}. */
  private final int myMaxAndroidSizePx;
  /** The minimum allowed size for resizing in pixels, converted from {@link #MIN_ANDROID_SIZE_DP}. */
  private final int myMinAndroidSizePx;
  /** The current width of the device in Android pixels, updated during a drag operation. This value is always clamped to the valid range. */
  private int myCurrentAndroidWidth;
  /** The current height of the device in Android pixels, updated during a drag operation. This value is always clamped to the valid range. */
  private int myCurrentAndroidHeight;
  /** The DPI of the current device configuration. */
  private final int myCurrentDpi;
  /** The current mouse cursor X position in Swing coordinates. */
  private int myCurrentX;
  /** The current mouse cursor Y position in Swing coordinates. */
  private int myCurrentY;
  /** Stores information about the interaction's starting point. */
  private InteractionInformation myStartInfo;

  /**
   * Threshold used to force a resize of the surface when getting close to the border. If the mouse gets closer than
   * 2*myResizeTriggerThreshold to the border of the surface, the surface will be extended by myResizeTriggerThreshold
   */
  private final int myResizeTriggerThreshold = JBUI.scale(200);

  public CanvasResizeInteraction(@NotNull NlDesignSurface designSurface,
                                 @NotNull ScreenView screenView,
                                 @NotNull Configuration configuration) {
    myDesignSurface = designSurface;
    myScreenView = screenView;
    myConfiguration = configuration;
    myUpdateQueue = new MergingUpdateQueue("layout.editor.canvas.resize", 10, true, null, myDesignSurface);
    myUpdateQueue.setRestartTimerOnAdd(true);

    // Store the original device and state to allow reverting the resize.
    myOriginalDevice = configuration.getCachedDevice();
    myOriginalDeviceState = configuration.getDeviceState();

    myCurrentDpi = configuration.getDensity().getDpiValue();

    // Convert the min/max dp values to pixels for the current device's density.
    myMaxAndroidSizePx = (int)(1.0 * MAX_ANDROID_SIZE_DP * myCurrentDpi / DEFAULT_DENSITY);
    myMinAndroidSizePx = (int)(1.0 * MIN_ANDROID_SIZE_DP * myCurrentDpi / DEFAULT_DENSITY);

    DeviceSize deviceSize = ConfigurationUtilKt.deviceSizePx(myScreenView.getConfiguration());
    myCurrentAndroidWidth = deviceSize.getWidth();
    myCurrentAndroidHeight = deviceSize.getHeight();
  }

  @Override
  public void begin(@NotNull InteractionEvent event) {
    if (event instanceof MousePressedEvent) {
      MouseEvent mouseEvent = ((MousePressedEvent)event).getEventObject();
      myStartInfo = event.getInfo();
      int x = mouseEvent.getX();
      int y = mouseEvent.getY();
      myCurrentX = x;
      myCurrentY = y;

      myDesignSurface.setResizeMode(true);
    }
  }

  @Override
  public void update(@NotNull InteractionEvent event) {
    if (event instanceof MouseDraggedEvent) {
      MouseEvent mouseEvent = ((MouseDraggedEvent)event).getEventObject();
      int x = mouseEvent.getX();
      int y = mouseEvent.getY();
      if (myOriginalDevice.isScreenRound()) {
        int startX = myStartInfo.getX();
        int startY = myStartInfo.getY();
        // For round devices, preserve the aspect ratio by making the resize diagonal.
        int deltaX = x - startX;
        int deltaY = y - startY;
        if (deltaX > deltaY) {
          y = startY + deltaX;
        }
        else {
          x = startX + deltaY;
        }
      }

      myCurrentX = x;
      myCurrentY = y;

      // Calculate width/height relative to the ScreenView's origin in Swing coordinates.
      // This is robust against the view moving on the canvas during resize.
      int swingWidth = myCurrentX - myScreenView.getX();
      int swingHeight = myCurrentY - myScreenView.getY();

      // Convert the Swing dimension to the raw Android pixel dimension.
      int androidWidth = Coordinates.getAndroidDimension(myScreenView, swingWidth);
      int androidHeight = Coordinates.getAndroidDimension(myScreenView, swingHeight);

      // Clamp the dimensions to the valid range [myMinAndroidSizePx, myMaxAndroidSizePx].
      myCurrentAndroidWidth = Math.max(myMinAndroidSizePx, Math.min(androidWidth, myMaxAndroidSizePx));
      myCurrentAndroidHeight = Math.max(myMinAndroidSizePx, Math.min(androidHeight, myMaxAndroidSizePx));

      Dimension viewSize = myDesignSurface.getViewSize();
      int maxX = Coordinates.getSwingX(myScreenView, myMaxAndroidSizePx) + NlConstants.DEFAULT_SCREEN_OFFSET_X;
      int maxY = Coordinates.getSwingY(myScreenView, myMaxAndroidSizePx) + NlConstants.DEFAULT_SCREEN_OFFSET_Y;
      if (myCurrentX < maxX &&
          myCurrentY < maxY &&
          (myCurrentX + myResizeTriggerThreshold * 2 > viewSize.getWidth() ||
           myCurrentY + myResizeTriggerThreshold * 2 > viewSize.getHeight())) {
        // Extend the scrollable area of the surface to accommodate for the resize
        myDesignSurface.setScrollableViewMinSize(
          new Dimension(myCurrentX + myResizeTriggerThreshold, myCurrentY + myResizeTriggerThreshold));
        myDesignSurface.validateScrollArea();
      }

      // Queue an update to re-render the component with the new size.
      myUpdateQueue.queue(myPositionUpdate);
    }
  }


  @Override
  public void commit(@NotNull InteractionEvent event) {
    myDesignSurface.setResizeMode(false);
    myDesignSurface.setScrollableViewMinSize(new Dimension(0, 0));

    DeviceSize deviceSize = ConfigurationUtilKt.deviceSizePx(myScreenView.getConfiguration());
    // If the user has not moved the mouse, then ignore the commit
    if (myCurrentAndroidWidth == deviceSize.getWidth() && myCurrentAndroidHeight == deviceSize.getHeight()) return;

    ResizeTracker tracker = ResizeTracker.getTracker(myScreenView.getSceneManager());
    Configurations.updateScreenSize(myConfiguration, myCurrentAndroidWidth, myCurrentAndroidHeight);

    int androidXDp = ConversionUtil.INSTANCE.pxToDp(myCurrentAndroidWidth, myCurrentDpi);
    int androidYDp = ConversionUtil.INSTANCE.pxToDp(myCurrentAndroidHeight, myCurrentDpi);
    if (tracker != null) tracker.reportResizeStopped(myScreenView.getSceneManager(), androidXDp, androidYDp, myCurrentDpi);
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
    myConfiguration.setEffectiveDevice(myOriginalDevice, myOriginalDeviceState);
  }

  @Nullable
  @Override
  public Cursor getCursor() {
    return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
  }

  @NotNull
  @Override
  public synchronized List<Layer> createOverlays() {
    ImmutableList.Builder<Layer> layers = ImmutableList.builder();
    layers.add(new ResizeOutlineLayer());
    return layers.build();
  }

  /**
   * An {@link Update} runnable that applies the current resize dimensions to the {@link Configuration}.
   * This is queued to run during a drag to provide a live preview of the resize.
   */
  private final Update myPositionUpdate = new Update("CanvasResizePositionUpdate") {
    @Override
    public void run() {
      Configurations.updateScreenSize(myConfiguration, myCurrentAndroidWidth, myCurrentAndroidHeight);
    }
  };

  /**
   * An {@link Layer} for the {@link CanvasResizeInteraction}; paints an outline of what the canvas
   * size will be after resizing.
   * If user drags further than {@link #MAX_ANDROID_SIZE_DP} or smaller than {@link #MIN_ANDROID_SIZE_DP},
   * paints a red outline to indicate the resize limit has been reached.
   */
  private class ResizeOutlineLayer extends Layer {

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      int screenViewX = myScreenView.getX();
      int screenViewY = myScreenView.getY();

      int currentSwingWidth = Coordinates.getSwingDimension(myScreenView, myCurrentAndroidWidth);
      int currentSwingHeight = Coordinates.getSwingDimension(myScreenView, myCurrentAndroidHeight);

      if (currentSwingWidth > 0 && currentSwingHeight > 0) {
        Graphics2D graphics = (Graphics2D)g2d.create();
        graphics.setStroke(NlConstants.THICK_SOLID_STROKE);

        // The resize outline is drawn in red if the current size has hit either the minimum or maximum limit.
        // This provides a visual cue that the resize is being capped.
        boolean atMinWidth = myCurrentAndroidWidth == myMinAndroidSizePx;
        boolean atMinHeight = myCurrentAndroidHeight == myMinAndroidSizePx;
        boolean atMaxWidth = myCurrentAndroidWidth == myMaxAndroidSizePx;
        boolean atMaxHeight = myCurrentAndroidHeight == myMaxAndroidSizePx;

        if (atMinWidth || atMinHeight || atMaxWidth || atMaxHeight) {
          graphics.setColor(JBColor.RED);
        }
        else {
          graphics.setColor(NlConstants.RESIZING_CONTOUR_COLOR);
        }
        graphics.drawRect(screenViewX - 1, screenViewY - 1, currentSwingWidth, currentSwingHeight);
        graphics.dispose();
      }
    }
  }
}
