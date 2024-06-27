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

import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.adtui.ZoomController;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.actions.LayoutPreviewHandler;
import com.android.tools.idea.actions.LayoutPreviewHandlerKt;
import com.android.tools.idea.common.diagnostics.NlDiagnosticKey;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.error.IssueProvider;
import com.android.tools.idea.common.layout.LayoutManagerSwitcher;
import com.android.tools.idea.common.layout.SceneViewAlignment;
import com.android.tools.idea.common.layout.SurfaceLayoutOption;
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
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.Interactable;
import com.android.tools.idea.common.surface.InteractionHandler;
import com.android.tools.idea.common.surface.LayoutScannerControl;
import com.android.tools.idea.common.surface.ScaleChange;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SceneViewPanel;
import com.android.tools.idea.common.surface.SurfaceScale;
import com.android.tools.idea.common.surface.ZoomControlsPolicy;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.layout.scroller.DesignSurfaceViewportScroller;
import com.android.tools.idea.common.layout.scroller.ReferencePointScroller;
import com.android.tools.idea.common.layout.scroller.TopLeftCornerScroller;
import com.android.tools.idea.common.layout.scroller.ZoomCenterScroller;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.interaction.CanvasResizeInteraction;
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.GroupedListSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.layout.option.ListLayoutManager;
import com.android.tools.idea.common.layout.positionable.PositionableContent;
import com.android.tools.idea.common.layout.option.SurfaceLayoutManager;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface<LayoutlibSceneManager>
  implements ViewGroupHandler.AccessoryPanelVisibility, LayoutPreviewHandler, NlDiagnosticKey {

  private boolean myPreviewWithToolsVisibilityAndPosition = true;

  /**
   * See {@link NlSurfaceBuilder#setDelegateDataProvider(DataProvider)}
   */
  @Nullable private final DataProvider myDelegateDataProvider;

  @NotNull
  @Override
  public ZoomController getZoomController() {
    return myZoomController;
  }

  @NotNull private ScreenViewProvider myScreenViewProvider = NlScreenViewProvider.Companion.loadPreferredMode();
  private boolean myIsCanvasResizing = false;
  private final RenderListener myRenderListener = this::modelRendered;
  @NotNull private ImmutableList<? extends IssueProvider> myRenderIssueProviders = ImmutableList.of();
  private final AccessoryPanel myAccessoryPanel = new AccessoryPanel(AccessoryPanel.Type.SOUTH_PANEL, true);
  @NotNull private final NlAnalyticsManager myAnalyticsManager;
  /**
   * Allows customizing the generation of {@link SceneManager}s
   */
  private final BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider;

  // To scroll to correct viewport position when its size is changed.
  @Nullable private DesignSurfaceViewportScroller myViewportScroller = null;

  private boolean myIsRenderingSynchronously = false;
  private boolean myIsAnimationScrubbing = false;
  private float myRotateSurfaceDegree = Float.NaN;

  private final Dimension myScrollableViewMinSize = new Dimension();
  @Nullable private final LayoutScannerControl myScannerControl;

  @NotNull private final Supplier<ImmutableSet<NlSupportedActions>> mySupportedActionsProvider;

  private final ErrorQueue myErrorQueue = new ErrorQueue(this, getProject());

  private final boolean myShouldRenderErrorsPanel;

  private final VisualLintIssueProvider myVisualLintIssueProvider;

  private final NlDesignSurfaceZoomController myZoomController;

  NlDesignSurface(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider,
                          @SuppressWarnings("deprecation") @NotNull SurfaceLayoutOption defaultLayoutOption,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, ActionManager<? extends DesignSurface<LayoutlibSceneManager>>> actionManagerProvider,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, Interactable> interactableProvider,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, InteractionHandler> interactionHandlerProvider,
                          @SurfaceScale double minScale,
                          @SurfaceScale double maxScale,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, DesignSurfaceActionHandler> actionHandlerProvider,
                          @Nullable DataProvider delegateDataProvider,
                          @NotNull SelectionModel selectionModel,
                          ZoomControlsPolicy zoomControlsPolicy,
                          @NotNull Supplier<ImmutableSet<NlSupportedActions>> supportedActionsProvider,
                          boolean shouldRenderErrorsPanel,
                          double maxZoomToFitLevel,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, VisualLintIssueProvider> issueProviderFactory) {
    super(project, parentDisposable, actionManagerProvider, interactableProvider, interactionHandlerProvider,
          (surface) -> new NlDesignSurfacePositionableContentLayoutManager((NlDesignSurface)surface, parentDisposable, defaultLayoutOption),
          actionHandlerProvider,
          selectionModel,
          zoomControlsPolicy
    );
    myAnalyticsManager = new NlAnalyticsManager(this);
    myAccessoryPanel.setSurface(this);
    mySceneManagerProvider = sceneManagerProvider;
    mySupportedActionsProvider = supportedActionsProvider;
    myShouldRenderErrorsPanel = shouldRenderErrorsPanel;
    myVisualLintIssueProvider = issueProviderFactory.apply(this);

    getViewport().addChangeListener(e -> {
      DesignSurfaceViewportScroller scroller = myViewportScroller;
      myViewportScroller = null;
      if (scroller != null) {
        scroller.scroll(getViewport());
      }
    });

    myScannerControl = new NlLayoutScanner(this);
    myDelegateDataProvider = delegateDataProvider;
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
  final NlDesignSurfacePositionableContentLayoutManager getSceneViewLayoutManager() {
    return (NlDesignSurfacePositionableContentLayoutManager)mySceneViewPanel.getLayout();
  }

  @NotNull
  @Override
  protected LayoutlibSceneManager createSceneManager(@NotNull NlModel model) {
    LayoutlibSceneManager manager = mySceneManagerProvider.apply(this, model);
    manager.addRenderListener(myRenderListener);

    return manager;
  }

  @UiThread
  public void onLayoutUpdated(SurfaceLayoutOption layoutOption) {
    if (mySceneViewPanel != null) {
      setSceneViewAlignment(layoutOption.getSceneViewAlignment());
      setScrollPosition(0, 0);
      revalidateScrollArea();
    }
  }

  @NotNull
  @Override
  public NlAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  @Override
  public @Nullable LayoutScannerControl getLayoutScannerControl() {
    return myScannerControl;
  }

  /**
   * Tells this surface to resize mode. While on resizing mode, the views won't be auto positioned.
   * This can be disabled to avoid moving the screens around when the user is resizing the canvas. See {@link CanvasResizeInteraction}
   *
   * @param isResizing true to enable the resize mode
   */
  public void setResizeMode(boolean isResizing) {
    myIsCanvasResizing = isResizing;
    // When in resize mode, allow the scrollable surface autoscroll so it follow the mouse.
    setSurfaceAutoscrolls(isResizing);
  }

  /**
   * When true, the surface will autoscroll when the mouse gets near the edges. See {@link JScrollPane#setAutoscrolls(boolean)}
   */
  private void setSurfaceAutoscrolls(boolean enabled) {
    if (myScrollPane != null) {
      myScrollPane.setAutoscrolls(enabled);
    }
  }

  /**
   * Returns whether this surface is currently in resize mode or not. See {@link #setResizeMode(boolean)}
   */
  public boolean isCanvasResizing() {
    return myIsCanvasResizing;
  }

  @NotNull
  public ScreenViewProvider getScreenViewProvider() {
    return myScreenViewProvider;
  }

  public void setScreenViewProvider(@NotNull ScreenViewProvider screenViewProvider, boolean setAsDefault) {
    // TODO(b/160021437): Make ScreenViewProvider a property of SceneManager instead, the way is currently implemented, changes made to
    //  DesignSurface properties affect previews from all NlModels, that's not always the desired behavior like in this case
    if (setAsDefault && screenViewProvider instanceof NlScreenViewProvider) {
      NlScreenViewProvider.Companion.savePreferredMode((NlScreenViewProvider)screenViewProvider);
    }

    if (screenViewProvider != myScreenViewProvider) {
      myScreenViewProvider.onViewProviderReplaced();
      myScreenViewProvider = screenViewProvider;

      for (SceneManager manager : getSceneManagers()) {
        manager.updateSceneView();
        manager.requestLayoutAndRenderAsync(false);
      }
      revalidateScrollArea();
    }
  }

  /**
   * Update the color-blind mode in the {@link ScreenViewProvider} for this surface
   * and make sure to update all the SceneViews in this surface to reflect the change.
   */
  public void setColorBlindMode(ColorBlindMode mode) {
    myScreenViewProvider.setColorBlindFilter(mode);
    for (SceneManager manager : getSceneManagers()) {
      manager.updateSceneView();
      manager.requestLayoutAndRenderAsync(false);
    }
    revalidateScrollArea();
  }

  @Override
  public boolean shouldRenderErrorsPanel() {
    return myShouldRenderErrorsPanel;
  }

  /**
   * Set the ConstraintsLayer and SceneLayer layers to paint,
   * even if they are set to paint only on mouse hover
   *
   * @param value if true, force painting
   */
  public void forceLayersPaint(boolean value) {
    for (SceneView view : getSceneViews()) {
      view.setForceLayersRepaint(value);
    }
    repaint();
  }

  @NotNull
  @Override
  public AccessoryPanel getAccessoryPanel() {
    return myAccessoryPanel;
  }

  public void showInspectorAccessoryPanel(boolean show) {
    for (DesignSurfaceListener listener : getSurfaceListeners()) {
      listener.showAccessoryPanel(this, show);
    }
  }

  @Override
  public void show(@NotNull AccessoryPanel.Type type, boolean show) {
    showInspectorAccessoryPanel(show);
  }

  @NotNull
  @Override
  public ActionManager<DesignSurface<LayoutlibSceneManager>> getActionManager() {
    //noinspection unchecked
    return super.getActionManager();
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

  /**
   * The offsets to the left and top edges when scrolling to a component by calling {@link #scrollToVisible(SceneView, boolean)}
   */
  @SwingCoordinate
  @Override
  protected Dimension getScrollToVisibleOffset() {
    return new Dimension(2 * DEFAULT_SCREEN_OFFSET_X, 2 * DEFAULT_SCREEN_OFFSET_Y);
  }

  @Override
  public CompletableFuture<Void> setModel(@Nullable NlModel model) {
    myAccessoryPanel.setModel(model);
    return super.setModel(model);
  }

  @Override
  public void dispose() {
    myAccessoryPanel.setSurface(null);
    myRenderIssueProviders = ImmutableList.of();
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

  /**
   * Notifies the design surface that the given screen view (which must be showing in this design surface)
   * has been rendered (possibly with errors)
   */
  public void updateErrorDisplay() {
    if (myIsRenderingSynchronously) {
      // No errors update while we are in the middle of playing an animation
      return;
    }

    myErrorQueue.updateErrorDisplay(
      myScannerControl, myVisualLintIssueProvider, this, getIssueModel(), this::getSceneManagers);
  }

  private void modelRendered() {
    updateErrorDisplay();
    // modelRendered might be called in the Layoutlib Render thread and revalidateScrollArea needs to be called on the UI thread.
    UIUtil.invokeLaterIfNeeded(this::revalidateScrollArea);
  }

  @Override
  public void deactivate() {
    myRenderIssueProviders.forEach(renderIssueProvider -> getIssueModel().removeIssueProvider(renderIssueProvider));
    myRenderIssueProviders = ImmutableList.of();
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
    return mySceneViewPanel.getPositionableContent();
  }

  public Map<SceneView, Rectangle> findSceneViewRectangles() {
    return mySceneViewPanel.findSceneViewRectangles();
  }

  @Override
  public @Nullable LayoutManagerSwitcher getLayoutManagerSwitcher() {
    return (LayoutManagerSwitcher) mySceneViewPanel.getLayout();
  }

  @Override
  public void onScaleChange(@NotNull ScaleChange update) {
    super.onScaleChange(update);

    DesignSurfaceViewport port = getViewport();
    Point scrollPosition = getScrollPosition();
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

  /**
   * Creates a {@link ReferencePointScroller} that scrolls a given focus point of {@link NlDesignSurface}.
   * The focus point could be either the coordinates of a focused scene view or the position of the mouse.
   *
   * @param port The view port were to apply the {@link ReferencePointScroller}
   * @param newScrollPosition The scroll position of the next scrolling action.
   * @param update The {@link ScaleChange} applied to this {@link NlDesignSurface}.
   * @param oldScrollPosition the previous scroll position
   *
   * @return A {@link ReferencePointScroller} to apply to this {@link NlDesignSurface}.
   */
  private DesignSurfaceViewportScroller createScrollerForGroupedSurfaces(
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
        (SceneView sceneView) -> mySceneViewPanel.findMeasuredSceneViewRectangle(sceneView, getExtentSize())
      );
    }
  }

  /**
   * Zoom (in or out) and move the scroll position to ensure that the given rectangle is fully
   * visible and centered.
   * When zooming, the sceneViews may move around, and so the rectangle's coordinates should be
   * relative to the sceneView.
   * The given rectangle should be a subsection of the given sceneView.
   *
   * @param sceneView the {@link SceneView} that contains the given rectangle
   * @param rectangle the rectangle that should be visible, with its coordinates relative to the
   *                  sceneView, and with its currentsize (before zooming).
   */
  public final void zoomAndCenter(@NotNull SceneView sceneView,
                                  @NotNull @SwingCoordinate Rectangle rectangle) {
    if (myScrollPane == null) {
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
    double boundedNewScale = getBoundedScale(curScale * scaleChangeNeeded);
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

  /**
   * When the surface is in "Animation Mode", the error display is not updated. This allows for the surface
   * to render results faster without triggering updates of the issue panel per frame.
   */
  public void setRenderSynchronously(boolean enabled) {
    myIsRenderingSynchronously = enabled;
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

  public boolean isRenderingSynchronously() { return myIsRenderingSynchronously; }

  public void setAnimationScrubbing(boolean value) {
    myIsAnimationScrubbing = value;
  }

  /**
   * Set the rotation degree of the surface to simulate the phone rotation.
   * @param value angle of the rotation.
   */
  public void setRotateSurfaceDegree(float value) {
    myRotateSurfaceDegree = value;
  }

  /**
   * Return the rotation degree of the surface to simulate the phone rotation.
   */
  public float getRotateSurfaceDegree() {
    return myRotateSurfaceDegree;
  }

  /**
   * Return whenever surface is rotating.
   */
  public boolean isRotating() {
    return !Float.isNaN(myRotateSurfaceDegree);
  }
  public boolean isInAnimationScrubbing() { return myIsAnimationScrubbing; }

  /**
   * Sets the min size allowed for the scrollable surface. This is useful in cases where we have an interaction that needs
   * to extend the available space.
   */
  public void setScrollableViewMinSize(@NotNull Dimension dimension) {
    myScrollableViewMinSize.setSize(dimension);
  }

  @Override
  public Object getData(@NotNull String dataId) {
    Object data = myDelegateDataProvider != null ? myDelegateDataProvider.getData(dataId) : null;
    if (data != null) {
      return data;
    }

    if (LayoutPreviewHandlerKt.LAYOUT_PREVIEW_HANDLER_KEY.is(dataId) ) {
      return this;
    }

    return super.getData(dataId);
  }

  @NotNull
  public ImmutableSet<NlSupportedActions> getSupportedActions() {
    return mySupportedActionsProvider.get();
  }

  @Override
  public boolean getPreviewWithToolsVisibilityAndPosition() {
    return myPreviewWithToolsVisibilityAndPosition;
  }

  @Override
  public void setPreviewWithToolsVisibilityAndPosition(boolean isPreviewWithToolsVisibilityAndPosition) {
    if (myPreviewWithToolsVisibilityAndPosition != isPreviewWithToolsVisibilityAndPosition) {
      myPreviewWithToolsVisibilityAndPosition = isPreviewWithToolsVisibilityAndPosition;
      forceRefresh();
    }
  }

  @NotNull
  Dimension getScrollableViewMinSize() {
    return myScrollableViewMinSize;
  }

  @NotNull
  public VisualLintIssueProvider getVisualLintIssueProvider() {
    return myVisualLintIssueProvider;
  }

  /**
   * Sets the {@link SceneViewAlignment} for the {@link SceneView}s. This only applies to {@link SceneView}s when the
   * content size is less than the minimum size allowed. See {@link SceneViewPanel}.
   */
  public final void setSceneViewAlignment(@NotNull SceneViewAlignment sceneViewAlignment) {
    mySceneViewPanel.setSceneViewAlignment(sceneViewAlignment.getAlignmentX());
  }
}
