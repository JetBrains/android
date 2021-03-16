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

import static com.android.tools.idea.actions.DesignerDataKeys.DESIGN_SURFACE;
import static com.android.tools.idea.actions.DesignerDataKeys.LAYOUT_VALIDATOR_KEY;
import static com.android.tools.idea.flags.StudioFlags.NELE_LAYOUT_VALIDATOR_IN_EDITOR;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.SCREEN_DELTA;

import com.android.tools.adtui.actions.ZoomType;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.actions.LayoutPreviewHandler;
import com.android.tools.idea.actions.LayoutPreviewHandlerKt;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DnDTransferComponent;
import com.android.tools.idea.common.model.DnDTransferItem;
import com.android.tools.idea.common.model.ItemTransferable;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.common.surface.InteractionHandler;
import com.android.tools.idea.common.surface.PositionableContentLayoutManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.gradle.project.BuildSettings;
import com.android.tools.idea.gradle.util.BuildMode;
import com.android.tools.idea.rendering.RenderErrorModelFactory;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.rendering.errors.ui.RenderErrorModel;
import com.android.tools.idea.uibuilder.adaptiveicon.ShapeMenuAction;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.error.RenderIssueProvider;
import com.android.tools.idea.uibuilder.mockup.editor.MockupEditor;
import com.android.tools.idea.uibuilder.model.NlComponentHelper;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent;
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager;
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager;
import com.android.tools.idea.validator.ValidatorResult;
import com.android.utils.ImmutableCollectors;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.Update;
import java.awt.*;
import java.util.ArrayList;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The {@link DesignSurface} for the layout editor, which contains the full background, rulers, one
 * or more device renderings, etc
 */
@SuppressWarnings("ClassWithOnlyPrivateConstructors")
public class NlDesignSurface extends DesignSurface implements ViewGroupHandler.AccessoryPanelVisibility, LayoutPreviewHandler {

  private boolean myPreviewWithToolsVisibilityAndPosition = true;

  private static final double DEFAULT_MIN_SCALE = 0.1;
  private static final double DEFAULT_MAX_SCALE = 10;

  /**
   * See {@link Builder#setDelegateDataProvider(DataProvider)}
   */
  @Nullable private final DataProvider myDelegateDataProvider;

  private static class NlDesignSurfacePositionableContentLayoutManager extends PositionableContentLayoutManager {
    private final NlDesignSurface myDesignSurface;
    private final SurfaceLayoutManager myLayoutManager;

    NlDesignSurfacePositionableContentLayoutManager(@NotNull NlDesignSurface surface, @NotNull SurfaceLayoutManager surfaceLayoutManager) {
      myDesignSurface = surface;
      myLayoutManager = surfaceLayoutManager;
    }

    @Override
    public void layoutContent(@NotNull Collection<? extends PositionableContent> content) {
      Dimension extentSize = myDesignSurface.getExtentSize();
      int availableWidth = extentSize.width;
      int availableHeight = extentSize.height;
      myLayoutManager.layout(content, availableWidth, availableHeight, myDesignSurface.isCanvasResizing());
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
      Dimension extentSize = myDesignSurface.getExtentSize();
      int availableWidth = extentSize.width;
      int availableHeight = extentSize.height;
      Dimension dimension = myLayoutManager.getRequiredSize(myDesignSurface.getPositionableContent(), availableWidth, availableHeight, null);

      if (dimension.width >= 0 && dimension.height >= 0) {
        dimension.setSize(dimension.width + 2 * DEFAULT_SCREEN_OFFSET_X, dimension.height + 2 * DEFAULT_SCREEN_OFFSET_Y);
      }
      else {
        // The layout manager returned an invalid layout
        dimension.setSize(0, 0);
      }

      dimension.setSize(
        Math.max(myDesignSurface.myScrollableViewMinSize.width, dimension.width),
        Math.max(myDesignSurface.myScrollableViewMinSize.height, dimension.height)
      );

      return dimension;
    }
  }

  public static class Builder {
    private final Project myProject;
    private final Disposable myParentDisposable;
    private boolean myIsPreview = false;
    private BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider =
      NlDesignSurface::defaultSceneManagerProvider;
    private boolean myShowModelName = false;
    private boolean myIsEditable = true;
    private SurfaceLayoutManager myLayoutManager;
    private NavigationHandler myNavigationHandler;
    private double myMinScale = DEFAULT_MIN_SCALE;
    private double myMaxScale = DEFAULT_MAX_SCALE;
    @NotNull private ZoomType myOnChangeZoom = ZoomType.FIT_INTO;
    /**
     * An optional {@link DataProvider} that allows users of the surface to provide additional information associated
     * with this surface.
     */
    @Nullable private DataProvider myDelegateDataProvider = null;

    /**
     * Factory to create an action manager for the NlDesignSurface
     */
    private Function<DesignSurface, ActionManager<? extends DesignSurface>> myActionManagerProvider =
      NlDesignSurface::defaultActionManagerProvider;

    /**
     * Factory to create an {@link InteractionHandler} for the {@link DesignSurface}.
     */
    private Function<DesignSurface, InteractionHandler> myInteractionHandlerProvider = NlDesignSurface::defaultInteractionHandlerProvider;
    private Function<DesignSurface, DesignSurfaceActionHandler> myActionHandlerProvider = NlDesignSurface::defaultActionHandlerProvider;

    private Builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
      myProject = project;
      myParentDisposable = parentDisposable;
    }

    /**
     * Marks the {@link NlDesignSurface} as being in preview mode.
     */
    @NotNull
    public Builder setIsPreview(boolean isPreview) {
      myIsPreview = isPreview;
      return this;
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
     * Enables {@link NlDesignSurface} displaying of the model names when present.
     */
    @NotNull
    public Builder showModelNames() {
      myShowModelName = true;
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
    public Builder setActionManagerProvider(@NotNull Function<DesignSurface, ActionManager<? extends DesignSurface>> actionManagerProvider) {
      myActionManagerProvider = actionManagerProvider;
      return this;
    }

    /**
     * Specify if {@link NlDesignSurface} can edit editable content. For example, a xml layout file is a editable content. But
     * an image drawable file is not editable, so {@link NlDesignSurface} cannot edit the image drawable file even we set
     * editable for {@link NlDesignSurface}.
     * <p>
     * The default value is true (editable)
     */
    @NotNull
    public Builder setEditable(boolean editable) {
      myIsEditable = editable;
      return this;
    }

    /**
     * Allows customizing the {@link InteractionHandler}. Use this method if you need to apply different interaction behavior to the
     * {@link DesignSurface}.
     *
     * @see NlDesignSurface#defaultInteractionHandlerProvider(DesignSurface)
     */
    @NotNull
    public Builder setInteractionHandlerProvider(@NotNull Function<DesignSurface, InteractionHandler> interactionHandlerProvider) {
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
    public Builder setMinScale(double scale) {
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
    public Builder setMaxScale(double scale) {
      myMaxScale = scale;
      return this;
    }

    /**
     * Set zoom type to apply to surface on configuration changes.
     * @param onChangeZoom {@link ZoomType} to be applied on configuration change
     * @return this {@link Builder}
     */
    public Builder setOnConfigurationChangedZoom(@NotNull ZoomType onChangeZoom) {
      myOnChangeZoom = onChangeZoom;
      return this;
    }

    /**
     * Sets the {@link DesignSurfaceActionHandler} provider for this surface.
     */
    @NotNull
    public Builder setActionHandler(@NotNull Function<DesignSurface, DesignSurfaceActionHandler> actionHandlerProvider) {
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

    @NotNull
    public NlDesignSurface build() {
      SurfaceLayoutManager layoutManager = myLayoutManager != null ? myLayoutManager : createDefaultSurfaceLayoutManager();
      if (myMinScale > myMaxScale) {
        throw new IllegalStateException("The max scale (" + myMaxScale + ") is lower than min scale (" + myMinScale +")");
      }
      return new NlDesignSurface(myProject,
                                 myParentDisposable,
                                 myIsPreview,
                                 myIsEditable,
                                 myShowModelName,
                                 mySceneManagerProvider,
                                 layoutManager,
                                 myActionManagerProvider,
                                 myInteractionHandlerProvider,
                                 myNavigationHandler,
                                 myMinScale,
                                 myMaxScale,
                                 myOnChangeZoom,
                                 myActionHandlerProvider,
                                 myDelegateDataProvider);
    }
  }

  /**
   * Optional navigation helper for when the surface is clicked.
   */
  public interface NavigationHandler extends Disposable {
    /**
     * Triggered when preview in the design surface is clicked, returns true if the navigation was handled by this handler.
     * This method receives the x and y coordinates of the click. You will usually only need the coordinates if your navigation can
     * be different within a same {@link SceneComponent}.
     *
     * @param sceneView {@link SceneView} for which the navigation request is being issued
     * @param sceneComponent {@link SceneComponent} for which the navigation request is being issued
     * @param x X coordinate within the {@link SceneView} where the click action was initiated
     * @param y y coordinate within the {@link SceneView} where the click action was initiated
     * @param requestFocus true if the navigation should focus the editor
     */
    boolean handleNavigate(@NotNull SceneView sceneView,
                           @NotNull SceneComponent sceneComponent,
                           @SwingCoordinate int x,
                           @SwingCoordinate int y,
                           boolean requestFocus);
  }

  @NotNull private SceneMode mySceneMode = SceneMode.Companion.loadPreferredMode();
  private boolean myIsCanvasResizing = false;
  private boolean myShowModelNames = false;
  private boolean myMockupVisible;
  private MockupEditor myMockupEditor;
  private final boolean myIsInPreview;
  private ShapeMenuAction.AdaptiveIconShape myAdaptiveIconShape = ShapeMenuAction.AdaptiveIconShape.getDefaultShape();
  private final RenderListener myRenderListener = this::modelRendered;
  private RenderIssueProvider myRenderIssueProvider;
  private AccessoryPanel myAccessoryPanel = new AccessoryPanel(AccessoryPanel.Type.SOUTH_PANEL, true);
  @NotNull private final NlAnalyticsManager myAnalyticsManager;
  /**
   * Allows customizing the generation of {@link SceneManager}s
   */
  private final BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> mySceneManagerProvider;

  @NotNull private final SurfaceLayoutManager myLayoutManager;

  @Nullable private final NavigationHandler myNavigationHandler;

  private final double myMinScale;
  private final double myMaxScale;

  private boolean myIsRenderingSynchronously = false;
  private boolean myIsAnimationScrubbing = false;

  private final Dimension myScrollableViewMinSize = new Dimension();
  @Nullable private NlLayoutValidator myValidator;
  @Nullable private LayoutValidatorControl myValidatorControl;

  private NlDesignSurface(@NotNull Project project,
                          @NotNull Disposable parentDisposable,
                          boolean isInPreview,
                          boolean isEditable,
                          boolean showModelNames,
                          @NotNull BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager> sceneManagerProvider,
                          @NotNull SurfaceLayoutManager layoutManager,
                          @NotNull Function<DesignSurface, ActionManager<? extends DesignSurface>> actionManagerProvider,
                          @NotNull Function<DesignSurface, InteractionHandler> interactionHandlerProvider,
                          @Nullable NavigationHandler navigationHandler,
                          double minScale,
                          double maxScale,
                          @NotNull ZoomType onChangeZoom,
                          @NotNull Function<DesignSurface, DesignSurfaceActionHandler> actionHandlerProvider,
                          @Nullable DataProvider delegateDataProvider) {
    super(project, parentDisposable, actionManagerProvider, interactionHandlerProvider, isEditable, onChangeZoom,
          (surface) -> new NlDesignSurfacePositionableContentLayoutManager((NlDesignSurface)surface, layoutManager),
          actionHandlerProvider);
    myAnalyticsManager = new NlAnalyticsManager(this);
    myAccessoryPanel.setSurface(this);
    myIsInPreview = isInPreview;
    myShowModelNames = showModelNames;
    myLayoutManager = layoutManager;
    mySceneManagerProvider = sceneManagerProvider;
    myNavigationHandler = navigationHandler;

    if (myNavigationHandler != null) {
      Disposer.register(this, myNavigationHandler);
    }

    myMinScale = minScale;
    myMaxScale = maxScale;

    if (NELE_LAYOUT_VALIDATOR_IN_EDITOR.get()) {
      myValidator = new NlLayoutValidator(myIssueModel, this);
      myValidatorControl = new NlLayoutValidatorControl(this, myValidator);
    }

    myDelegateDataProvider = delegateDataProvider;
  }

  /**
   * Default {@link LayoutlibSceneManager} provider.
   */
  @NotNull
  public static LayoutlibSceneManager defaultSceneManagerProvider(@NotNull NlDesignSurface surface, @NotNull NlModel model) {
    LayoutlibSceneManager sceneManager = new LayoutlibSceneManager(model, surface);
    RenderSettings settings = RenderSettings.getProjectSettings(model.getProject());
    sceneManager.setShowDecorations(settings.getShowDecorations());
    sceneManager.setUseImagePool(settings.getUseLiveRendering());
    sceneManager.setQuality(settings.getQuality());
    return sceneManager;
  }

  @NotNull
  public static SurfaceLayoutManager createDefaultSurfaceLayoutManager() {
     return new SingleDirectionLayoutManager(DEFAULT_SCREEN_OFFSET_X, DEFAULT_SCREEN_OFFSET_Y, SCREEN_DELTA, SCREEN_DELTA);
  }

  /**
   * Default {@link NlActionManager} provider.
   */
  @NotNull
  public static ActionManager<? extends NlDesignSurface> defaultActionManagerProvider(@NotNull DesignSurface surface) {
    return new NlActionManager((NlDesignSurface) surface);
  }

  /**
   * Default {@link NlInteractionHandler} provider.
   */
  @NotNull
  public static NlInteractionHandler defaultInteractionHandlerProvider(@NotNull DesignSurface surface) {
    return new NlInteractionHandler(surface);
  }

  /**
   * Default {@link NlDesignSurfaceActionHandler} provider.
   */
  @NotNull
  public static NlDesignSurfaceActionHandler defaultActionHandlerProvider(@NotNull DesignSurface surface) {
    return new NlDesignSurfaceActionHandler(surface);
  }

  @NotNull
  public static Builder builder(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable);
  }

  @NotNull
  @Override
  protected SceneManager createSceneManager(@NotNull NlModel model) {
    LayoutlibSceneManager manager = mySceneManagerProvider.apply(this, model);
    manager.addRenderListener(myRenderListener);

    return manager;
  }

  @NotNull
  @Override
  public NlAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  public boolean isPreviewSurface() {
    return myIsInPreview;
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
    myScrollPane.setAutoscrolls(isResizing);
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

  public boolean isShowModelNames() {
    return myShowModelNames;
  }

  @NotNull
  public SceneMode getSceneMode() {
    return mySceneMode;
  }

  @Nullable
  public NavigationHandler getNavigationHandler() {
    return myNavigationHandler;
  }

  public void setScreenMode(@NotNull SceneMode sceneMode, boolean setAsDefault) {
    if (setAsDefault) {
      SceneMode.Companion.savePreferredMode(sceneMode);
    }

    if (sceneMode != mySceneMode) {
      mySceneMode = sceneMode;

      LayoutlibSceneManager manager = getSceneManager();
      if (manager != null) {
        manager.updateSceneView();
        manager.requestLayoutAndRender(false);
      }
      if (!contentResizeSkipped()) {
        zoomToFit();
        revalidateScrollArea();
      }
    }
  }

  /**
   * Builds a new {@link NlDesignSurface} with the default settings
   */
  @NotNull
  public static NlDesignSurface build(@NotNull Project project, @NotNull Disposable parentDisposable) {
    return new Builder(project, parentDisposable).build();
  }

  @Nullable
  @Override
  public LayoutlibSceneManager getSceneManager() {
    return (LayoutlibSceneManager)super.getSceneManager();
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

  @Override
  @Nullable
  public SceneView getSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    SceneView view = getHoverSceneView(x, y);
    if (view == null) {
      // TODO: For keeping the behaviour as before in multi-model case, we return primary SceneView when there is no hovered SceneView.
      SceneManager manager = getSceneManager();
      if (manager != null) {
        view = manager.getSceneView();
      }
    }
    return view;
  }

  /**
   * Return the ScreenView under the given position
   *
   * @return the ScreenView, or null if we are not above one.
   */
  @Nullable
  @Override
  public SceneView getHoverSceneView(@SwingCoordinate int x, @SwingCoordinate int y) {
    Collection<SceneView> sceneViews = getSceneViews();
    for (SceneView view : sceneViews) {
      if (view.getX() <= x && x <= (view.getX() + view.getScaledContentSize().width) && view.getY() <= y && y <= (view.getY() + view.getScaledContentSize().height)) {
        return view;
      }
    }
    return null;
  }

  @Override
  @NotNull
  protected ImmutableCollection<SceneView> getSceneViews() {
    ImmutableList.Builder<SceneView> builder = new ImmutableList.Builder<>();

    // Add all primary SceneViews
    builder.addAll(super.getSceneViews());

    // Add secondary SceneViews
    for (SceneManager manager : getSceneManagers()) {
      SceneView secondarySceneView = ((LayoutlibSceneManager)manager).getSecondarySceneView();
      if (secondarySceneView != null) {
        builder.add(secondarySceneView);
      }
    }
    return builder.build();
  }

  public void setAdaptiveIconShape(@NotNull ShapeMenuAction.AdaptiveIconShape adaptiveIconShape) {
    myAdaptiveIconShape = adaptiveIconShape;
  }

  @NotNull
  public ShapeMenuAction.AdaptiveIconShape getAdaptiveIconShape() {
    return myAdaptiveIconShape;
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
  public float getScreenScalingFactor() {
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
        .collect(
          ImmutableCollectors.toImmutableList());
    return new ItemTransferable(new DnDTransferItem(model != null ? model.getId() : 0, components));
  }

  @SwingCoordinate
  @Override
  protected Dimension getDefaultOffset() {
    return new Dimension(2 * DEFAULT_SCREEN_OFFSET_X, 2 * DEFAULT_SCREEN_OFFSET_Y);
  }

  @SwingCoordinate
  @NotNull
  @Override
  protected Dimension getPreferredContentSize(@SwingCoordinate int availableWidth, @SwingCoordinate int availableHeight) {
    Collection<SceneView> sceneViews = getSceneViews();
    Dimension extent = getExtentSize();
    return myLayoutManager.getPreferredSize(sceneViews, extent.width, extent.height, null);
  }

  @Override
  public CompletableFuture<Void> setModel(@Nullable NlModel model) {
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
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component);

    if (handler != null) {
      handler.onActivateInDesignSurface(component, getSceneManager().getViewEditor(), x, y);
    }
    super.notifyComponentActivate(component, x, y);
  }

  @NotNull
  @Override
  public Consumer<NlComponent> getComponentRegistrar() {
    return component -> NlComponentHelper.INSTANCE.registerComponent(component);
  }

  public void setMockupVisible(boolean mockupVisible) {
    myMockupVisible = mockupVisible;
    repaint();
  }

  public boolean isMockupVisible() {
    return myMockupVisible;
  }

  public void setMockupEditor(@Nullable MockupEditor mockupEditor) {
    myMockupEditor = mockupEditor;
  }

  @Nullable
  public MockupEditor getMockupEditor() {
    return myMockupEditor;
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
        // Look up *current* result; a newer one could be available
        LayoutlibSceneManager sceneManager = getSceneManager();
        RenderResult result = sceneManager != null ? sceneManager.getRenderResult() : null;
        if (result == null) {
          return;
        }
        if (NELE_LAYOUT_VALIDATOR_IN_EDITOR.get() && !getModels().isEmpty()) {
          myValidator.validateAndUpdateLint(result, getModels().get(0));
        }

        Project project = getProject();
        if (project.isDisposed()) {
          return;
        }

        // createErrorModel needs to run in Smart mode to resolve the classes correctly
        DumbService.getInstance(project).runReadActionInSmartMode(() -> {
          BuildMode gradleBuildMode = BuildSettings.getInstance(project).getBuildMode();
          RenderErrorModel model = gradleBuildMode != null && result.getLogger().hasErrors()
                                   ? RenderErrorModel.STILL_BUILDING_ERROR_MODEL
                                   : RenderErrorModelFactory
                                     .createErrorModel(NlDesignSurface.this, result, null);
          if (myRenderIssueProvider != null) {
            getIssueModel().removeIssueProvider(myRenderIssueProvider);
          }
          myRenderIssueProvider = new RenderIssueProvider(model);
          getIssueModel().addIssueProvider(myRenderIssueProvider);
        });
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

  @NotNull
  @Override
  public CompletableFuture<Void> forceUserRequestedRefresh() {
    return requestSequentialRender(manager -> ((LayoutlibSceneManager)manager).requestUserInitiatedRender());
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
  protected double getMinScale() {
    return Math.max(getFitScale(true), myMinScale);
  }

  @Override
  protected double getMaxScale() {
    return myMaxScale;
  }

  @Override
  public boolean canZoomToFit() {
    double minZoomLevel = myMinScale / getScreenScalingFactor();
    double maxZoomLevel = myMaxScale / getScreenScalingFactor();
    double zoomToFitLevel = Math.max(minZoomLevel, Math.min(getFitScale(true), maxZoomLevel));
    return Math.abs(getScale() - zoomToFitLevel) > 0.01;
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
    double fitScale = getFitScale(areaToCenter.getSize(), false);

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
  }

  public boolean isRenderingSynchronously() { return myIsRenderingSynchronously; }

  public void setAnimationScrubbing(boolean value) {
    myIsAnimationScrubbing = value;
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
  public final Object getData(@NotNull String dataId) {
    Object data = myDelegateDataProvider != null ? myDelegateDataProvider.getData(dataId) : null;
    if (data != null) {
      return data;
    }

    if (LayoutPreviewHandlerKt.LAYOUT_PREVIEW_HANDLER_KEY.is(dataId) ) {
      return this;
    }
    else if(LAYOUT_VALIDATOR_KEY.is(dataId)) {
      return myValidatorControl;
    }

    return super.getData(dataId);
  }

  @Override
  public boolean getPreviewWithToolsVisibilityAndPosition() {
    return myPreviewWithToolsVisibilityAndPosition;
  }

  @Override
  public void setPreviewWithToolsVisibilityAndPosition(boolean isPreviewWithToolsVisibilityAndPosition) {
    if (myPreviewWithToolsVisibilityAndPosition != isPreviewWithToolsVisibilityAndPosition) {
      myPreviewWithToolsVisibilityAndPosition = isPreviewWithToolsVisibilityAndPosition;
      forceUserRequestedRefresh();
    }
  }
}
