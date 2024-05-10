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
import com.android.tools.idea.common.layout.SurfaceLayoutOption;
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
import com.android.tools.idea.common.surface.ScaleChange;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.surface.SurfaceInteractable;
import com.android.tools.idea.common.surface.SurfaceScale;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewportScroller;
import com.android.tools.idea.common.surface.layout.ReferencePointScroller;
import com.android.tools.idea.common.surface.layout.TopLeftCornerScroller;
import com.android.tools.idea.common.surface.layout.ZoomCenterScroller;
import com.android.tools.idea.gradle.project.build.GradleBuildState;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
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
import com.android.tools.idea.uibuilder.surface.interaction.CanvasResizeInteraction;
import com.android.tools.idea.uibuilder.surface.layout.GridLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.ListLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent;
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager;
import com.android.tools.idea.uibuilder.visual.VisualizationToolWindowFactory;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintService;
import com.android.tools.rendering.RenderResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
public class NlDesignSurface extends DesignSurface<LayoutlibSceneManager>
  implements ViewGroupHandler.AccessoryPanelVisibility, LayoutPreviewHandler, NlDiagnosticKey {

  private boolean myPreviewWithToolsVisibilityAndPosition = true;

  @SurfaceScale private static final double DEFAULT_MIN_SCALE = 0.025;
  @SurfaceScale private static final double DEFAULT_MAX_SCALE = 10;
  private static final ImmutableSet<NlSupportedActions> DEFAULT_NL_SUPPORTED_ACTIONS =
    ImmutableSet.copyOf(NlSupportedActions.values());

  /**
   * See {@link Builder#setDelegateDataProvider(DataProvider)}
   */
  @Nullable private final DataProvider myDelegateDataProvider;

  @NotNull
  @Override
  public ZoomController getZoomController() {
    return myZoomController;
  }

  public static class Builder {
    private final Project myProject;
    private final Disposable myParentDisposable;
    private final BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider;
    @SuppressWarnings("deprecation") private SurfaceLayoutOption myLayoutOption;
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
    @NotNull private Supplier<ImmutableSet<NlSupportedActions>> mySupportedActionsProvider = () -> DEFAULT_NL_SUPPORTED_ACTIONS;

    private boolean myShouldRenderErrorsPanel = false;

    @Nullable private ScreenViewProvider myScreenViewProvider = null;
    private boolean mySetDefaultScreenViewProvider = false;

    private double myMaxZoomToFitLevel = Double.MAX_VALUE;

    private Function<DesignSurface<LayoutlibSceneManager>, VisualLintIssueProvider> myVisualLintIssueProviderFactory =
      NlDesignSurface::viewVisualLintIssueProviderFactory;

    private Builder(@NotNull Project project,
                    @NotNull Disposable parentDisposable,
                    @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider
    ) {
      myProject = project;
      myParentDisposable = parentDisposable;
      mySceneManagerProvider = sceneManagerProvider;
    }

    /**
     * Allows customizing the {@link SurfaceLayoutOption}.
     */
    @SuppressWarnings("deprecation")
    @NotNull
    public Builder setLayoutOption(@NotNull SurfaceLayoutOption layoutOption) {
      myLayoutOption = layoutOption;
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
     * Restrict the minimum zoom level to the given value. The default value is {@link #DEFAULT_MIN_SCALE}.
     * For example, if this value is 0.15 then the zoom level of {@link DesignSurface} can never be lower than 15%.
     * This restriction also effects to zoom-to-fit, if the measured size of zoom-to-fit is 10%, then the zoom level will be cut to 15%.
     * <br/>
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
     * <br/><br/>
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
     * Set the supported {@link NlSupportedActions} for the built NlDesignSurface.
     * These actions are registered by xml and can be found globally, we need to assign if the built NlDesignSurface supports it or not.
     * By default, the builder assumes there is no supported {@link NlSupportedActions}.
     * <br/><br/>
     * Be aware the {@link com.intellij.openapi.actionSystem.AnAction}s registered by code are not effected.
     * TODO(b/183243031): These mechanism should be integrated into {@link ActionManager}.
     */
    @NotNull
    public Builder setSupportedActionsProvider(@NotNull Supplier<ImmutableSet<NlSupportedActions>> supportedActionsProvider) {
      mySupportedActionsProvider = supportedActionsProvider;
      return this;
    }

    /**
     * See {@link #setSupportedActionsProvider(Supplier)}.
     * This method will create a copy of the given set.
     */
    @NotNull
    public Builder setSupportedActions(@NotNull Set<NlSupportedActions> supportedActions) {
      final ImmutableSet<NlSupportedActions> supportedActionsCopy = ImmutableSet.copyOf(supportedActions);
      setSupportedActionsProvider(() -> supportedActionsCopy);
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
    public Builder setMaxZoomToFitLevel(double maxZoomToFitLevel) {
      myMaxZoomToFitLevel = maxZoomToFitLevel;
      return this;
    }

    @NotNull
    public Builder setVisualLintIssueProvider(Function<DesignSurface<LayoutlibSceneManager>, VisualLintIssueProvider> issueProviderFactory) {
      myVisualLintIssueProviderFactory = issueProviderFactory;
      return this;
    }

    @NotNull
    public NlDesignSurface build() {
      SurfaceLayoutOption layoutOption = myLayoutOption != null
                                         ? myLayoutOption
                                         : SurfaceLayoutOption.Companion.getDEFAULT_OPTION();
      if (myMinScale > myMaxScale) {
        throw new IllegalStateException("The max scale (" + myMaxScale + ") is lower than min scale (" + myMinScale +")");
      }
      NlDesignSurface surface = new NlDesignSurface(
        myProject,
        myParentDisposable,
        mySceneManagerProvider,
        layoutOption,
        myActionManagerProvider,
        myInteractableProvider,
        myInteractionHandlerProvider,
        myMinScale,
        myMaxScale,
        myActionHandlerProvider,
        myDelegateDataProvider,
        mySelectionModel != null ? mySelectionModel : new DefaultSelectionModel(),
        myZoomControlsPolicy,
        mySupportedActionsProvider,
        myShouldRenderErrorsPanel,
        myMaxZoomToFitLevel,
        myVisualLintIssueProviderFactory);

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

  // To scroll to correct viewport position when its size is changed.
  @Nullable private DesignSurfaceViewportScroller myViewportScroller = null;

  private boolean myIsRenderingSynchronously = false;
  private boolean myIsAnimationScrubbing = false;
  private float myRotateSurfaceDegree = Float.NaN;

  private final Dimension myScrollableViewMinSize = new Dimension();
  @Nullable private final LayoutScannerControl myScannerControl;

  @NotNull private final Supplier<ImmutableSet<NlSupportedActions>> mySupportedActionsProvider;

  private final boolean myShouldRenderErrorsPanel;

  private final VisualLintIssueProvider myVisualLintIssueProvider;

  private final NlDesignSurfaceZoomController myZoomController;

  private NlDesignSurface(@NotNull Project project,
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


  /**
   * Default {@link LayoutlibSceneManager} provider with update listener {@link SceneManager.SceneUpdateListener }
   */
  @NotNull
  public static LayoutlibSceneManager defaultSceneManagerProvider(@NotNull NlDesignSurface surface, @NotNull NlModel model, @Nullable SceneManager.SceneUpdateListener listener) {
    LayoutlibSceneManager sceneManager = new LayoutlibSceneManager(model, surface, new LayoutScannerEnabled(), listener);
    RenderSettings settings = RenderSettings.getProjectSettings(model.getProject());
    sceneManager.setShowDecorations(settings.getShowDecorations());
    sceneManager.setUseImagePool(settings.getUseLiveRendering());
    sceneManager.setQuality(settings.getQuality());
    return sceneManager;
  }

  @NotNull
  final NlDesignSurfacePositionableContentLayoutManager getSceneViewLayoutManager() {
    return (NlDesignSurfacePositionableContentLayoutManager)mySceneViewPanel.getLayout();
  }

  /**
   * Default {@link NlActionManager} provider.
   */
  @NotNull
  public static ActionManager<? extends NlDesignSurface> defaultActionManagerProvider(@NotNull DesignSurface<LayoutlibSceneManager> surface) {
    return new NlActionManager((NlDesignSurface)surface);
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

  /**
   * {@link VisualLintIssueProvider} factory that produces a {@link ViewVisualLintIssueProvider} for view-based layouts.
   */
  @NotNull
  public static VisualLintIssueProvider viewVisualLintIssueProviderFactory(@NotNull DesignSurface<LayoutlibSceneManager> surface) {
    return new ViewVisualLintIssueProvider(surface);
  }

  @NotNull
  public static Builder builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return builder(project, parentDisposable, (surface, model) -> NlDesignSurface.defaultSceneManagerProvider(surface, model, null));
  }

  @NotNull
  public static Builder builder(@NotNull Project project, @NotNull Disposable parentDisposable,
                                @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> provider) {
    return new Builder(project, parentDisposable, provider);
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
   * Builds a new {@link NlDesignSurface} with the default settings
   */
  @NotNull
  @TestOnly
  public static NlDesignSurface build(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable, (surface, model) -> NlDesignSurface.defaultSceneManagerProvider(surface, model, null)).build();
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

  @NotNull
  @Override
  public ActionManager<NlDesignSurface> getActionManager() {
    //noinspection unchecked
    return (ActionManager<NlDesignSurface>)super.getActionManager();
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
                  .createErrorModel(NlDesignSurface.this, entry.getValue());
                return new RenderIssueProvider(entry.getKey().getModel(), errorModel);
              })
              .collect(toImmutableList());
          }
          myRenderIssueProviders.forEach(renderIssueProvider -> getIssueModel().removeIssueProvider(renderIssueProvider));
          myRenderIssueProviders = renderIssueProviders;
          renderIssueProviders.forEach(renderIssueProvider -> getIssueModel().addIssueProvider(renderIssueProvider));
        });

        boolean hasLayoutValidationOpen = VisualizationToolWindowFactory.hasVisibleValidationWindow(project);
        boolean hasRunAtfOnMainPreview = hasLayoutValidationOpen;
        if (!hasLayoutValidationOpen) {
          List<NlModel> modelsForBackgroundRun = new ArrayList<>();
          Map<RenderResult, NlModel> renderResultsForAnalysis = new HashMap<>();
          results.forEach((manager, result) -> {
            switch (manager.getVisualLintMode()) {
              case RUN_IN_BACKGROUND:
                modelsForBackgroundRun.add(manager.getModel());
                break;
              case RUN_ON_PREVIEW_ONLY:
                renderResultsForAnalysis.put(result, manager.getModel());
                break;
              case DISABLED:
                break;
            }
          });
          hasRunAtfOnMainPreview = !renderResultsForAnalysis.isEmpty();
          VisualLintService.getInstance(project).runVisualLintAnalysis(
            NlDesignSurface.this,
            myVisualLintIssueProvider,
            modelsForBackgroundRun,
            renderResultsForAnalysis);
        }

        if (myScannerControl != null && !hasRunAtfOnMainPreview) {
          myScannerControl.validateAndUpdateLint(results);
        }
      }

      @Override
      public boolean canEat(@NotNull Update update) {
        return true;
      }
    });
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
      scrollGroupedLayoutToPosition(
        port,
        new Point(scrollPosition.x, Math.max(0, focusPoint.y)),
        update,
        scrollPosition
      );
    }
    else if (isGroupedGridLayout) {
      scrollGroupedLayoutToPosition(
        port,
        scrollPosition,
        update,
        scrollPosition
      );
    } else if (!(layoutManager instanceof GridSurfaceLayoutManager)) {
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

  private void scrollGroupedLayoutToPosition(
    DesignSurfaceViewport port,
    Point x,
    @NotNull ScaleChange update,
    Point scrollPosition
  ) {
    SceneView focusedSceneView = getFocusedSceneView();
    Point focusPoint = update.getFocusPoint();
    if (focusedSceneView != null) {
      focusPoint = new Point(focusedSceneView.getX(), focusedSceneView.getY());
    }
    if (focusPoint.x < 0 || focusPoint.y < 0) {
      // zoom with top-left of the visible area as anchor
      myViewportScroller = new TopLeftCornerScroller(
        new Dimension(port.getViewSize()),
        x,
        update.getPreviousScale(),
        update.getNewScale()
      );
    }
    else {
      // zoom with mouse position as anchor, and considering its relative position to the existing scene views
      myViewportScroller = new ReferencePointScroller(
        new Dimension(port.getViewSize()),
        new Point(scrollPosition),
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
  public void scrollToCenter(@NotNull List<NlComponent> list) {
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
}
