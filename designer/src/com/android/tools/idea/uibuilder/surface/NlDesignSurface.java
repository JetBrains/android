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
import static com.android.tools.idea.uibuilder.graphics.NlConstants.SCREEN_DELTA;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.actions.LayoutPreviewHandler;
import com.android.tools.idea.actions.LayoutPreviewHandlerKt;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.error.IssueProvider;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DefaultSelectionModel;
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
import com.android.tools.idea.common.surface.LayoutScannerEnabled;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SurfaceInteractable;
import com.android.tools.idea.common.surface.SurfaceScale;
import com.android.tools.idea.common.surface.SurfaceScreenScalingFactor;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewportScroller;
import com.android.tools.idea.common.surface.layout.TopBoundCenterScroller;
import com.android.tools.idea.common.surface.layout.ZoomCenterScroller;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager;
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface<LayoutlibSceneManager>
  implements ViewGroupHandler.AccessoryPanelVisibility, LayoutPreviewHandler {

  private boolean myPreviewWithToolsVisibilityAndPosition = true;

  @SurfaceScale private static final double DEFAULT_MIN_SCALE = 0.025;
  @SurfaceScale private static final double DEFAULT_MAX_SCALE = 10;

  /**
   * See {@link Builder#setDelegateDataProvider(DataProvider)}
   */
  @Nullable private final DataProvider myDelegateDataProvider;

  public static class Builder {
    private final Project myProject;
    private final Disposable myParentDisposable;
    private BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider =
      NlDesignSurface::defaultSceneManagerProvider;
    private SurfaceLayoutManager myLayoutManager;
    private NavigationHandler myNavigationHandler;
    @SurfaceScale private double myMinScale = DEFAULT_MIN_SCALE;
    @SurfaceScale private double myMaxScale = DEFAULT_MAX_SCALE;
    /**
     * An optional {@link DataProvider} that allows users of the surface to provide additional information associated
     * with this surface.
     */
    @Nullable private DataProvider myDelegateDataProvider = null;

    /**
     * Factory to create an action manager for the NlDesignSurface
     */
    private Function<DesignSurface<LayoutlibSceneManager>, ActionManager<? extends DesignSurface<LayoutlibSceneManager>>> myActionManagerProvider =
      NlDesignSurface::defaultActionManagerProvider;

    /**
     * Factory to create an {@link Interactable} for the NlDesignSurface.
     * It should only be modified for tests.
     */
    private Function<DesignSurface<LayoutlibSceneManager>, Interactable> myInteractableProvider = SurfaceInteractable::new;

    /**
     * Factory to create an {@link InteractionHandler} for the {@link DesignSurface}.
     */
    private Function<DesignSurface<LayoutlibSceneManager>, InteractionHandler> myInteractionHandlerProvider = NlDesignSurface::defaultInteractionHandlerProvider;
    private Function<DesignSurface<LayoutlibSceneManager>, DesignSurfaceActionHandler> myActionHandlerProvider = NlDesignSurface::defaultActionHandlerProvider;
    @Nullable private SelectionModel mySelectionModel = null;
    private ZoomControlsPolicy myZoomControlsPolicy = ZoomControlsPolicy.AUTO_HIDE;
    @NotNull private Set<NlSupportedActions> mySupportedActions = Collections.emptySet();

    private boolean myShouldRunVisualLintService = false;

    private boolean myShouldRenderErrorsPanel = false;

    @Nullable private ScreenViewProvider myScreenViewProvider = null;
    private boolean mySetDefaultScreenViewProvider = false;

    private double myMaxFitIntoZoomLevel = Double.MAX_VALUE;

    private Builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
      myProject = project;
      myParentDisposable = parentDisposable;
    }

    /**
     * Allows customizing the {@link LayoutlibSceneManager}. Use this method if you need to apply additional settings to it or if you
     * need to completely replace it, for example for tests.
     *
     * @see NlDesignSurface#defaultSceneManagerProvider(NlDesignSurface, NlModel)
     */
    @NotNull
    public Builder setSceneManagerProvider(@NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider) {
      mySceneManagerProvider = sceneManagerProvider;
      return this;
    }

    /**
     * Allows customizing the {@link SurfaceLayoutManager}. Use this method if you need to apply additional settings to it or if you
     * need to completely replace it, for example for tests.
     */
    @NotNull
    public Builder setLayoutManager(@NotNull SurfaceLayoutManager layoutManager) {
      myLayoutManager = layoutManager;
      return this;
    }

    /**
     * Allows customizing the {@link ActionManager}. Use this method if you need to apply additional settings to it or if you
     * need to completely replace it, for example for tests.
     *
     * @see NlDesignSurface#defaultActionManagerProvider(DesignSurface)
     */
    @NotNull
    public Builder setActionManagerProvider(@NotNull Function<DesignSurface<LayoutlibSceneManager>, ActionManager<? extends DesignSurface<LayoutlibSceneManager>>> actionManagerProvider) {
      myActionManagerProvider = actionManagerProvider;
      return this;
    }

    /**
     * Allows to define the {@link Interactable} factory that will later be used to generate
     * the {@link Interactable} over which the {@link InteractionHandler} will be placed.
     */
    @TestOnly
    @NotNull
    public Builder setInteractableProvider(@NotNull Function<DesignSurface<LayoutlibSceneManager>, Interactable> interactableProvider) {
      myInteractableProvider = interactableProvider;
      return this;
    }

    /**
     * Allows customizing the {@link InteractionHandler}. Use this method if you need to apply different interaction behavior to the
     * {@link DesignSurface}.
     *
     * @see NlDesignSurface#defaultInteractionHandlerProvider(DesignSurface)
     */
    @NotNull
    public Builder setInteractionHandlerProvider(@NotNull Function<DesignSurface<LayoutlibSceneManager>, InteractionHandler> interactionHandlerProvider) {
      myInteractionHandlerProvider = interactionHandlerProvider;
      return this;
    }

    /**
     * When the surface is clicked, it can delegate navigation related task to the given handler.
     * @param navigationHandler handles the navigation when the surface is clicked.
     */
    @NotNull
    public Builder setNavigationHandler(NavigationHandler navigationHandler) {
      myNavigationHandler = navigationHandler;
      return this;
    }

    /**
     * Restrict the minimum zoom level to the given value. The default value is {@link #DEFAULT_MIN_SCALE}.
     * For example, if this value is 0.15 then the zoom level of {@link DesignSurface} can never be lower than 15%.
     * This restriction also effects to zoom-to-fit, if the measured size of zoom-to-fit is 10%, then the zoom level will be cut to 15%.
     *
     * This value should always be larger than 0, otherwise the {@link IllegalStateException} will be thrown.
     *
     * @see #setMaxScale(double)
     */
    public Builder setMinScale(@SurfaceScale double scale) {
      if (scale <= 0) {
        throw new IllegalStateException("The min scale (" + scale + ") is not larger than 0");
      }
      myMinScale = scale;
      return this;
    }

    /**
     * Restrict the max zoom level to the given value. The default value is {@link #DEFAULT_MAX_SCALE}.
     * For example, if this value is 1.0 then the zoom level of {@link DesignSurface} can never be larger than 100%.
     * This restriction also effects to zoom-to-fit, if the measured size of zoom-to-fit is 120%, then the zoom level will be cut to 100%.
     *
     * This value should always be larger than 0 and larger than min scale which is set by {@link #setMinScale(double)}. otherwise the
     * {@link IllegalStateException} will be thrown when {@link #build()} is called.
     *
     * @see #setMinScale(double)
     */
    public Builder setMaxScale(@SurfaceScale double scale) {
      myMaxScale = scale;
      return this;
    }

    /**
     * Sets the {@link DesignSurfaceActionHandler} provider for this surface.
     */
    @NotNull
    public Builder setActionHandler(@NotNull Function<DesignSurface<LayoutlibSceneManager>, DesignSurfaceActionHandler> actionHandlerProvider) {
      myActionHandlerProvider = actionHandlerProvider;
      return this;
    }

    /**
     * Sets a delegate {@link DataProvider} that allows users of the surface to provide additional information associated
     * with this surface.
     */
    @NotNull
    public Builder setDelegateDataProvider(@NotNull DataProvider dataProvider) {
      myDelegateDataProvider = dataProvider;
      return this;
    }

    /**
     * Sets a new {@link SelectionModel} for this surface.
     */
    @NotNull
    public Builder setSelectionModel(@NotNull SelectionModel selectionModel) {
      mySelectionModel = selectionModel;
      return this;
    }

    /**
     * The surface will autohide the zoom controls when the mouse is not over it.
     */
    @NotNull
    public Builder setZoomControlsPolicy(@NotNull ZoomControlsPolicy policy) {
      myZoomControlsPolicy = policy;
      return this;
    }

    /**
     * The surface will run visual lint analysis on the background.
     * Default value is false.
     */
    @NotNull
    public Builder setRunVisualLintAnalysis(boolean value) {
      myShouldRunVisualLintService = value;
      return this;
    }

    /**
     * Set the supported {@link NlSupportedActions} for the built NlDesignSurface.
     * These actions are registered by xml and can be found globally, we need to assign if the built NlDesignSurface supports it or not.
     * By default, the builder assumes there is no supported {@link NlSupportedActions}.
     *
     * Be award the {@link com.intellij.openapi.actionSystem.AnAction}s registered by code are not effected.
     * TODO(b/183243031): These mechanism should be integrated into {@link ActionManager}.
     */
    @NotNull
    public Builder setSupportedActions(@NotNull Set<NlSupportedActions> supportedActions) {
      mySupportedActions = supportedActions;
      return this;
    }

    @NotNull
    public Builder setShouldRenderErrorsPanel(Boolean shouldRenderErrorsPanel) {
      myShouldRenderErrorsPanel = shouldRenderErrorsPanel;
      return this;
    }

    @NotNull
    public Builder setScreenViewProvider(@NotNull ScreenViewProvider screenViewProvider, boolean setAsDefault) {
      myScreenViewProvider = screenViewProvider;
      mySetDefaultScreenViewProvider = setAsDefault;
      return this;
    }

    @NotNull
    public Builder setMaxFitIntoZoomLevel(double maxFitIntoZoomLevel) {
      myMaxFitIntoZoomLevel = maxFitIntoZoomLevel;
      return this;
    }

    @NotNull
    public NlDesignSurface build() {
      SurfaceLayoutManager layoutManager = myLayoutManager != null ? myLayoutManager : createDefaultSurfaceLayoutManager();
      if (myMinScale > myMaxScale) {
        throw new IllegalStateException("The max scale (" + myMaxScale + ") is lower than min scale (" + myMinScale +")");
      }
      NlDesignSurface surface = new NlDesignSurface(
        myProject,
        myParentDisposable,
        mySceneManagerProvider,
        layoutManager,
        myActionManagerProvider,
        myInteractableProvider,
        myInteractionHandlerProvider,
        myNavigationHandler,
        myMinScale,
        myMaxScale,
        myActionHandlerProvider,
        myDelegateDataProvider,
        mySelectionModel != null ? mySelectionModel : new DefaultSelectionModel(),
        myZoomControlsPolicy,
        myShouldRunVisualLintService,
        mySupportedActions,
        myShouldRenderErrorsPanel,
        myMaxFitIntoZoomLevel);

      if (myScreenViewProvider != null) {
        surface.setScreenViewProvider(myScreenViewProvider, mySetDefaultScreenViewProvider);
      }

      return surface;
    }
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

  @NotNull private SurfaceLayoutManager myLayoutManager;

  @Nullable private final NavigationHandler myNavigationHandler;

  @SurfaceScale private final double myMinScale;
  @SurfaceScale private final double myMaxScale;

  // To scroll to correct viewport position when its size is changed.
  @Nullable private DesignSurfaceViewportScroller myViewportScroller = null;

  private boolean myIsRenderingSynchronously = false;
  private boolean myIsAnimationScrubbing = false;
  private float myRotateSurfaceDegree = Float.NaN;

  private final Dimension myScrollableViewMinSize = new Dimension();
  @Nullable private LayoutScannerControl myScannerControl;

  @NotNull private final Set<NlSupportedActions> mySupportedActions;

  private final boolean myShouldRunVisualLintService;

  private boolean myShouldRenderErrorsPanel;

  private final VisualLintIssueProvider myVisualLintIssueProvider;

  private NlDesignSurface(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider,
                          @NotNull SurfaceLayoutManager defaultLayoutManager,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, ActionManager<? extends DesignSurface<LayoutlibSceneManager>>> actionManagerProvider,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, Interactable> interactableProvider,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, InteractionHandler> interactionHandlerProvider,
                          @Nullable NavigationHandler navigationHandler,
                          @SurfaceScale double minScale,
                          @SurfaceScale double maxScale,
                          @NotNull Function<DesignSurface<LayoutlibSceneManager>, DesignSurfaceActionHandler> actionHandlerProvider,
                          @Nullable DataProvider delegateDataProvider,
                          @NotNull SelectionModel selectionModel,
                          ZoomControlsPolicy zoomControlsPolicy,
                          boolean shouldRunVisualLintService,
                          @NotNull Set<NlSupportedActions> supportedActions,
                          boolean shouldRenderErrorsPanel,
                          double maxFitIntoZoomLevel) {
    super(project, parentDisposable, actionManagerProvider, interactableProvider, interactionHandlerProvider,
          (surface) -> new NlDesignSurfacePositionableContentLayoutManager((NlDesignSurface)surface, defaultLayoutManager),
          actionHandlerProvider,
          selectionModel,
          zoomControlsPolicy,
          maxFitIntoZoomLevel);
    myAnalyticsManager = new NlAnalyticsManager(this);
    myAccessoryPanel.setSurface(this);
    myLayoutManager = defaultLayoutManager;
    mySceneManagerProvider = sceneManagerProvider;
    myNavigationHandler = navigationHandler;
    mySupportedActions = supportedActions;
    myShouldRunVisualLintService = shouldRunVisualLintService;
    myShouldRenderErrorsPanel = shouldRenderErrorsPanel;
    myVisualLintIssueProvider = new VisualLintIssueProvider(this);

    if (myNavigationHandler != null) {
      Disposer.register(this, myNavigationHandler);
    }

    myMinScale = minScale;
    myMaxScale = maxScale;

    getViewport().addChangeListener(e -> {
      DesignSurfaceViewportScroller scroller = myViewportScroller;
      myViewportScroller = null;
      if (scroller != null) {
        scroller.scroll(getViewport());
      }
    });

    myScannerControl = new NlLayoutScanner(this);
    myDelegateDataProvider = delegateDataProvider;
  }

  /**
   * Default {@link LayoutlibSceneManager} provider.
   */
  @NotNull
  public static LayoutlibSceneManager defaultSceneManagerProvider(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    LayoutlibSceneManager sceneManager = new LayoutlibSceneManager(model, surface, new LayoutScannerEnabled());
    RenderSettings settings = RenderSettings.getProjectSettings(model.getProject());
    sceneManager.setShowDecorations(settings.getShowDecorations());
    sceneManager.setUseImagePool(settings.getUseLiveRendering());
    sceneManager.setQuality(settings.getQuality());
    return sceneManager;
  }

  @NotNull
  public static SurfaceLayoutManager createDefaultSurfaceLayoutManager() {
    return new SingleDirectionLayoutManager(DEFAULT_SCREEN_OFFSET_X, DEFAULT_SCREEN_OFFSET_Y, SCREEN_DELTA, SCREEN_DELTA,
                                            SingleDirectionLayoutManager.Alignment.CENTER);
  }

  /**
   * Default {@link NlActionManager} provider.
   */
  @NotNull
  public static ActionManager<? extends NlDesignSurface> defaultActionManagerProvider(@NotNull DesignSurface<LayoutlibSceneManager> surface) {
    return new NlActionManager((NlDesignSurface) surface);
  }

  /**
   * Default {@link NlInteractionHandler} provider.
   */
  @NotNull
  public static NlInteractionHandler defaultInteractionHandlerProvider(@NotNull DesignSurface<LayoutlibSceneManager> surface) {
    return new NlInteractionHandler(surface);
  }

  /**
   * Default {@link NlDesignSurfaceActionHandler} provider.
   */
  @NotNull
  public static NlDesignSurfaceActionHandler defaultActionHandlerProvider(@NotNull DesignSurface<LayoutlibSceneManager> surface) {
    return new NlDesignSurfaceActionHandler(surface);
  }

  @NotNull
  public static Builder builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable);
  }

  @NotNull
  @Override
  protected LayoutlibSceneManager createSceneManager(@NotNull NlModel model) {
    LayoutlibSceneManager manager = mySceneManagerProvider.apply(this, model);
    manager.addRenderListener(myRenderListener);

    return manager;
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
   * Returns whether this surface is currently in resize mode or not. See {@link #setResizeMode(boolean)}
   */
  public boolean isCanvasResizing() {
    return myIsCanvasResizing;
  }

  @Override
  public boolean isLayoutDisabled() {
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

  @Nullable
  public NavigationHandler getNavigationHandler() {
    return myNavigationHandler;
  }

  @Override
  public boolean shouldRenderErrorsPanel() {
    return myShouldRenderErrorsPanel;
  }

  /**
   * Builds a new {@link NlDesignSurface} with the default settings
   */
  @NotNull
  public static NlDesignSurface build(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable).build();
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
    for (DesignSurfaceListener listener : ImmutableList.copyOf(myListeners)) {
      listener.showAccessoryPanel(this, show);
    }
  }

  @Override
  public void show(@NotNull AccessoryPanel.Type type, boolean show) {
    showInspectorAccessoryPanel(show);
  }

  @Override
  @SurfaceScreenScalingFactor
  public double getScreenScalingFactor() {
    return JBUIScale.sysScale(this);
  }

  @NotNull
  @Override
  public ActionManager<NlDesignSurface> getActionManager() {
    return (ActionManager<NlDesignSurface>)super.getActionManager();
  }

  @Override
  @NotNull
  public ItemTransferable getSelectionAsTransferable() {
    NlModel model = getModel();

    ImmutableList<DnDTransferComponent> components =
      getSelectionModel().getSelection().stream()
        .map(component -> new DnDTransferComponent(component.getTagName(), component.getTagDeprecated().getText(),
                                                   NlComponentHelperKt.getW(component), NlComponentHelperKt.getH(component)))
        .collect(toImmutableList());
    return new ItemTransferable(new DnDTransferItem(model != null ? model.getId() : 0, components));
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
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);

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

    assert ApplicationManager.getApplication().isDispatchThread() ||
           !ApplicationManager.getApplication().isReadAccessAllowed() : "Do not hold read lock when calling updateErrorDisplay!";

    getErrorQueue().cancelAllUpdates();
    getErrorQueue().queue(new Update("errors") {
      @Override
      public void run() {
        // Whenever error queue is active, make sure to resume any paused scanner control.
        if (myScannerControl != null) {
          myScannerControl.resume();
        }
        // Look up *current* result; a newer one could be available
        Map<LayoutlibSceneManager, RenderResult> results = getSceneManagers().stream()
          .filter(sceneManager -> sceneManager.getRenderResult() != null)
          .collect(Collectors.toMap(Function.identity(), LayoutlibSceneManager::getRenderResult));
        if (results.isEmpty()) {
          return;
        }
        if (myScannerControl != null) {
          for (Map.Entry<LayoutlibSceneManager, RenderResult> entry : results.entrySet()) {
            LayoutlibSceneManager manager = entry.getKey();
            if (manager.getLayoutScannerConfig().isIntegrateWithDefaultIssuePanel()) {
              myScannerControl.validateAndUpdateLint(entry.getValue(), manager.getModel());
            }
          }
        }

        Project project = getProject();
        if (project.isDisposed()) {
          return;
        }

        // createErrorModel needs to run in Smart mode to resolve the classes correctly
        DumbService.getInstance(project).runReadActionInSmartMode(() -> {
          ImmutableList<RenderIssueProvider> renderIssueProviders = null;
          if (GradleBuildState.getInstance(project).isBuildInProgress()) {
            for (Map.Entry<LayoutlibSceneManager, RenderResult> entry : results.entrySet()) {
              if (entry.getValue().getLogger().hasErrors()) {
                // We are still building, display the message to the user.
                renderIssueProviders = ImmutableList.of(new RenderIssueProvider(
                  entry.getKey().getModel(), RenderErrorModel.STILL_BUILDING_ERROR_MODEL));
                break;
              }
            }
          }

          if (renderIssueProviders == null) {
            renderIssueProviders = results.entrySet().stream()
              .map(entry -> {
                RenderErrorModel errorModel = RenderErrorModelFactory
                  .createErrorModel(NlDesignSurface.this, entry.getValue(), null);
                return new RenderIssueProvider(entry.getKey().getModel(), errorModel);
              })
              .collect(toImmutableList());
          }
          myRenderIssueProviders.forEach(renderIssueProvider -> getIssueModel().removeIssueProvider(renderIssueProvider));
          myRenderIssueProviders = renderIssueProviders;
          renderIssueProviders.forEach(renderIssueProvider -> getIssueModel().addIssueProvider(renderIssueProvider));
        });

        if (myShouldRunVisualLintService && !VisualizationToolWindowFactory.hasVisibleValidationWindow(project)) {
          VisualLintService.getInstance(project).runVisualLintAnalysis(
            NlDesignSurface.this,
            myVisualLintIssueProvider,
            getModels());
        }
      }

      @Override
      public boolean canEat(Update update) {
        return true;
      }
    });
  }

  private void modelRendered() {
    updateErrorDisplay();
    // modelRendered might be called in the Layoutlib Render thread and revalidateScrollArea needs to be called on the UI thread.
    UIUtil.invokeLaterIfNeeded(() -> revalidateScrollArea());
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
      PerformInBackgroundOption.ALWAYS_BACKGROUND,
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

    LayoutlibSceneManager manager = getSceneManager();
    assert manager != null;

    return manager.getRenderResult() != null;
  }

  @Override
  @SurfaceScale
  protected double getMinScale() {
    return myMinScale;
  }

  @Override
  @SurfaceScale
  protected double getMaxScale() {
    return myMaxScale;
  }

  @Override
  public boolean canZoomToFit() {
    @SurfaceScale double currentScale = getScale();
    @SurfaceScale double zoomToFitScale = getFitScale();
    return (currentScale > zoomToFitScale && canZoomOut()) || (currentScale < zoomToFitScale && canZoomIn());
  }

  @Override
  public boolean canZoomToActual() {
    @SurfaceScale double currentScale = getScale();
    @SurfaceScale double scaleOfActual = 1d / getScreenScalingFactor();
    return (currentScale > scaleOfActual && canZoomOut()) || (currentScale < scaleOfActual && canZoomIn());
  }

  @Override
  public double getFitScale() {
    Dimension extent = getExtentSize();
    double scale = ((NlDesignSurfacePositionableContentLayoutManager)getSceneViewLayoutManager())
      .getLayoutManager()
      .getFitIntoScale(getPositionableContent(), extent.width, extent.height);

    return Math.min(scale, myMaxFitIntoScale);
  }

  @Override
  public boolean setScale(double scale, int x, int y) {
    boolean changed = super.setScale(scale, x, y);
    if (changed) {
      DesignSurfaceViewport port = getViewport();

      if (myLayoutManager instanceof GroupedListSurfaceLayoutManager) {
        Point scrollPosition = getScrollPosition();
        myViewportScroller = new TopBoundCenterScroller(new Dimension(port.getViewSize()), new Point(scrollPosition));
      }
      else if (!(myLayoutManager instanceof GridSurfaceLayoutManager)) {
        Point zoomCenterInView;
        if (x < 0 || y < 0) {
          x = port.getViewportComponent().getWidth() / 2;
          y = port.getViewportComponent().getHeight() / 2;
        }
        Point scrollPosition = getScrollPosition();
        zoomCenterInView = new Point(scrollPosition.x + x, scrollPosition.y + y);

        myViewportScroller = new ZoomCenterScroller(new Dimension(port.getViewSize()), new Point(scrollPosition), zoomCenterInView);
      }
    }
    return changed;
  }

  /**
   * Zoom (in or out) and move the scroll position to ensure that the given rectangle is fully
   * visible and centered.
   * When zooming, the sceneViews may move around, and so the rectangle's coordinates should be
   * relative to the sceneView.
   * The given rectangle should be a subsection of the given sceneView.
   * @param sceneView the {@link SceneView} that contains the given rectangle
   * @param rectangle the rectangle that should be visible, with its coordinates relative to the
   *  sceneView, and with its currentsize (before zooming).
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
      (availableSize.getWidth() - 2*offset.width) / curSize.getWidth(),
      (availableSize.getHeight() - 2*offset.height) / curSize.getHeight()
    );
    // Adjust the scale change to keep the new scale between the lower and upper bounds.
    double curScale = getScale();
    double boundedNewScale = getBoundedScale(curScale * scaleChangeNeeded);
    scaleChangeNeeded = boundedNewScale/curScale;
    // The rectangle size and its coordinates relative to the sceneView have
    // changed due to the scale change.
    rectangle.setRect(rectangle.x * scaleChangeNeeded, rectangle.y * scaleChangeNeeded,
                      rectangle.width * scaleChangeNeeded, rectangle.height * scaleChangeNeeded);

    if (setScale(boundedNewScale)) {
      myViewportScroller = port -> scrollToCenter(sceneView, rectangle);
    }
    else {
      // If scale hasn't changed, then just scroll to center
      scrollToCenter(sceneView, rectangle);
    }
  }

  @Override
  public void scrollToCenter(@NotNull List<NlComponent> list) {
    Scene scene = getScene();
    SceneView view = getFocusedSceneView();
    if (list.isEmpty() || scene == null || view == null) {
      return;
    }
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

    if (getScale() > fitScale) {
      // Scale down to fit selection.
      setScale(fitScale, targetSwingX, targetSwingY);
    }
  }

  @Override
  protected void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    super.notifySelectionListeners(newSelection);
    scrollToCenter(newSelection);
  }

  @NotNull
  @Override
  public List<NlComponent> getSelectableComponents() {
    NlModel model = getModel();
    if (model == null) {
      return Collections.emptyList();
    }

    List<NlComponent> roots = model.getComponents();
    if (roots.isEmpty()) {
      return Collections.emptyList();
    }

    NlComponent root = roots.get(0);
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
  public void setRotateSufaceDegree(float value) {
    myRotateSurfaceDegree = value;
  }

  /**
   * Return the rotation degree of the surface to simulate the phone rotation.
   */
  public float getRotateSurfaceDegree() {
    return myRotateSurfaceDegree;
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
  public Set<NlSupportedActions> getSupportedActions() {
    return mySupportedActions;
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
}
