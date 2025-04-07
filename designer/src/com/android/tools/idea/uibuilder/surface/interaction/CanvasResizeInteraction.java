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
import static com.android.tools.idea.uibuilder.graphics.NlConstants.MAX_MATCH_DISTANCE;

import android.annotation.SuppressLint;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.configurations.Configuration;
import com.android.tools.configurations.ConfigurationSettings;
import com.android.tools.configurations.Configurations;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.MousePressedEvent;
import com.android.tools.idea.configurations.AdditionalDeviceService;
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
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JComponent;

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
  private final List<DeviceLayer> myDeviceLayers = new ArrayList<>();
  private final Device myOriginalDevice;
  private final State myOriginalDeviceState;
  private final DeviceSizeList myDeviceSizeList = new DeviceSizeList();
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
  private int myCurrentDpi;
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
    ConfigurationSettings configSettings = configuration.getSettings();

    List<Device> devicesToShow;
    if (Device.isWear(myOriginalDevice)) {
      devicesToShow = configSettings.getDevices().stream().filter(
        d -> Device.isWear(d) && !Configuration.CUSTOM_DEVICE_ID.equals(d.getId())).collect(Collectors.toList());
    }
    else if (Device.isTv(myOriginalDevice)) {
      // There are only two devices and they have the same dip sizes, so just use one of them
      devicesToShow = Collections.singletonList(configSettings.getDeviceById("tv_1080p"));
    }
    else {
      // Reference devices as phone, foldable, tablet, desktop
      devicesToShow = AdditionalDeviceService.getInstance().getWindowSizeDevices();
    }

    for (Device device : devicesToShow) {
      assert device != null;
      Screen screen = device.getDefaultHardware().getScreen();
      double dpiRatio = 1.0 * myCurrentDpi / screen.getPixelDensity().getDpiValue();
      int px = (int)(screen.getXDimension() * dpiRatio);
      int py = (int)(screen.getYDimension() * dpiRatio);
      myDeviceSizeList.add(device, px, py);
      myDeviceLayers.add(new DeviceLayer(myDesignSurface, myScreenView, myConfiguration, px, py, device.getDisplayName()));
    }
    myDeviceSizeList.sort();

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

      snapToDevice(x, y);

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

  private void snapToDevice(int x, int y) {
    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);
    int snapThreshold = Coordinates.getAndroidDimension(myScreenView, MAX_MATCH_DISTANCE);
    myLastSnappedDevice = myDeviceSizeList.snapToDevice(androidX, androidY, snapThreshold);
    myCurrentDpi = myLastSnappedDevice == null ? myCurrentDpi : myLastSnappedDevice.getDevice().getDefaultHardware().getScreen().getPixelDensity().getDpiValue();

    if (myLastSnappedDevice != null) {
      myCurrentX = Coordinates.getSwingX(myScreenView, myLastSnappedDevice.getX());
      myCurrentY = Coordinates.getSwingY(myScreenView, myLastSnappedDevice.getY());
    } else {
      myCurrentX = x;
      myCurrentY = y;
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
    layers.addAll(myDeviceLayers);
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

  private static class DeviceLayer extends Layer {
    private final String myName;
    @NotNull private final ScreenView myScreenView;
    @NotNull private final Configuration myConfiguration;
    private final int myNameWidth;
    private final int myBigDimension;
    private final int mySmallDimension;

    public DeviceLayer(@NotNull JComponent designSurface, @NotNull ScreenView screenView, @NotNull Configuration configuration,
                       int pxWidth, int pxHeight, @NotNull String name) {
      myScreenView = screenView;
      myConfiguration = configuration;
      myBigDimension = Math.max(pxWidth, pxHeight);
      mySmallDimension = Math.min(pxWidth, pxHeight);
      myName = name;
      FontMetrics fontMetrics = designSurface.getFontMetrics(designSurface.getFont());
      myNameWidth = (int)fontMetrics.getStringBounds(myName, null).getWidth();
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      State deviceState = myConfiguration.getDeviceState();
      assert deviceState != null;
      boolean isDevicePortrait = deviceState.getOrientation() == ScreenOrientation.PORTRAIT;

      int x = Coordinates.getSwingX(myScreenView, isDevicePortrait ? mySmallDimension : myBigDimension);
      int y = Coordinates.getSwingY(myScreenView, isDevicePortrait ? myBigDimension : mySmallDimension);

      Graphics2D graphics = (Graphics2D)g2d.create();
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setColor(NlConstants.RESIZING_CORNER_COLOR);
      graphics.drawLine(x, y, x - NlConstants.RESIZING_CORNER_SIZE, y);
      graphics.drawLine(x, y, x, y - NlConstants.RESIZING_CORNER_SIZE);
      graphics.setColor(NlConstants.RESIZING_TEXT_COLOR);
      graphics.drawString(myName, x - myNameWidth - 5, y - 5);
      graphics.dispose();
    }
  }
}
