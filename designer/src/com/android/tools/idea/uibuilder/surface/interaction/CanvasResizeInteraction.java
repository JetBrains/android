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

import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.Configurations;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.MousePressedEvent;
import com.android.tools.idea.uibuilder.analytics.ResizeTracker;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.surface.DeviceSizeList;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;

import com.google.common.collect.ImmutableList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;

import java.util.List;

public class CanvasResizeInteraction implements Interaction {
  /**
   * Cut-off size (in dp) for resizing, it should be bigger than any Android device. Resizing size needs to be capped
   * because layoutlib will create an image of the size of the device, which will cause an OOM error when the
   * device is too large.
   */
  // TODO: Make it possible to resize to arbitrary large sizes without running out of memory
  private static final int MAX_ANDROID_SIZE = 1500;

  @NotNull private final NlDesignSurface myDesignSurface;
  @NotNull private final ScreenView myScreenView;
  @NotNull private final Configuration myConfiguration;
  private final Device myOriginalDevice;
  private final State myOriginalDeviceState;
  private final MergingUpdateQueue myUpdateQueue;
  private final int myMaxSize;

  private final Update myPositionUpdate = new Update("CanvasResizePositionUpdate") {
    @Override
    public void run() {
      int androidX = Coordinates.getAndroidX(myScreenView, myCurrentX);
      int androidY = Coordinates.getAndroidY(myScreenView, myCurrentY);
      if (androidX > 0 && androidY > 0 && androidX < myMaxSize && androidY < myMaxSize) {
        Configurations.updateScreenSize(myConfiguration, androidX, androidY);
      }
    }
  };
  private final int myCurrentDpi;
  private int myCurrentX;
  private int myCurrentY;
  @Nullable private DeviceSizeList.DeviceSize myLastSnappedDevice;
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

    myOriginalDevice = configuration.getCachedDevice();
    myOriginalDeviceState = configuration.getDeviceState();

    myCurrentDpi = configuration.getDensity().getDpiValue();

    myMaxSize = (int)(1.0 * MAX_ANDROID_SIZE * myCurrentDpi / DEFAULT_DENSITY);
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
        // Force aspect preservation
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

      Dimension viewSize = myDesignSurface.getViewSize();
      int maxX = Coordinates.getSwingX(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_X;
      int maxY = Coordinates.getSwingY(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_Y;
      if (myCurrentX < maxX &&
          myCurrentY < maxY &&
          (myCurrentX + myResizeTriggerThreshold * 2 > viewSize.getWidth() ||
           myCurrentY + myResizeTriggerThreshold * 2 > viewSize.getHeight())) {
        // Extend the scrollable area of the surface to accommodate for the resize
        myDesignSurface.setScrollableViewMinSize(
          new Dimension(myCurrentX + myResizeTriggerThreshold, myCurrentY + myResizeTriggerThreshold));
        myDesignSurface.validateScrollArea();
      }

      myUpdateQueue.queue(myPositionUpdate);
    }
  }


  @Override
  public void commit(@NotNull InteractionEvent event) {
    // Set the surface in resize mode, so it doesn't try to re-center the screen views all the time
    myDesignSurface.setResizeMode(false);
    myDesignSurface.setScrollableViewMinSize(new Dimension(0, 0));

    int androidX = Coordinates.getAndroidX(myScreenView, event.getInfo().getX());
    int androidY = Coordinates.getAndroidY(myScreenView, event.getInfo().getY());

    if (androidX < 0 || androidY < 0) {
      myConfiguration.setEffectiveDevice(myOriginalDevice, myOriginalDeviceState);
    }
    else {
      if (myLastSnappedDevice != null) {
        Device deviceToSnap = myLastSnappedDevice.getDevice();
        ScreenOrientation deviceOrientation = androidX < androidY ? ScreenOrientation.PORTRAIT : ScreenOrientation.LANDSCAPE;
        State deviceState = deviceToSnap.getDefaultState().deepCopy();
        deviceState.setOrientation(deviceOrientation);
        myConfiguration.setEffectiveDevice(deviceToSnap, deviceState);
      }
      else {
        Configurations.updateScreenSize(myConfiguration, androidX, androidY);
      }
      ResizeTracker tracker = ResizeTracker.getTracker(myScreenView.getSceneManager());

      int androidXDp = Coordinates.getAndroidXDip(myScreenView, event.getInfo().getX());
      int androidYDp = Coordinates.getAndroidYDip(myScreenView, event.getInfo().getY());
      if (tracker != null) tracker.reportResizeStopped(myScreenView.getSceneManager(), androidXDp, androidYDp, myCurrentDpi);
    }
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
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
    layers.add(new ResizeLayer());
    return layers.build();
  }

  /**
   * An {@link Layer} for the {@link CanvasResizeInteraction}; paints an outline of what the canvas
   * size will be after resizing.
   */
  private class ResizeLayer extends Layer {
    @Override
    public void paint(@NotNull Graphics2D g2d) {
      int x = myScreenView.getX();
      int y = myScreenView.getY();

      if (myCurrentX > x && myCurrentY > y) {
        Graphics2D graphics = (Graphics2D)g2d.create();
        graphics.setColor(NlConstants.RESIZING_CONTOUR_COLOR);
        graphics.setStroke(NlConstants.THICK_SOLID_STROKE);

        graphics.drawRect(x - 1, y - 1, myCurrentX - x, myCurrentY - y);
        graphics.dispose();
      }
    }
  }
}
