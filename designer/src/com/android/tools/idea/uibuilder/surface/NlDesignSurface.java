/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.ZoomController;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.layout.SurfaceLayoutOption;
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager;
import com.android.tools.idea.common.layout.scroller.DesignSurfaceViewportScroller;
import com.android.tools.idea.common.layout.scroller.ZoomCenterScroller;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.DesignSurfaceHelper;
import com.android.tools.idea.common.surface.Interactable;
import com.android.tools.idea.common.surface.InteractionHandler;
import com.android.tools.idea.common.surface.LayoutScannerControl;
import com.android.tools.idea.common.surface.ScaleChange;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SurfaceScale;
import com.android.tools.idea.common.surface.ZoomControlsPolicy;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GroupedListSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.ListLayoutManager;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.scale.JBUIScale;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlDesignSurface extends NlSurface {

  @NotNull
  @Override
  public ZoomController getZoomController() {
    return myZoomController;
  }

  private final AccessoryPanel myAccessoryPanel = new AccessoryPanel(AccessoryPanel.Type.SOUTH_PANEL, true);
  @NotNull private final DesignerAnalyticsManager myAnalyticsManager;

  @Nullable private final LayoutScannerControl myScannerControl;

  private final ErrorQueue myErrorQueue = new ErrorQueue(this, getProject());

  private final VisualLintIssueProvider myVisualLintIssueProvider;

  private final NlDesignSurfaceZoomController myZoomController;

  NlDesignSurface(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          @NotNull Function2<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider,
                          @NotNull SurfaceLayoutOption defaultLayoutOption,
                          @NotNull Function1<DesignSurface<LayoutlibSceneManager>, ActionManager<? extends DesignSurface<LayoutlibSceneManager>>> actionManagerProvider,
                          @NotNull Function1<DesignSurface<LayoutlibSceneManager>, Interactable> interactableProvider,
                          @NotNull Function1<DesignSurface<LayoutlibSceneManager>, InteractionHandler> interactionHandlerProvider,
                          @SurfaceScale double minScale,
                          @SurfaceScale double maxScale,
                          @NotNull Function1<DesignSurface<LayoutlibSceneManager>, DesignSurfaceActionHandler> actionHandlerProvider,
                          @Nullable DataProvider delegateDataProvider,
                          @NotNull SelectionModel selectionModel,
                          ZoomControlsPolicy zoomControlsPolicy,
                          @NotNull Supplier<ImmutableSet<NlSupportedActions>> supportedActionsProvider,
                          boolean shouldRenderErrorsPanel,
                          double maxZoomToFitLevel,
                          @NotNull Function1<DesignSurface<LayoutlibSceneManager>, VisualLintIssueProvider> issueProviderFactory) {
    super(project, parentDisposable, sceneManagerProvider, defaultLayoutOption, actionManagerProvider, interactableProvider, interactionHandlerProvider,
          minScale, maxScale, actionHandlerProvider, delegateDataProvider, selectionModel, zoomControlsPolicy,
          supportedActionsProvider, shouldRenderErrorsPanel, maxZoomToFitLevel, issueProviderFactory);

    myAnalyticsManager = new NlAnalyticsManager(this);
    myAccessoryPanel.setSurface(this);
    myVisualLintIssueProvider = issueProviderFactory.invoke(this);

    getViewport().addChangeListener(e -> {
      DesignSurfaceViewportScroller scroller = getViewportScroller();
      setViewportScroller(null);
      if (scroller != null) {
        scroller.scroll(getViewport());
      }
    });

    myScannerControl = new NlLayoutScanner(this);
    myZoomController = new NlDesignSurfaceZoomController(
      () -> getSceneViewLayoutManager().getFitIntoScale(getSceneViewPanel().getPositionableContent(), getViewport().getExtentSize()),
      getAnalyticsManager(),
      getSelectionModel(),
      this,
      maxZoomToFitLevel
    );
    myZoomController.setOnScaleListener(this);
    myZoomController.setMaxScale(maxScale);
    myZoomController.setMinScale(minScale);
    myZoomController.setScreenScalingFactor(JBUIScale.sysScale(this));
  }

  @NotNull
  @Override
  protected LayoutlibSceneManager createSceneManager(@NotNull NlModel model) {
    LayoutlibSceneManager manager = getSceneManagerProvider().invoke(this, model);
    manager.addRenderListener(getRenderListener());

    return manager;
  }

  @NotNull
  @Override
  public DesignerAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  @Override
  public @Nullable LayoutScannerControl getLayoutScannerControl() {
    return myScannerControl;
  }

  public void setScreenViewProvider(@NotNull ScreenViewProvider screenViewProvider, boolean setAsDefault) {
    // TODO(b/160021437): Make ScreenViewProvider a property of SceneManager instead, the way is currently implemented, changes made to
    //  DesignSurface properties affect previews from all NlModels, that's not always the desired behavior like in this case
    if (setAsDefault && screenViewProvider instanceof NlScreenViewProvider) {
      NlScreenViewProvider.Companion.savePreferredMode((NlScreenViewProvider)screenViewProvider);
    }

    if (screenViewProvider != getScreenViewProvider()) {
      getScreenViewProvider().onViewProviderReplaced();
      setScreenViewProvider(screenViewProvider);

      for (SceneManager manager : getSceneManagers()) {
        manager.updateSceneView();
        manager.requestLayoutAndRenderAsync();
      }
      revalidateScrollArea();
    }
  }

  @NotNull
  @Override
  public AccessoryPanel getAccessoryPanel() {
    return myAccessoryPanel;
  }

  @Override
  public @NotNull CompletableFuture<Void> setModel(@Nullable NlModel model) {
    myAccessoryPanel.setModel(model);
    return super.setModel(model);
  }

  @Override
  public void dispose() {
    myAccessoryPanel.setSurface(null);
    super.dispose();
  }

  @Override
  public void updateErrorDisplay() {
    if (isRenderingSynchronously()) {
      // No errors update while we are in the middle of playing an animation
      return;
    }

    myErrorQueue.updateErrorDisplay(
      myScannerControl, myVisualLintIssueProvider, this, getIssueModel(), this::getSceneManagers);
  }

  @Override
  public void deactivate() {
    myErrorQueue.deactivate(getIssueModel());
    myVisualLintIssueProvider.clear();
    super.deactivate();
  }

  @NotNull
  @Override
  public CompletableFuture<Void> forceUserRequestedRefresh() {
    // When the user initiates the refresh, give some feedback via progress indicator.
    BackgroundableProcessIndicator refreshProgressIndicator = new BackgroundableProcessIndicator(
      getProject(),
      "Refreshing...",
      "",
      "",
      false
    );
    return requestSequentialRender(manager -> {
      manager.forceReinflate();
      return manager.requestUserInitiatedRenderAsync();
    }).whenComplete((r, t) -> refreshProgressIndicator.processFinish());
  }

  @Override
  public @NotNull Map<SceneView, Rectangle> findSceneViewRectangles() {
    return getSceneViewPanel().findSceneViewRectangles();
  }

  @Override
  public void onScaleChange(@NotNull ScaleChange update) {
    super.onScaleChange(update);

    DesignSurfaceViewport port = getViewport();
    Point scrollPosition = getPannable().getScrollPosition();
    Point focusPoint = update.getFocusPoint();

    @SuppressWarnings("deprecation")
    SurfaceLayoutManager layoutManager = getSceneViewLayoutManager().getCurrentLayout().getValue().getLayoutManager();

    // If layout is a vertical list layout
    boolean isGroupedListLayout = layoutManager instanceof GroupedListSurfaceLayoutManager || layoutManager instanceof ListLayoutManager;
    // If layout is grouped grid layout.
    boolean isGroupedGridLayout = layoutManager instanceof GroupedGridSurfaceLayoutManager || layoutManager instanceof GridLayoutManager;

    if (isGroupedListLayout) {
      setViewportScroller(createScrollerForGroupedSurfaces(
        port,
        update,
        scrollPosition,
        new Point(scrollPosition.x, Math.max(0, focusPoint.y))
      ));
    } else if (isGroupedGridLayout && StudioFlags.SCROLLABLE_ZOOM_ON_GRID.get()) {
      setViewportScroller(createScrollerForGroupedSurfaces(
        port,
        update,
        scrollPosition,
        scrollPosition
      ));
    }
    else if (!(layoutManager instanceof GridSurfaceLayoutManager)) {
      Point zoomCenterInView;
      if (focusPoint.x < 0 || focusPoint.y < 0) {
        focusPoint = new Point(
          port.getViewportComponent().getWidth() / 2,
          port.getViewportComponent().getHeight() / 2
        );
      }
      zoomCenterInView = new Point(scrollPosition.x + focusPoint.x, scrollPosition.y + focusPoint.y);

      setViewportScroller(new ZoomCenterScroller(new Dimension(port.getViewSize()), new Point(scrollPosition), zoomCenterInView));
    }
  }

  @Override
  public void scrollToCenter(List<? extends NlComponent> list) {
    SceneView view = getFocusedSceneView();
    if (list.isEmpty() || view == null) {
      return;
    }
    Scene scene = view.getScene();
    @AndroidDpCoordinate Rectangle componentsArea = new Rectangle(0, 0, -1, -1);
    @AndroidDpCoordinate Rectangle componentRect = new Rectangle();
    list.stream().filter(nlComponent -> !nlComponent.isRoot()).forEach(nlComponent -> {
      SceneComponent component = scene.getSceneComponent(nlComponent);
      if (component == null) {
        return;
      }
      component.fillRect(componentRect);

      if (componentsArea.width < 0) {
        componentsArea.setBounds(componentRect);
      }
      else {
        componentsArea.add(componentRect);
      }
    });

    @SwingCoordinate Rectangle areaToCenter = Coordinates.getSwingRectDip(view, componentsArea);
    if (areaToCenter.isEmpty() || getLayeredPane().getVisibleRect().contains(areaToCenter)) {
      // No need to scroll to components if they are all fully visible on the surface.
      return;
    }

    @SwingCoordinate Dimension swingViewportSize = getExtentSize();
    @SwingCoordinate int targetSwingX = (int)areaToCenter.getCenterX();
    @SwingCoordinate int targetSwingY = (int)areaToCenter.getCenterY();
    // Center to position.
    setScrollPosition(targetSwingX - swingViewportSize.width / 2, targetSwingY - swingViewportSize.height / 2);
    @SurfaceScale double fitScale = DesignSurfaceHelper.getFitContentIntoWindowScale(this, areaToCenter.getSize());

    if (myZoomController.getScale() > fitScale) {
      // Scale down to fit selection.
      myZoomController.setScale(fitScale, targetSwingX, targetSwingY);
    }
  }

  public void setRenderSynchronously(boolean enabled) {
    setRenderingSynchronously(enabled);
    // If animation is enabled, scanner must be paused.
    if (myScannerControl != null) {
      if (enabled) {
        myScannerControl.pause();
      }
      else {
        myScannerControl.resume();
      }
    }
  }

  @NotNull
  public VisualLintIssueProvider getVisualLintIssueProvider() {
    return myVisualLintIssueProvider;
  }

}
