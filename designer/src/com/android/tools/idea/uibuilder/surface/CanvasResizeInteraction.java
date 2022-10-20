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
package com.android.tools.idea.uibuilder.surface;

import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.MAX_MATCH_DISTANCE;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.InteractionEvent;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.MouseDraggedEvent;
import com.android.tools.idea.common.surface.MousePressedEvent;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.ImageUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CanvasResizeInteraction extends Interaction {
  private static final double SQRT_2 = Math.sqrt(2.0);
  /**
   * Cut-off size (in dp) for resizing, it should be bigger than any Android device. Resizing size needs to be capped
   * because layoutlib will create an image of the size of the device, which will cause an OOM error when the
   * device is too large.
   */
  // TODO: Make it possible to resize to arbitrary large sizes without running out of memory
  private static final int MAX_ANDROID_SIZE = 1500;
  /**
   * Specific subset of the phones/tablets to show when resizing; for tv and wear, this list
   * is not used; instead, all devices matching the tag (android-wear, android-tv) are used. @see nexus.xml
   */
  private static final String[] DEVICES_TO_SHOW = {"Nexus 5", "Nexus 7", "Nexus 9", "Nexus 10", "pixel_2", "pixel_3", "pixel_3_xl"};

  @NotNull private final NlDesignSurface myDesignSurface;
  @NotNull private final ScreenView myScreenView;
  @NotNull private final Configuration myConfiguration;
  private final OrientationLayer myOrientationLayer;
  private final SizeBucketLayer mySizeBucketLayer;
  private final List<DeviceLayer> myDeviceLayers = new ArrayList<>();
  private final Device myOriginalDevice;
  private final State myOriginalDeviceState;
  private final DeviceSizeList myDeviceSizeList = new DeviceSizeList();
  private final MergingUpdateQueue myUpdateQueue;
  private final int myMaxSize;
  private final Update myLayerUpdate = new Update("CanvasResizeLayerUpdate") {
    @Override
    public void run() {
      mySizeBucketLayer.reset();
      myOrientationLayer.reset();
    }
  };
  private final Update myPositionUpdate = new Update("CanvasResizePositionUpdate") {
    @Override
    public void run() {
      int androidX = Coordinates.getAndroidX(myScreenView, myCurrentX);
      int androidY = Coordinates.getAndroidY(myScreenView, myCurrentY);
      if (androidX > 0 && androidY > 0 && androidX < myMaxSize && androidY < myMaxSize) {
        NlModelHelperKt.updateConfigurationScreenSize(myConfiguration, androidX, androidY);
      }
    }
  };
  private int myCurrentX;
  private int myCurrentY;
  @Nullable private DeviceSizeList.DeviceSize myLastSnappedDevice;

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
    myOrientationLayer = new OrientationLayer(myDesignSurface, myScreenView, myConfiguration);
    mySizeBucketLayer = new SizeBucketLayer();
    myUpdateQueue = new MergingUpdateQueue("layout.editor.canvas.resize", 100, true, null, myDesignSurface);
    myUpdateQueue.setRestartTimerOnAdd(true);

    myOriginalDevice = configuration.getCachedDevice();
    myOriginalDeviceState = configuration.getDeviceState();

    double currentDpi = configuration.getDensity().getDpiValue();
    ConfigurationManager configManager = configuration.getConfigurationManager();

    boolean addSmallScreen = false;
    List<Device> devicesToShow;
    if (HardwareConfigHelper.isWear(myOriginalDevice)) {
      devicesToShow = configManager.getDevices().stream().filter(
        d -> HardwareConfigHelper.isWear(d) && !Configuration.CUSTOM_DEVICE_ID.equals(d.getId())).collect(Collectors.toList());
    }
    else if (HardwareConfigHelper.isTv(myOriginalDevice)) {
      // There are only two devices and they have the same dip sizes, so just use one of them
      devicesToShow = Collections.singletonList(configManager.getDeviceById("tv_1080p"));
    }
    else {
      devicesToShow = Lists.newArrayListWithExpectedSize(DEVICES_TO_SHOW.length);
      for (String id : DEVICES_TO_SHOW) {
        devicesToShow.add(configManager.getDeviceById(id));
      }
      addSmallScreen = true;
    }

    for (Device device : devicesToShow) {
      assert device != null;
      Screen screen = device.getDefaultHardware().getScreen();
      double dpiRatio = currentDpi / screen.getPixelDensity().getDpiValue();
      int px = (int)(screen.getXDimension() * dpiRatio);
      int py = (int)(screen.getYDimension() * dpiRatio);
      myDeviceSizeList.add(device, px, py);
      myDeviceLayers.add(new DeviceLayer(myDesignSurface, myScreenView, myConfiguration, px, py, device.getDisplayName()));
    }
    myDeviceSizeList.sort();

    if (addSmallScreen) {
      myDeviceLayers.add(new DeviceLayer(myDesignSurface, myScreenView, myConfiguration, (int)(426 * currentDpi / DEFAULT_DENSITY),
                                                                 (int)(320 * currentDpi / DEFAULT_DENSITY), "Small Screen"));
    }

    myMaxSize = (int)(MAX_ANDROID_SIZE * currentDpi / DEFAULT_DENSITY);
  }

  @Override
  public void begin(@NotNull InteractionEvent event) {
    if (event instanceof MousePressedEvent) {
      MouseEvent mouseEvent = ((MousePressedEvent)event).getEventObject();
      begin(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
    }
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    super.begin(x, y, modifiersEx);
    myCurrentX = x;
    myCurrentY = y;

    myDesignSurface.setResizeMode(true);
  }

  private static void constructPolygon(@NotNull Polygon polygon, @Nullable ScreenRatio ratio, int dim, boolean isPortrait) {
    polygon.reset();
    int x1 = isPortrait ? 0 : dim;
    int y1 = isPortrait ? dim : 0;
    int x2 = isPortrait ? dim : 5 * dim / 3;
    int y2 = isPortrait ? 5 * dim / 3 : dim;

    polygon.addPoint(0, 0);
    if (ratio == null) {
      polygon.addPoint(x1, y1);
      polygon.addPoint(dim, dim);
    }
    else if (ratio == ScreenRatio.LONG) {
      polygon.addPoint(x1, y1);
      polygon.addPoint(x2, y2);
    }
    else {
      polygon.addPoint(x2, y2);
      polygon.addPoint(dim, dim);
    }
  }

  @Override
  public void update(@NotNull InteractionEvent event) {
    if (event instanceof MouseDraggedEvent) {
      MouseEvent mouseEvent = ((MouseDraggedEvent)event).getEventObject();
      update(mouseEvent.getX(), mouseEvent.getY(), mouseEvent.getModifiersEx());
    }
  }

  @Override
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    if (myOriginalDevice.isScreenRound()) {
      // Force aspect preservation
      int deltaX = x - myStartX;
      int deltaY = y - myStartY;
      if (deltaX > deltaY) {
        y = myStartY + deltaX;
      }
      else {
        x = myStartX + deltaY;
      }
    }

    snapToDevice(x, y);
    super.update(myCurrentX, myCurrentY, modifiersEx);

    Dimension viewSize = myDesignSurface.getViewSize();
    int maxX = Coordinates.getSwingX(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_X;
    int maxY = Coordinates.getSwingY(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_Y;
    if (myCurrentX < maxX &&
        myCurrentY < maxY &&
        (myCurrentX + myResizeTriggerThreshold * 2 > viewSize.getWidth() ||
         myCurrentY + myResizeTriggerThreshold * 2 > viewSize.getHeight())) {
      // Extend the scrollable area of the surface to accommodate for the resize
      myDesignSurface.setScrollableViewMinSize(new Dimension(myCurrentX + myResizeTriggerThreshold, myCurrentY + myResizeTriggerThreshold));
      myDesignSurface.validateScrollArea();
      myUpdateQueue.queue(myLayerUpdate);
    }

    myUpdateQueue.queue(myPositionUpdate);
  }

  private void snapToDevice(int x, int y) {
    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);
    int snapThreshold = Coordinates.getAndroidDimension(myScreenView, MAX_MATCH_DISTANCE);
    myLastSnappedDevice = myDeviceSizeList.snapToDevice(androidX, androidY, snapThreshold);

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
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    end(event.getInfo().getX(), event.getInfo().getY(), event.getInfo().getModifiersEx());
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
    // Set the surface in resize mode so it doesn't try to re-center the screen views all the time
    myDesignSurface.setResizeMode(false);
    myDesignSurface.setScrollableViewMinSize(new Dimension(0, 0));

    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);

    if (androidX < 0 || androidY < 0) {
      myConfiguration.setEffectiveDevice(myOriginalDevice, myOriginalDeviceState);
    }
    else {
      if (myLastSnappedDevice != null) {
        Device deviceToSnap = myLastSnappedDevice.getDevice();
        State deviceState = deviceToSnap.getState(androidX < androidY ? "Portrait" : "Landscape");
        myConfiguration.setEffectiveDevice(deviceToSnap, deviceState);
      }
      else {
        NlModelHelperKt.updateConfigurationScreenSize(myConfiguration, androidX, androidY);
      }
    }
  }

  @Override
  public void cancel(@NotNull InteractionEvent event) {
    //noinspection MagicConstant // it is annotated as @InputEventMask in Kotlin.
    cancel(event.getInfo().getX(), event.getInfo().getY(), event.getInfo().getModifiersEx());
  }

  @Override
  public void cancel(@SwingCoordinate int x, @SwingCoordinate int y, @JdkConstants.InputEventMask int modifiersEx) {
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

    // Only show size buckets for mobile, not wear, tv, etc.
    if (HardwareConfigHelper.isMobile(myOriginalDevice)) {
      layers.add(mySizeBucketLayer);
    }

    layers.addAll(myDeviceLayers);
    layers.add(myOrientationLayer);
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
    @NotNull private final NlDesignSurface myDesignSurface;
    @NotNull private final ScreenView myScreenView;
    @NotNull private final Configuration myConfiguration;
    private final int myNameWidth;
    private final int myBigDimension;
    private final int mySmallDimension;

    public DeviceLayer(@NotNull NlDesignSurface designSurface, @NotNull ScreenView screenView, @NotNull Configuration configuration,
                       int pxWidth, int pxHeight, @NotNull String name) {
      myDesignSurface = designSurface;
      myScreenView = screenView;
      myConfiguration = configuration;
      myBigDimension = Math.max(pxWidth, pxHeight);
      mySmallDimension = Math.min(pxWidth, pxHeight);
      myName = name;
      FontMetrics fontMetrics = myDesignSurface.getFontMetrics(myDesignSurface.getFont());
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

  private static class OrientationLayer extends Layer {
    private final Polygon myOrientationPolygon = new Polygon();
    private final double myPortraitWidth;
    private final double myLandscapeWidth;
    @NotNull private final NlDesignSurface myDesignSurface;
    @NotNull private final ScreenView myScreenView;
    @NotNull private final Configuration myConfiguration;
    private BufferedImage myOrientationImage;
    private ScreenOrientation myLastOrientation;

    public OrientationLayer(@NotNull NlDesignSurface designSurface, @NotNull ScreenView screenView, @NotNull Configuration configuration) {
      myDesignSurface = designSurface;
      myScreenView = screenView;
      myConfiguration = configuration;
      FontMetrics fontMetrics = myDesignSurface.getFontMetrics(myDesignSurface.getFont());
      myPortraitWidth = fontMetrics.getStringBounds("Portrait", null).getWidth();
      myLandscapeWidth = fontMetrics.getStringBounds("Landscape", null).getWidth();
    }

    @Override
    public synchronized void paint(@NotNull Graphics2D g2d) {
      State deviceState = myConfiguration.getDeviceState();
      assert deviceState != null;
      ScreenOrientation currentOrientation = deviceState.getOrientation();
      boolean isDevicePortrait = currentOrientation == ScreenOrientation.PORTRAIT;

      BufferedImage image = currentOrientation == myLastOrientation ? myOrientationImage : null;

      if (image == null) {
        myLastOrientation = currentOrientation;
        int height = myDesignSurface.getExtentSize().height;
        int width = myDesignSurface.getExtentSize().width;
        int x0 = myScreenView.getX();
        int y0 = myScreenView.getY();
        int maxDim = Math.max(width, height);
        image = ImageUtil.createImage(maxDim, maxDim, BufferedImage.TYPE_INT_ARGB);

        constructPolygon(myOrientationPolygon, null, maxDim, !isDevicePortrait);
        myOrientationPolygon.translate(x0, y0);

        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(NlConstants.UNAVAILABLE_ZONE_COLOR);
        graphics.fill(myOrientationPolygon);

        int xL;
        int yL;
        int xP;
        int yP;
        if (height - width < myPortraitWidth / SQRT_2) {
          xP = height - y0 + x0 - (int)(myPortraitWidth * SQRT_2) - 5;
          yP = height;
        }
        else {
          xP = width - (int)(myPortraitWidth / SQRT_2) - 5;
          yP = width - x0 + y0 + (int)(myPortraitWidth / SQRT_2);
        }

        if (height - width < y0 - x0 - myLandscapeWidth / SQRT_2) {
          xL = height - y0 + x0 + 5;
          yL = height;
        }
        else {
          xL = width - (int)(myLandscapeWidth / SQRT_2);
          yL = width - x0 + y0 - (int)(myLandscapeWidth / SQRT_2) - 5;
        }

        graphics.setColor(NlConstants.RESIZING_TEXT_COLOR);
        graphics.rotate(-Math.PI / 4, xL, yL);
        graphics.drawString("Landscape", xL, yL);
        graphics.rotate(Math.PI / 4, xL, yL);

        graphics.rotate(-Math.PI / 4, xP, yP);
        graphics.drawString("Portrait", xP, yP);
        graphics.dispose();
        myOrientationImage = image;
      }
      StartupUiUtil.drawImage(g2d, image);
    }

    public synchronized void reset() {
      myOrientationImage = null;
    }
  }

  private class SizeBucketLayer extends Layer {
    private final Polygon myClip = new Polygon();
    private final FontMetrics myFontMetrics;
    private final Map<ScreenSize, SoftReference<BufferedImage>> myPortraitBuckets;
    private final Map<ScreenSize, SoftReference<BufferedImage>> myLandscapeBuckets;
    private int myTotalHeight;
    private int myTotalWidth;

    public SizeBucketLayer() {
      myTotalHeight = myDesignSurface.getExtentSize().height;
      myTotalWidth = myDesignSurface.getExtentSize().width;
      myFontMetrics = myDesignSurface.getFontMetrics(myDesignSurface.getFont());
      int screenSizeNumbers = ScreenSize.values().length;
      myPortraitBuckets = Maps.newHashMapWithExpectedSize(screenSizeNumbers);
      myLandscapeBuckets = Maps.newHashMapWithExpectedSize(screenSizeNumbers);
    }

    @Override
    public synchronized void paint(@NotNull Graphics2D g2d) {
      State deviceState = myConfiguration.getDeviceState();
      assert deviceState != null;
      boolean isDevicePortrait = deviceState.getOrientation() == ScreenOrientation.PORTRAIT;

      int width = Coordinates.getAndroidXDip(myScreenView, myCurrentX);
      int height = Coordinates.getAndroidYDip(myScreenView, myCurrentY);
      if ((width > height && isDevicePortrait) || (width < height && !isDevicePortrait)) {
        return;
      }

      int small = Math.min(width, height);
      int big = Math.max(width, height);

      ScreenSize screenSizeBucket = getScreenSizeBucket(small, big);

      Map<ScreenSize, SoftReference<BufferedImage>> buckets = isDevicePortrait ? myPortraitBuckets : myLandscapeBuckets;
      SoftReference<BufferedImage> bucketRef = buckets.get(screenSizeBucket);
      BufferedImage bucket = bucketRef != null ? bucketRef.get() : null;
      if (bucket == null) {
        bucket = ImageUtil.createImage(myTotalWidth, myTotalHeight, BufferedImage.TYPE_INT_ARGB);
        constructPolygon(myClip, null, Math.max(myTotalHeight, myTotalWidth), isDevicePortrait);
        myClip.translate(myScreenView.getX(), myScreenView.getY());

        Graphics2D graphics = bucket.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.clip(myClip);
        graphics.setColor(NlConstants.RESIZING_BUCKET_COLOR);
        Area coveredArea = getAreaForScreenSize(screenSizeBucket, myScreenView, isDevicePortrait);
        graphics.fill(coveredArea);

        graphics.setColor(NlConstants.RESIZING_CORNER_COLOR);
        graphics.setStroke(NlConstants.THICK_SOLID_STROKE);
        graphics.draw(coveredArea);

        graphics.setColor(NlConstants.RESIZING_TEXT_COLOR);
        Rectangle bounds = coveredArea.getBounds();
        if (isDevicePortrait) {
          int left = bounds.x + 5;
          int bottom = Math.min(bounds.y + bounds.height, myTotalHeight) - 5;
          graphics.drawString(screenSizeBucket.getShortDisplayValue() + " size range", left, bottom);
        }
        else {
          String text = screenSizeBucket.getShortDisplayValue() + " size range";
          Rectangle2D textBounds = myFontMetrics.getStringBounds(text, null);
          int left = (int)(Math.min(bounds.x + bounds.width, myTotalWidth) - textBounds.getWidth() - 5);
          int bottom = (int)(bounds.y + textBounds.getHeight());

          graphics.drawString(text, left, bottom);
        }
        graphics.dispose();
        buckets.put(screenSizeBucket, new SoftReference<>(bucket));
      }
      StartupUiUtil.drawImage(g2d, bucket);
    }

    @NotNull
    private ScreenSize getScreenSizeBucket(int small, int big) {
      if (big < 470) {
        return ScreenSize.SMALL;
      }
      if (big >= 960 && small >= 720) {
        return ScreenSize.XLARGE;
      }
      if (big >= 640 && small >= 480) {
        return ScreenSize.LARGE;
      }
      return ScreenSize.NORMAL;
    }

    @NotNull
    private Area getAreaForScreenSize(@NotNull ScreenSize screenSize, @NotNull SceneView screenView, boolean isDevicePortrait) {
      int x0 = screenView.getX();
      int y0 = screenView.getY();

      int smallX = Coordinates.getSwingXDip(screenView, 470);
      int smallY = Coordinates.getSwingYDip(screenView, 470);
      Area smallArea = new Area(new Rectangle(x0 - 2, y0 - 2, smallX - x0 + 2, smallY - y0 + 2));
      if (screenSize == ScreenSize.SMALL) {
        return smallArea;
      }

      int xlargeX = Coordinates.getSwingXDip(screenView, isDevicePortrait ? 720 : 960);
      int xlargeY = Coordinates.getSwingYDip(screenView, isDevicePortrait ? 960 : 720);
      Area xlargeArea = new Area(new Rectangle(xlargeX, xlargeY, myTotalWidth, myTotalHeight));
      if (screenSize == ScreenSize.XLARGE) {
        return xlargeArea;
      }

      int largeX = Coordinates.getSwingXDip(screenView, isDevicePortrait ? 480 : 640);
      int largeY = Coordinates.getSwingYDip(screenView, isDevicePortrait ? 640 : 480);
      Area largeArea = new Area(new Rectangle(largeX, largeY, myTotalWidth, myTotalHeight));
      if (screenSize == ScreenSize.LARGE) {
        largeArea.subtract(xlargeArea);
        return largeArea;
      }

      Area normalArea = new Area(new Rectangle(x0 - 2, y0 - 2, myTotalWidth, myTotalHeight));
      normalArea.subtract(smallArea);
      normalArea.subtract(largeArea);
      return normalArea;
    }

    public synchronized void reset() {
      myTotalHeight = myDesignSurface.getExtentSize().height;
      myTotalWidth = myDesignSurface.getExtentSize().width;
      myPortraitBuckets.clear();
      myLandscapeBuckets.clear();
    }
  }
}
