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

import com.android.annotations.Nullable;
import com.android.ide.common.res2.ResourceItem;
import com.android.ide.common.resources.configuration.*;
import com.android.resources.ResourceType;
import com.android.resources.ScreenOrientation;
import com.android.resources.ScreenRatio;
import com.android.sdklib.devices.State;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.uibuilder.graphics.NlConstants;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.ModelListener;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.lang.annotations.JdkConstants.InputEventMask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CanvasResizeInteraction extends Interaction {
  private static final double SQRT_2 = Math.sqrt(2.0);

  private final DesignSurface myDesignSurface;
  private final boolean isPreviewSurface;
  private final Set<FolderConfiguration> myFolderConfigurations;
  private final UnavailableSizesLayer myUnavailableLayer = new UnavailableSizesLayer();
  private final OrientationLayer myOrientationLayer;

  private int myCurrentX;
  private int myCurrentY;

  public CanvasResizeInteraction(DesignSurface designSurface) {
    myDesignSurface = designSurface;
    isPreviewSurface = designSurface.isPreviewSurface();
    myOrientationLayer = new OrientationLayer(myDesignSurface);

    Configuration config = myDesignSurface.getConfiguration();
    assert config != null;
    VirtualFile file = config.getFile();
    assert file != null;
    String layoutName = file.getNameWithoutExtension();
    ProjectResourceRepository resourceRepository = ProjectResourceRepository.getProjectResources(config.getModule(), true);
    assert resourceRepository != null;

    List<ResourceItem> layouts =
      resourceRepository.getItems().get(ResourceType.LAYOUT).get(layoutName);
    myFolderConfigurations = layouts.stream().map(ResourceItem::getConfiguration).collect(Collectors.toSet());
  }

  @Override
  public void begin(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int startMask) {
    super.begin(x, y, startMask);

    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }
    screenView.getSurface().setResizeMode(true);
    updateUnavailableLayer(screenView);
  }

  public void updatePosition(int x, int y) {
    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    screenView.getModel().overrideConfigurationScreenSize(Coordinates.getAndroidX(screenView, x),
                                                          Coordinates.getAndroidY(screenView, y));
    if (isPreviewSurface) {
      updateUnavailableLayer(screenView);
    }
  }

  private void updateUnavailableLayer(@NotNull ScreenView screenView) {
    Configuration config = screenView.getConfiguration();
    //noinspection ConstantConditions
    FolderConfiguration currentFolderConfig =
      FolderConfiguration.getConfigForFolder(config.getFile().getParent().getNameWithoutExtension());
    assert currentFolderConfig != null;

    if (currentFolderConfig.equals(myUnavailableLayer.getCurrentFolderConfig())) {
      return;
    }

    DesignSurface surface = screenView.getSurface();
    // Start with covering the full screen
    Area unavailable = new Area(new Rectangle(screenView.getX(), screenView.getY(), surface.getWidth(), surface.getHeight()));

    // Uncover the area associated with the current folder configuration
    unavailable.subtract(coveredAreaForConfig(currentFolderConfig, screenView));

    for (FolderConfiguration configuration : myFolderConfigurations) {
      if (!configuration.equals(currentFolderConfig) &&
          currentFolderConfig.isMatchFor(configuration) &&
          currentFolderConfig.compareTo(configuration) < 0) {
        // Cover the area associated with every folder configuration that would be preferred to the current one
        unavailable.add(coveredAreaForConfig(configuration, screenView));
      }
    }
    myUnavailableLayer.update(unavailable, currentFolderConfig);
  }

  /**
   * Returns the {@link Area} of the {@link ScreenView} that is covered by the given {@link FolderConfiguration}
   */
  @SuppressWarnings("SuspiciousNameCombination")
  @NotNull
  private Area coveredAreaForConfig(@NotNull FolderConfiguration config, @NotNull ScreenView screenView) {
    int x0 = screenView.getX();
    int y0 = screenView.getY();
    int width = myDesignSurface.getWidth();
    int height = myDesignSurface.getHeight();

    int maxDim = Math.max(width, height);
    int minX = 0;
    int maxX = -1;
    int minY = 0;
    int maxY = -1;

    int dpi = screenView.getConfiguration().getDensity().getDpiValue();
    SmallestScreenWidthQualifier smallestWidthQualifier = config.getSmallestScreenWidthQualifier();
    if (smallestWidthQualifier != null) {
      // Restrict the area due to a sw<N>dp qualifier
      minX = smallestWidthQualifier.getValue() * dpi / 160;
      minY = smallestWidthQualifier.getValue() * dpi / 160;
    }

    ScreenWidthQualifier widthQualifier = config.getScreenWidthQualifier();
    if (widthQualifier != null) {
      // Restrict the area due to a w<N>dp qualifier
      minX = Math.max(minX, widthQualifier.getValue() * dpi / 160);
    }

    ScreenHeightQualifier heightQualifier = config.getScreenHeightQualifier();
    if (heightQualifier != null) {
      // Restrict the area due to a h<N>dp qualifier
      minY = Math.max(minY, heightQualifier.getValue() * dpi / 160);
    }

    ScreenSizeQualifier sizeQualifier = config.getScreenSizeQualifier();
    if (sizeQualifier != null && sizeQualifier.getValue() != null) {
      // Restrict the area due to a screen size qualifier (SMALL, NORMAL, LARGE, XLARGE)
      switch (sizeQualifier.getValue()) {
        case SMALL:
          maxX = 320 * dpi / 160;
          maxY = 470 * dpi / 160;
          break;
        case NORMAL:
          break;
        case LARGE:
          minX = 480 * dpi / 160;
          minY = 640 * dpi / 160;
          break;
        case XLARGE:
          minX = 720 * dpi / 160;
          minY = 960 * dpi / 160;
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
    super.update(x, y, modifiers);
    myCurrentX = x;
    myCurrentY = y;

    // Only do full live updating of the file if we are in preview mode.
    // Otherwise, restrict it to the area associated with the current configuration of the layout.
    if (isPreviewSurface || myUnavailableLayer.isAvailable(x, y)) {
      updatePosition(x, y);
    }
  }

  @Override
  public void end(@SwingCoordinate int x, @SwingCoordinate int y, @InputEventMask int modifiers, boolean canceled) {
    super.end(x, y, modifiers, canceled);

    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return;
    }

    // Set the surface in resize mode so it doesn't try to re-center the screen views all the time
    screenView.getSurface().setResizeMode(false);

    // When disabling the resize mode, add a render handler to call zoomToFit
    screenView.getModel().addListener(new ModelListener() {
      @Override
      public void modelChanged(@NotNull NlModel model) {
      }

      @Override
      public void modelRendered(@NotNull NlModel model) {
        model.removeListener(this);
      }
    });

    updatePosition(x, y);
  }

  @Override
  public List<Layer> createOverlays() {
    return ImmutableList.of(myUnavailableLayer, myOrientationLayer, new ResizeLayer());
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
      ScreenView screenView = myDesignSurface.getCurrentScreenView();
      if (screenView == null) {
        return;
      }

      int x = screenView.getX();
      int y = screenView.getY();

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
  private static class UnavailableSizesLayer extends Layer {
    private Area myUnavailableArea;
    private FolderConfiguration myCurrentFolderConfig;

    @Override
    public void create() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      if (myUnavailableArea != null) {
        Graphics2D graphics = (Graphics2D)g2d.create();
        graphics.setColor(NlConstants.UNAVAILABLE_ZONE_COLOR);
        graphics.fill(myUnavailableArea);
        graphics.dispose();
      }
    }

    private boolean isAvailable(int x, int y) {
      return !myUnavailableArea.contains(x, y);
    }

    private void update(@NotNull Area unavailableArea, @NotNull FolderConfiguration currentFolderConfig) {
      myUnavailableArea = unavailableArea;
      myCurrentFolderConfig = currentFolderConfig;
    }

    @Nullable
    private FolderConfiguration getCurrentFolderConfig() {
      return myCurrentFolderConfig;
    }
  }

  private static class OrientationLayer extends Layer {
    private final Polygon myOrientationPolygon = new Polygon();
    private final double myPortraitWidth;
    private final double myLandscapeWidth;
    private final DesignSurface myDesignSurface;

    public OrientationLayer(@NotNull DesignSurface designSurface) {
      myDesignSurface = designSurface;
      FontMetrics fontMetrics = myDesignSurface.getFontMetrics(myDesignSurface.getFont());
      myPortraitWidth = fontMetrics.getStringBounds("Portrait", null).getWidth();
      myLandscapeWidth = fontMetrics.getStringBounds("Landscape", null).getWidth();
    }

    @Override
    public void paint(@NotNull Graphics2D g2d) {
      ScreenView screenView = myDesignSurface.getCurrentScreenView();
      if (screenView == null) {
        return;
      }

      State deviceState = screenView.getConfiguration().getDeviceState();
      assert deviceState != null;
      boolean isDevicePortrait = deviceState.getOrientation() == ScreenOrientation.PORTRAIT;

      JScrollPane scrollPane = myDesignSurface.getScrollPane();
      int height = scrollPane.getHeight() - scrollPane.getHorizontalScrollBar().getHeight();
      int width = scrollPane.getWidth() - scrollPane.getVerticalScrollBar().getWidth();
      int x0 = screenView.getX();
      int y0 = screenView.getY();
      int maxDim = Math.max(width, height);

      constructPolygon(myOrientationPolygon, null, maxDim, !isDevicePortrait);
      myOrientationPolygon.translate(x0, y0);

      Graphics2D graphics = (Graphics2D)g2d.create();
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

      graphics.setColor(Color.DARK_GRAY);
      graphics.rotate(-Math.PI / 4, xL, yL);
      graphics.drawString("Landscape", xL, yL);
      graphics.rotate(Math.PI / 4, xL, yL);

      graphics.rotate(-Math.PI / 4, xP, yP);
      graphics.drawString("Portrait", xP, yP);
      graphics.dispose();
    }
  }
}
