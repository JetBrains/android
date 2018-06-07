/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.resources.ScreenSize;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.surface.Interaction;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Area;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.MAX_MATCH_DISTANCE;

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
   * is not used; instead, all devices matching the tag (android-wear, android-tv) are used.
   */
  private static final String[] DEVICES_TO_SHOW = {"Nexus 5", "Nexus 6P", "Nexus 7", "Nexus 9", "Nexus 10"};

  @NotNull private final NlDesignSurface myDesignSurface;
  @NotNull private final ScreenView myScreenView;
  @NotNull private final Configuration myConfiguration;
  private final boolean isPreviewSurface;
  private final List<FolderConfiguration> myFolderConfigurations;
  private final UnavailableSizesLayer myUnavailableLayer = new UnavailableSizesLayer();
  private final OrientationLayer myOrientationLayer;
  private final SizeBucketLayer mySizeBucketLayer;
  private final List<DeviceLayer> myDeviceLayers = Lists.newArrayList();
  private final Device myOriginalDevice;
  private final State myOriginalDeviceState;
  private final Map<Point, Device> myAndroidCoordinatesToDeviceMap = Maps.newHashMapWithExpectedSize(DEVICES_TO_SHOW.length);
  private final MergingUpdateQueue myUpdateQueue;
  private final int myMaxSize;
  private final Update myLayerUpdate = new Update("CanvasResizeLayerUpdate") {
    @Override
    public void run() {
      mySizeBucketLayer.reset();
      myOrientationLayer.reset();
      updateUnavailableLayer(true);
    }
  };
  private final Update myPositionUpdate = new Update("CanvasResizePositionUpdate") {
    @Override
    public void run() {
      int androidX = Coordinates.getAndroidX(myScreenView, myCurrentX);
      int androidY = Coordinates.getAndroidY(myScreenView, myCurrentY);
      if (androidX > 0 && androidY > 0 && androidX < myMaxSize && androidY < myMaxSize) {
        NlModelHelperKt.overrideConfigurationScreenSize(myScreenView.getModel(), androidX, androidY);
        if (isPreviewSurface) {
          updateUnavailableLayer(false);
        }
      }
    }
  };
  private int myCurrentX;
  private int myCurrentY;

  public CanvasResizeInteraction(@NotNull NlDesignSurface designSurface,
                                 @NotNull ScreenView screenView,
                                 @NotNull Configuration configuration) {
    myDesignSurface = designSurface;
    myScreenView = screenView;
    myConfiguration = configuration;
    isPreviewSurface = designSurface.isPreviewSurface();
    myOrientationLayer = new OrientationLayer(myDesignSurface, myScreenView, myConfiguration);
    mySizeBucketLayer = new SizeBucketLayer();
    myUpdateQueue = new MergingUpdateQueue("layout.editor.canvas.resize", 25, true, null, myDesignSurface);
    myUpdateQueue.setRestartTimerOnAdd(true);

    myOriginalDevice = configuration.getDevice();
    myOriginalDeviceState = configuration.getDeviceState();

    VirtualFile file = configuration.getFile();
    assert file != null;
    String layoutName = file.getNameWithoutExtension();
    ProjectResourceRepository resourceRepository = ProjectResourceRepository.getOrCreateInstance(configuration.getModule());
    assert resourceRepository != null;

    // TODO: namespaces
    List<ResourceItem> layouts =
      resourceRepository.getItems().get(null, ResourceType.LAYOUT).get(layoutName);
    myFolderConfigurations =
      layouts.stream().map(ResourceItem::getConfiguration).sorted(Collections.reverseOrder()).collect(Collectors.toList());

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
      Point p = new Point((int)(screen.getXDimension() * dpiRatio), (int)(screen.getYDimension() * dpiRatio));
      myAndroidCoordinatesToDeviceMap.put(p, device);
      myDeviceLayers.add(new DeviceLayer(myDesignSurface, myScreenView, myConfiguration, p.x, p.y, device.getDisplayName()));
    }

    if (addSmallScreen) {
      myDeviceLayers.add(new DeviceLayer(myDesignSurface, myScreenView, myConfiguration, (int)(426 * currentDpi / DEFAULT_DENSITY),
                                         (int)(320 * currentDpi / DEFAULT_DENSITY), "Small Screen"));
    }

    myMaxSize = (int)(MAX_ANDROID_SIZE * currentDpi / DEFAULT_DENSITY);
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int startMask) {
    super.begin(x, y, startMask);
    myCurrentX = x;
    myCurrentY = y;

    myDesignSurface.setResizeMode(true);
    updateUnavailableLayer(false);
  }

  private void updateUnavailableLayer(boolean forceRecompute) {
    //noinspection ConstantConditions
    FolderConfiguration currentFolderConfig =
      FolderConfiguration.getConfigForFolder(myConfiguration.getFile().getParent().getNameWithoutExtension());
    assert currentFolderConfig != null;

    if (!forceRecompute && currentFolderConfig.equals(myUnavailableLayer.getCurrentFolderConfig())) {
      return;
    }

    List<Area> configAreas = Lists.newArrayList();
    Area totalCoveredArea = new Area();
    for (FolderConfiguration configuration : myFolderConfigurations) {
      Area configArea = coveredAreaForConfig(configuration, myScreenView);
      configArea.subtract(totalCoveredArea);
      if (!configuration.equals(currentFolderConfig)) {
        configAreas.add(configArea);
      }
      totalCoveredArea.add(configArea);
    }

    myUnavailableLayer.update(configAreas, currentFolderConfig);
  }

  /**
   * Returns the {@link Area} of the {@link ScreenView} that is covered by the given {@link FolderConfiguration}
   */
  @SuppressWarnings("SuspiciousNameCombination")
  @NotNull
  private Area coveredAreaForConfig(@NotNull FolderConfiguration config, @NotNull SceneView screenView) {
    int x0 = screenView.getX();
    int y0 = screenView.getY();
    JComponent layeredPane = myDesignSurface.getLayeredPane();
    int width = layeredPane.getWidth();
    int height = layeredPane.getHeight();

    int maxDim = Math.max(width, height);
    int minX = 0;
    int maxX = -1;
    int minY = 0;
    int maxY = -1;

    SmallestScreenWidthQualifier smallestWidthQualifier = config.getSmallestScreenWidthQualifier();
    if (smallestWidthQualifier != null) {
      // Restrict the area due to a sw<N>dp qualifier
      minX = Coordinates.dpToPx(screenView, smallestWidthQualifier.getValue());
      minY = Coordinates.dpToPx(screenView, smallestWidthQualifier.getValue());
    }

    ScreenWidthQualifier widthQualifier = config.getScreenWidthQualifier();
    if (widthQualifier != null) {
      // Restrict the area due to a w<N>dp qualifier
      minX = Math.max(minX, Coordinates.dpToPx(screenView, widthQualifier.getValue()));
    }

    ScreenHeightQualifier heightQualifier = config.getScreenHeightQualifier();
    if (heightQualifier != null) {
      // Restrict the area due to a h<N>dp qualifier
      minY = Math.max(minY, Coordinates.dpToPx(screenView, heightQualifier.getValue()));
    }

    ScreenSizeQualifier sizeQualifier = config.getScreenSizeQualifier();
    if (sizeQualifier != null && sizeQualifier.getValue() != null) {
      // Restrict the area due to a screen size qualifier (SMALL, NORMAL, LARGE, XLARGE)
      switch (sizeQualifier.getValue()) {
        case SMALL:
          maxX = Coordinates.dpToPx(screenView, 320);
          maxY = Coordinates.dpToPx(screenView, 470);
          break;
        case NORMAL:
          break;
        case LARGE:
          minX = Coordinates.dpToPx(screenView, 480);
          minY = Coordinates.dpToPx(screenView, 640);
          break;
        case XLARGE:
          minX = Coordinates.dpToPx(screenView, 720);
          minY = Coordinates.dpToPx(screenView, 960);
          break;
      }
    }

    ScreenRatioQualifier ratioQualifier = config.getScreenRatioQualifier();
    ScreenRatio ratio = ratioQualifier != null ? ratioQualifier.getValue() : null;

    ScreenOrientationQualifier orientationQualifier = config.getScreenOrientationQualifier();
    ScreenOrientation orientation = orientationQualifier != null ? orientationQualifier.getValue() : null;

    Polygon portrait = new Polygon();
    Polygon landscape = new Polygon();

    if (orientation == null || orientation.equals(ScreenOrientation.PORTRAIT)) {
      constructPolygon(portrait, ratio, maxDim, true);
      portrait.translate(x0, y0);
    }

    if (orientation == null || orientation.equals(ScreenOrientation.LANDSCAPE)) {
      constructPolygon(landscape, ratio, maxDim, false);
      landscape.translate(x0, y0);
    }

    Area portraitArea = new Area(portrait);
    Area landscapeArea = new Area(landscape);

    Area portraitBounds = new Area(new Rectangle(Coordinates.getSwingX(screenView, minX), Coordinates.getSwingY(screenView, minY),
                                                 maxX >= 0 ? Coordinates.getSwingDimension(screenView, maxX - minX) : width,
                                                 maxY >= 0 ? Coordinates.getSwingDimension(screenView, maxY - minY) : height));
    Area landscapeBounds = new Area(new Rectangle(Coordinates.getSwingX(screenView, minY), Coordinates.getSwingY(screenView, minX),
                                                  maxY >= 0 ? Coordinates.getSwingDimension(screenView, maxY - minY) : width,
                                                  maxX >= 0 ? Coordinates.getSwingDimension(screenView, maxX - minX) : height));

    portraitArea.intersect(portraitBounds);
    landscapeArea.intersect(landscapeBounds);
    portraitArea.add(landscapeArea);
    return portraitArea;
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
  public void update(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers) {
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

    super.update(x, y, modifiers);
    myCurrentX = x;
    myCurrentY = y;

    JComponent layeredPane = myDesignSurface.getLayeredPane();
    int maxX = Coordinates.getSwingX(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_X;
    int maxY = Coordinates.getSwingY(myScreenView, myMaxSize) + NlConstants.DEFAULT_SCREEN_OFFSET_Y;
    if (x < maxX && y < maxY && (x > layeredPane.getWidth() || y > layeredPane.getHeight())) {
      Dimension d = layeredPane.getPreferredSize();
      layeredPane.setPreferredSize(new Dimension(Math.max(d.width, x), Math.max(d.height, y)));
      layeredPane.revalidate();
      myUpdateQueue.queue(myLayerUpdate);
    }

    // Only do full live updating of the file if we are in preview mode.
    // Otherwise, restrict it to the area associated with the current configuration of the layout.
    if (isPreviewSurface || myUnavailableLayer.isAvailable(x, y)) {
      myUpdateQueue.queue(myPositionUpdate);
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);

    // Set the surface in resize mode so it doesn't try to re-center the screen views all the time
    myDesignSurface.setResizeMode(false);

    int androidX = Coordinates.getAndroidX(myScreenView, x);
    int androidY = Coordinates.getAndroidY(myScreenView, y);

    if (canceled || androidX < 0 || androidY < 0) {
      myConfiguration.setEffectiveDevice(myOriginalDevice, myOriginalDeviceState);
    }
    else {
      int snapThreshold = Coordinates.getAndroidDimension(myScreenView, MAX_MATCH_DISTANCE);
      Device deviceToSnap = snapToDevice(androidX, androidY, snapThreshold);
      if (deviceToSnap != null) {
        State deviceState = deviceToSnap.getState(androidX < androidY ? "Portrait" : "Landscape");
        myConfiguration.setEffectiveDevice(deviceToSnap, deviceState);
      }
      else {
        NlModelHelperKt.overrideConfigurationScreenSize(myScreenView.getModel(), androidX, androidY);
      }
    }
  }

  /**
   * Returns the device to snap to when at (x, y) in Android coordinates.
   * If there is no such device, returns null.
   */
  @Nullable/*null if no device is close enough to snap to*/
  private Device snapToDevice(@AndroidCoordinate int x, @AndroidCoordinate int y, int threshold) {
    for (Point p : myAndroidCoordinatesToDeviceMap.keySet()) {
      if ((Math.abs(x - p.x) < threshold && Math.abs(y - p.y) < threshold)
          || (Math.abs(y - p.x) < threshold && Math.abs(x - p.y) < threshold)) {
        return myAndroidCoordinatesToDeviceMap.get(p);
      }
    }
    return null;
  }

  @Override
  public synchronized List<Layer> createOverlays() {
    ImmutableList.Builder<Layer> layers = ImmutableList.builder();

    // Only show size buckets for mobile, not wear, tv, etc.
    if (HardwareConfigHelper.isMobile(myOriginalDevice)) {
      layers.add(mySizeBucketLayer);
    }

    layers.add(myUnavailableLayer);
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
    public void create() {
    }

    @Override
    public void dispose() {
    }

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

  /**
   * An {@link Layer} for the {@link CanvasResizeInteraction}.
   * Greys out the {@link Area} unavailableArea.
   */
  private class UnavailableSizesLayer extends Layer {
    private Polygon myClip = new Polygon();
    private List<Area> myConfigAreas;
    private FolderConfiguration myCurrentFolderConfig;

    @Override
    public synchronized void paint(@NotNull Graphics2D g2d) {
      State deviceState = myConfiguration.getDeviceState();
      assert deviceState != null;
      boolean isDevicePortrait = deviceState.getOrientation() == ScreenOrientation.PORTRAIT;

      JComponent layeredPane = myDesignSurface.getLayeredPane();
      constructPolygon(myClip, null, Math.max(layeredPane.getWidth(), layeredPane.getHeight()), isDevicePortrait);
      myClip.translate(myScreenView.getX() + 1, myScreenView.getY() + 1);

      Graphics2D graphics = (Graphics2D)g2d.create();
      graphics.clip(myClip);

      int n = 0;
      for (Area configArea : myConfigAreas) {
        graphics.setColor(NlConstants.RESIZING_OTHER_CONFIG_COLOR_ARRAY[n++ % NlConstants.RESIZING_OTHER_CONFIG_COLOR_ARRAY.length]);
        graphics.fill(configArea);
      }

      graphics.dispose();
    }

    private boolean isAvailable(int x, int y) {
      for (Area configArea : myConfigAreas) {
        if (configArea.contains(x, y)) {
          return false;
        }
      }
      return true;
    }

    private synchronized void update(@NotNull List<Area> configAreas, @NotNull FolderConfiguration currentFolderConfig) {
      myConfigAreas = configAreas;
      myCurrentFolderConfig = currentFolderConfig;
    }

    @Nullable
    private FolderConfiguration getCurrentFolderConfig() {
      return myCurrentFolderConfig;
    }
  }

  private static class DeviceLayer extends Layer {
    private final String myName;
    @NotNull private final NlDesignSurface myDesignSurface;
    @NotNull private final ScreenView myScreenView;
    @NotNull private final Configuration myConfiguration;
    private final int myNameWidth;
    private int myBigDimension;
    private int mySmallDimension;

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
        JComponent layeredPane = myDesignSurface.getLayeredPane();
        JScrollPane scrollPane = myDesignSurface.getScrollPane();
        int height = layeredPane.getHeight() - scrollPane.getHorizontalScrollBar().getHeight();
        int width = layeredPane.getWidth() - scrollPane.getVerticalScrollBar().getWidth();
        int x0 = myScreenView.getX();
        int y0 = myScreenView.getY();
        int maxDim = Math.max(width, height);
        image = UIUtil.createImage(maxDim, maxDim, BufferedImage.TYPE_INT_ARGB);

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
      UIUtil.drawImage(g2d, image, null, 0, 0);
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
      JScrollPane scrollPane = myDesignSurface.getScrollPane();
      JComponent layeredPane = myDesignSurface.getLayeredPane();
      myTotalHeight = layeredPane.getHeight() - scrollPane.getHorizontalScrollBar().getHeight();
      myTotalWidth = layeredPane.getWidth() - scrollPane.getVerticalScrollBar().getWidth();
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

      int width = Coordinates.getAndroidX(myScreenView, myCurrentX);
      int height = Coordinates.getAndroidY(myScreenView, myCurrentY);
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
        bucket = UIUtil.createImage(myTotalWidth, myTotalHeight, BufferedImage.TYPE_INT_ARGB);
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
      UIUtil.drawImage(g2d, bucket, null, 0, 0);
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
      JScrollPane scrollPane = myDesignSurface.getScrollPane();
      JComponent layeredPane = myDesignSurface.getLayeredPane();
      myTotalHeight = layeredPane.getHeight() - scrollPane.getHorizontalScrollBar().getHeight();
      myTotalWidth = layeredPane.getWidth() - scrollPane.getVerticalScrollBar().getWidth();
      myPortraitBuckets.clear();
      myLandscapeBuckets.clear();
    }
  }
}
