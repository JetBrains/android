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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.ZoomController;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.layout.SceneViewAlignment;
import com.android.tools.idea.common.layout.SurfaceLayoutOption;
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager;
import com.android.tools.idea.common.layout.positionable.PositionableContent;
import com.android.tools.idea.common.layout.scroller.DesignSurfaceViewportScroller;
import com.android.tools.idea.common.layout.scroller.ReferencePointScroller;
import com.android.tools.idea.common.layout.scroller.TopLeftCornerScroller;
import com.android.tools.idea.common.layout.scroller.ZoomCenterScroller;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
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
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GroupedListSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.ListLayoutManager;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.stream.Collectors;
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

  // To scroll to correct viewport position when its size is changed.
  @Nullable private DesignSurfaceViewportScroller myViewportScroller = null;

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
      DesignSurfaceViewportScroller scroller = myViewportScroller;
      myViewportScroller = null;
      if (scroller != null) {
        scroller.scroll(getViewport());
      }
    });

    myScannerControl = new NlLayoutScanner(this);
    myZoomController = new NlDesignSurfaceZoomController(
      () -> getSceneViewLayoutManager().getFitIntoScale(getPositionableContent(), getViewport().getExtentSize()),
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
  @NotNull
  public ItemTransferable getSelectionAsTransferable() {
    ImmutableList<DnDTransferComponent> components =
      getSelectionModel().getSelection().stream()
        .filter(component -> component.getTag() != null)
        .map(component ->
               new DnDTransferComponent(component.getTagName(), component.getTag().getText(),
                                        NlComponentHelperKt.getW(component), NlComponentHelperKt.getH(component)))
        .collect(toImmutableList());

    ImmutableSet<NlModel> selectedModels = getSelectionModel().getSelection()
      .stream()
      .map(NlComponent::getModel)
      .collect(toImmutableSet());

    if (selectedModels.size() != 1) {
      Logger
        .getInstance(NlDesignSurface.class)
        .warn("Elements from multiple models were selected.");
    }

    NlModel selectedModel = Iterables.getFirst(selectedModels, null);
    return new ItemTransferable(new DnDTransferItem(selectedModel != null ? selectedModel.getTreeWriter().getId() : 0, components));
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
  public void notifyComponentActivate(@NotNull NlComponent component, int x, int y) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component, () -> { });

    if (handler != null) {
      handler.onActivateInDesignSurface(component, x, y);
    }
    super.notifyComponentActivate(component, x, y);
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
  public void modelRendered() {
    updateErrorDisplay();
    // modelRendered might be called in the Layoutlib Render thread and revalidateScrollArea needs to be called on the UI thread.
    UIUtil.invokeLaterIfNeeded(this::revalidateScrollArea);
  }

  @Override
  public void deactivate() {
    myErrorQueue.deactivate(getIssueModel());
    myVisualLintIssueProvider.clear();
    super.deactivate();
  }

  @Override
  public void activate() {
    super.activate();
    updateErrorDisplay();
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

  @NotNull
  @Override
  public CompletableFuture<Void> forceRefresh() {
    return requestSequentialRender(manager -> {
      manager.forceReinflate();
      return manager.requestRenderAsync();
    });
  }

  @Override
  protected boolean useSmallProgressIcon() {
    if (getFocusedSceneView() == null) {
      return false;
    }

    return Iterables.any(getSceneManagers(), (sceneManager) -> sceneManager.getRenderResult() != null);
  }

  /**
   * Returns all the {@link PositionableContent} in this surface.
   */
  @NotNull
  protected Collection<PositionableContent> getPositionableContent() {
    return getSceneViewPanel().getPositionableContent();
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
      myViewportScroller = createScrollerForGroupedSurfaces(
        port,
        update,
        scrollPosition,
        new Point(scrollPosition.x, Math.max(0, focusPoint.y))
      );
    } else if (isGroupedGridLayout && StudioFlags.SCROLLABLE_ZOOM_ON_GRID.get()) {
      myViewportScroller = createScrollerForGroupedSurfaces(
        port,
        update,
        scrollPosition,
        scrollPosition
      );
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

      myViewportScroller = new ZoomCenterScroller(new Dimension(port.getViewSize()), new Point(scrollPosition), zoomCenterInView);
    }
  }

  @Override
  public DesignSurfaceViewportScroller createScrollerForGroupedSurfaces(
    DesignSurfaceViewport port,
    @NotNull ScaleChange update,
    Point oldScrollPosition,
    Point newScrollPosition
  ) {
    SceneView focusedSceneView = getFocusedSceneView();
    Point focusPoint = update.getFocusPoint();
    if (focusedSceneView != null) {
      focusPoint = new Point(focusedSceneView.getX(), focusedSceneView.getY());
    }
    if (focusPoint.x < 0 || focusPoint.y < 0) {
      // zoom with top-left of the visible area as anchor
      return new TopLeftCornerScroller(
        new Dimension(port.getViewSize()),
        newScrollPosition,
        update.getPreviousScale(),
        update.getNewScale()
      );
    }
    else {
      // zoom with mouse position as anchor, and considering its relative position to the existing scene views
      return new ReferencePointScroller(
        new Dimension(port.getViewSize()),
        new Point(oldScrollPosition),
        focusPoint,
        update.getPreviousScale(),
        update.getNewScale(),
        findSceneViewRectangles(),
        (SceneView sceneView) -> getSceneViewPanel().findMeasuredSceneViewRectangle(sceneView, getExtentSize())
      );
    }
  }

  public final void zoomAndCenter(@NotNull SceneView sceneView,
                                  @NotNull @SwingCoordinate Rectangle rectangle) {
    if (getScrollPane() == null) {
      Logger
        .getInstance(NlDesignSurface.class)
        .warn("The scroll pane is null, cannot zoom and center.");
      return;
    }
    // Calculate the scaleChangeNeeded so that after zooming,
    // the given rectangle with a given offset fits tight in the scroll panel.
    Dimension offset = getScrollToVisibleOffset();
    Dimension availableSize = getExtentSize();
    Dimension curSize = new Dimension(rectangle.width, rectangle.height);
    // Make sure both dimensions fit, and at least one of them is as tight
    // as possible (respecting the offset).
    double scaleChangeNeeded = Math.min(
      (availableSize.getWidth() - 2 * offset.width) / curSize.getWidth(),
      (availableSize.getHeight() - 2 * offset.height) / curSize.getHeight()
    );
    // Adjust the scale change to keep the new scale between the lower and upper bounds.
    double curScale = myZoomController.getScale();
    double boundedNewScale = myZoomController.getBoundedScale(curScale * scaleChangeNeeded);
    scaleChangeNeeded = boundedNewScale / curScale;
    // The rectangle size and its coordinates relative to the sceneView have
    // changed due to the scale change.
    rectangle.setRect(rectangle.x * scaleChangeNeeded, rectangle.y * scaleChangeNeeded,
                      rectangle.width * scaleChangeNeeded, rectangle.height * scaleChangeNeeded);

    if (myZoomController.setScale(boundedNewScale)) {
      myViewportScroller = port -> scrollToCenter(sceneView, rectangle);
    }
    else {
      // If scale hasn't changed, then just scroll to center
      scrollToCenter(sceneView, rectangle);
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

  @NotNull
  @Override
  public List<NlComponent> getSelectableComponents() {
    NlComponent root = getModels()
      .stream()
      .flatMap((model) -> model.getTreeReader().getComponents().stream())
      .findFirst()
      .orElse(null);
    if (root == null) {
      return Collections.emptyList();
    }

    return root.flatten().collect(Collectors.toList());
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

  public void setAnimationScrubbing(boolean value) {
    setInAnimationScrubbing(value);
  }

  @NotNull
  public VisualLintIssueProvider getVisualLintIssueProvider() {
    return myVisualLintIssueProvider;
  }

  @Override
  public final void setSceneViewAlignment(@NotNull SceneViewAlignment sceneViewAlignment) {
    getSceneViewPanel().setSceneViewAlignment(sceneViewAlignment.getAlignmentX());
  }
}
