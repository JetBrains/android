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
package com.android.tools.idea.common.surface;

import static com.android.tools.adtui.PannableKt.PANNABLE_KEY;
import static com.android.tools.adtui.ZoomableKt.ZOOMABLE_KEY;
import static com.android.tools.idea.actions.DesignerDataKeys.CONFIGURATIONS;
import static com.android.tools.idea.actions.DesignerDataKeys.DESIGN_SURFACE;

import com.android.annotations.VisibleForTesting;
import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.Slow;
import com.android.annotations.concurrency.UiThread;
import com.android.sdklib.AndroidCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
import com.android.tools.idea.common.error.IssueModel;
import com.android.tools.idea.common.error.LintIssueProvider;
import com.android.tools.idea.common.lint.LintAnnotationsModel;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.DefaultSelectionModel;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.layout.DesignSurfaceViewport;
import com.android.tools.idea.common.layout.manager.MatchParentLayoutManager;
import com.android.tools.idea.common.surface.layout.NonScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.surface.layout.ScrollableDesignSurfaceViewport;
import com.android.tools.idea.common.layout.manager.PositionableContentLayoutManager;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.OverlayLayout;
import javax.swing.SwingUtilities;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * A generic design surface for use in a graphical editor.
 */
public abstract class DesignSurface<T extends SceneManager> extends PreviewSurface<T> {

  /**
   * Filter got {@link #getModels()} to avoid returning disposed elements
   **/
  private static final Predicate<NlModel> FILTER_DISPOSED_MODELS = input -> input != null && !input.getModule().isDisposed();
  /**
   * Filter got {@link #getSceneManagers()} ()} to avoid returning disposed elements
   **/
  private final Predicate<T> FILTER_DISPOSED_SCENE_MANAGERS =
    input -> input != null && FILTER_DISPOSED_MODELS.apply(input.getModel());

  private static final Integer LAYER_PROGRESS = JLayeredPane.POPUP_LAYER + 10;
  private static final Integer LAYER_MOUSE_CLICK = LAYER_PROGRESS + 10;

  /**
   * {@link JScrollPane} contained in this surface when zooming is enabled.
   */
  @Nullable protected final JScrollPane myScrollPane;
  /**
   * Component that wraps the displayed content. If this is a scrollable surface, that will be the Scroll Pane.
   * Otherwise, it will be the ScreenViewPanel container.
   */
  @NotNull private final JComponent myContentContainerPane;
  @NotNull protected final DesignSurfaceViewport myViewport;
  @NotNull private final JLayeredPane myLayeredPane;
  @NotNull protected final SceneViewPanel mySceneViewPanel;
  @NotNull private final MouseClickDisplayPanel myMouseClickDisplayPanel;
  @VisibleForTesting
  private final GuiInputHandler myGuiInputHandler;

  private final ActionManager<? extends DesignSurface<T>> myActionManager;
  private final ReentrantReadWriteLock myModelToSceneManagersLock = new ReentrantReadWriteLock();
  @GuardedBy("myModelToSceneManagersLock")
  private final LinkedHashMap<NlModel, T> myModelToSceneManagers = new LinkedHashMap<>();

  protected final IssueModel myIssueModel;
  private boolean myIsActive = false;
  private LintIssueProvider myLintIssueProvider;

  /**
   * Responsible for converting this surface state and send it for tracking (if logging is enabled).
   */
  @NotNull
  private final DesignerAnalyticsManager myAnalyticsManager;

  @NotNull
  private final Function<DesignSurface<T>, DesignSurfaceActionHandler> myActionHandlerProvider;

  @NotNull
  private final AWTEventListener myOnHoverListener;

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function<DesignSurface<T>, DesignSurfaceActionHandler> designSurfaceActionHandlerProvider,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    this(project, parentDisposable, actionManagerProvider, SurfaceInteractable::new, interactionProviderCreator,
         positionableLayoutManagerProvider, designSurfaceActionHandlerProvider, new DefaultSelectionModel(), zoomControlsPolicy);
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function<DesignSurface<T>, Interactable> interactableProvider,
    @NotNull Function<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function<DesignSurface<T>, DesignSurfaceActionHandler> actionHandlerProvider,
    @NotNull SelectionModel selectionModel,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    super(project, selectionModel, zoomControlsPolicy, new BorderLayout());

    Disposer.register(parentDisposable, this);
    myIssueModel = new IssueModel(this, getProject());

    boolean hasZoomControls = getZoomControlsPolicy() != ZoomControlsPolicy.HIDDEN;

    myAnalyticsManager = new DesignerAnalyticsManager(this);

    myActionHandlerProvider = actionHandlerProvider;

    // TODO: handle the case when selection are from different NlModels.
    // Manager can be null if the selected component is not part of NlModel. For example, a temporarily NlMode.
    // In that case we don't change focused SceneView.
    SelectionListener selectionListener = (model, selection) -> {
      if (getFocusedSceneView() != null) {
        notifySelectionListeners(selection);
      }
      else {
        notifySelectionListeners(Collections.emptyList());
      }
    };
    getSelectionModel().addListener(selectionListener);

    myProgressPanel = new SurfaceProgressPanel(this, this::useSmallProgressIcon);
    myProgressPanel.setName("Layout Editor Progress Panel");

    mySceneViewPanel = new SceneViewPanel(
      this::getSceneViews,
      () -> getGuiInputHandler().getLayers(),
      this::getActionManager,
      this,
      this::shouldRenderErrorsPanel,
      positionableLayoutManagerProvider.apply(this));
    mySceneViewPanel.setBackground(getBackground());

    if (hasZoomControls) {
      myScrollPane = DesignSurfaceScrollPane.createDefaultScrollPane(mySceneViewPanel, getBackground(), this::notifyPanningChanged);
    }
    else {
      myScrollPane = null;
    }
    myMouseClickDisplayPanel = new MouseClickDisplayPanel(this);

    // Setup the layers for the DesignSurface
    // If the surface is scrollable, we use four layers:
    //
    //  1. ScrollPane layer: Layer that contains the ScreenViews and does all the rendering, including the interaction layers.
    //  2. Progress layer: Displays the progress icon while a rendering is happening
    //  3. Mouse click display layer: It allows displaying clicks on the surface with a translucent bubble
    //  4. Zoom controls layer: Used to display the zoom controls of the surface
    //
    //  (4) sits at the top of the stack so is the first one to receive events like clicks.
    //
    // If the surface is NOT scrollable, the zoom controls will not be added and the scroll pane will be replaced
    // by the actual content.
    myLayeredPane = new JLayeredPane();
    myLayeredPane.setFocusable(true);
    if (myScrollPane != null) {
      myLayeredPane.setLayout(new MatchParentLayoutManager());
      myLayeredPane.add(myScrollPane, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = myScrollPane;
      myViewport = new ScrollableDesignSurfaceViewport(myScrollPane.getViewport());
      myScrollPane.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          // Relayout the PositionableContents when visible size (e.g. window size) of ScrollPane is changed.
          revalidateScrollArea();
        }
      });
    }
    else {
      myLayeredPane.setLayout(new OverlayLayout(myLayeredPane));
      mySceneViewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
      myLayeredPane.add(mySceneViewPanel, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = mySceneViewPanel;
      myViewport = new NonScrollableDesignSurfaceViewport(this);
    }
    myLayeredPane.add(myProgressPanel, LAYER_PROGRESS);
    myLayeredPane.add(myMouseClickDisplayPanel, LAYER_MOUSE_CLICK);

    add(myLayeredPane);



    Interactable interactable = interactableProvider.apply(this);
    myGuiInputHandler = new GuiInputHandler(this, interactable, interactionProviderCreator.apply(this));
    myGuiInputHandler.startListening();
    //noinspection AbstractMethodCallInConstructor
    myActionManager = actionManagerProvider.apply(this);
    myActionManager.registerActionsShortcuts(myLayeredPane);

    if (hasZoomControls) {
      JPanel zoomControlsLayerPane = new JPanel();
      zoomControlsLayerPane.setBorder(JBUI.Borders.empty(UIUtil.getScrollBarWidth()));
      zoomControlsLayerPane.setOpaque(false);
      zoomControlsLayerPane.setLayout(new BorderLayout());
      zoomControlsLayerPane.setFocusable(false);

      myLayeredPane.add(zoomControlsLayerPane, JLayeredPane.DRAG_LAYER);
      zoomControlsLayerPane.add(myActionManager.getDesignSurfaceToolbar(), BorderLayout.EAST);
      if (getZoomControlsPolicy() == ZoomControlsPolicy.AUTO_HIDE) {
        myOnHoverListener = DesignSurfaceHelper.createZoomControlAutoHiddenListener(this, zoomControlsLayerPane);
        zoomControlsLayerPane.setVisible(false);
        Toolkit.getDefaultToolkit().addAWTEventListener(myOnHoverListener, AWTEvent.MOUSE_EVENT_MASK);
      }
      else {
        myOnHoverListener = event -> {};
      }
    }
    else {
      myOnHoverListener = event -> {};
    }
  }


  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  @NotNull
  public DesignSurfaceViewport getViewport() {
    return myViewport;
  }

  @NotNull
  public ActionManager getActionManager() {
    return myActionManager;
  }

  // Allow a test to override myActionHandlerProvider when the surface is a mockito mock
  @NotNull
  public Function<DesignSurface<T>, DesignSurfaceActionHandler> getActionHandlerProvider() {
    return myActionHandlerProvider;
  }

  /**
   * @return the list of added {@link NlModel}s.
   * @see #getModel()
   */
  @NotNull
  @Override
  public ImmutableList<NlModel> getModels() {
    myModelToSceneManagersLock.readLock().lock();
    try {
      return ImmutableList.copyOf(Sets.filter(myModelToSceneManagers.keySet(), FILTER_DISPOSED_MODELS));
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * Returns the list of all the {@link SceneManager} part of this surface
   */
  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
  @NotNull
  @Override
  public ImmutableList<T> getSceneManagers() {
    myModelToSceneManagersLock.readLock().lock();
    try {
      return ImmutableList.copyOf(Collections2.filter(myModelToSceneManagers.values(), FILTER_DISPOSED_SCENE_MANAGERS));
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * Add an {@link NlModel} to DesignSurface and return the created {@link SceneManager}.
   * If it is added before then it just returns the associated {@link SceneManager} which was created before. The {@link NlModel} will be
   * moved to the last position which might affect rendering.
   *
   * @param model the added {@link NlModel}
   * @see #addAndRenderModel(NlModel)
   */
  @Slow
  @NotNull
  private T addModel(@NotNull NlModel model) {
    T manager = getSceneManager(model);
    if (manager != null) {
      myModelToSceneManagersLock.writeLock().lock();
      try {
        // No need to add same model twice. We just move it to the bottom of the model list since order is important.
        T managerToMove = myModelToSceneManagers.remove(model);
        if (managerToMove != null) {
          myModelToSceneManagers.put(model, managerToMove);
        }
        return manager;
      }
      finally {
        myModelToSceneManagersLock.writeLock().unlock();
      }
    }

    model.addListener(getModelListener());
    // SceneManager creation is a slow operation. Multiple can happen in parallel.
    // We optimistically create a new scene manager for the given model and then, with the mapping
    // locked we checked if a different one has been added.
    T newManager = createSceneManager(model);
    myModelToSceneManagersLock.writeLock().lock();
    try {
      manager = myModelToSceneManagers.putIfAbsent(model, newManager);
      if (manager == null) {
        // The new SceneManager was correctly added
        manager = newManager;
      }
    }
    finally {
      myModelToSceneManagersLock.writeLock().unlock();
    }
    if (manager != newManager) {
      // There was already a manager assigned to the model so discard this one.
      Disposer.dispose(newManager);
    }
    if (myIsActive) {
      manager.activate(this);
    }
    return manager;
  }

  /**
   * Add an {@link NlModel} to DesignSurface and refreshes the rendering of the model. If the model was already part of the surface, it will
   * be moved to the bottom of the list and a refresh will be triggered.
   * The scene views are updated before starting to render and the callback
   * {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)} is triggered after rendering.
   * The method returns a {@link CompletableFuture} that will complete when the render of the new model has finished.
   * <br/><br/>
   * Note that the order of the addition might be important for the rendering order. {@link PositionableContentLayoutManager} will receive
   * the models in the order they are added.
   *
   * @param model the added {@link NlModel}
   * @see #addModel(NlModel)
   */
  @NotNull
  public final CompletableFuture<Void> addAndRenderModel(@NotNull NlModel model) {
    T modelSceneManager = addModel(model);
    // Mark the scene view panel as invalid to force the scene views to be updated
    mySceneViewPanel.invalidate();

    // We probably do not need to request a render for all models but it is currently the
    // only point subclasses can override to disable the layoutlib render behaviour.
    return modelSceneManager.requestRenderAsync()
      .whenCompleteAsync((result, ex) -> {
        reactivateGuiInputHandler();

        revalidateScrollArea();

        // TODO(b/147225165): The tasks depends on model inflating callback should be moved to ModelListener#modelDerivedDataChanged.
        for (DesignSurfaceListener listener : getListeners()) {
          listener.modelChanged(this, model);
        }
      }, EdtExecutorService.getInstance());
  }

  /**
   * Add an {@link NlModel} to DesignSurface and return the created {@link SceneManager}.
   * If it is added before then it just returns the associated {@link SceneManager} which created before.
   * In this function, the scene views are not updated and {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)}
   * callback is triggered immediately.
   * In the opposite, {@link #addAndRenderModel(NlModel)} updates the scene views and triggers
   * {@link DesignSurfaceListener#modelChanged(DesignSurface, NlModel)} when render is completed.
   * <p>
   * <br/><br/>
   * Note that the order of the addition might be important for the rendering order. {@link PositionableContentLayoutManager} will receive
   * the models in the order they are added.
   * <p>
   * TODO(b/147225165): Remove #addAndRenderModel function and rename this function as #addModel
   *
   * @param model the added {@link NlModel}
   * @see #addModel(NlModel)
   * @see #addAndRenderModel(NlModel)
   */
  @NotNull
  public final CompletableFuture<T> addModelWithoutRender(@NotNull NlModel modelToAdd) {
    return CompletableFuture
      .supplyAsync(() -> addModel(modelToAdd), AppExecutorUtil.getAppExecutorService())
      .whenCompleteAsync((model, ex) -> {
          if (getProject().isDisposed() || modelToAdd.isDisposed()) return;
          for (DesignSurfaceListener listener : getSurfaceListeners()) {
            // TODO: The listeners have the expectation of the call happening in the EDT. We need
            //       to address that.
            listener.modelChanged(this, modelToAdd);
          }
          reactivateGuiInputHandler();
      }, EdtExecutorService.getInstance());
  }

  /**
   * Remove an {@link NlModel} from DesignSurface. If it had not been added before then nothing happens.
   *
   * @param model the {@link NlModel} to remove
   * @return true if the model existed and was removed
   */
  private boolean removeModelImpl(@NotNull NlModel model) {
    SceneManager manager;
    myModelToSceneManagersLock.writeLock().lock();
    try {
      manager = myModelToSceneManagers.remove(model);
    }
    finally {
      myModelToSceneManagersLock.writeLock().unlock();
    }

    // Mark the scene view panel as invalid to force the scene views to be updated
    mySceneViewPanel.removeSceneViewForModel(model);

    if (manager == null) {
      return false;
    }

    model.deactivate(this);

    model.removeListener(getModelListener());

    Disposer.dispose(manager);
    UIUtil.invokeLaterIfNeeded(this::revalidateScrollArea);
    return true;
  }

  /**
   * Remove an {@link NlModel} from DesignSurface. If it isn't added before then nothing happens.
   *
   * @param model the {@link NlModel} to remove
   */
  public void removeModel(@NotNull NlModel model) {
    if (!removeModelImpl(model)) {
      return;
    }

    reactivateGuiInputHandler();
  }

  /**
   * Sets the current {@link NlModel} to DesignSurface.
   *
   * @see #addAndRenderModel(NlModel)
   * @see #removeModel(NlModel)
   */
  @Override
  public @NotNull CompletableFuture<Void> setModel(@Nullable NlModel model) {
    NlModel oldModel = getModel();
    if (model == oldModel) {
      return CompletableFuture.completedFuture(null);
    }

    if (oldModel != null) {
      removeModelImpl(oldModel);
    }

    if (model == null) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.supplyAsync(
      () -> addModel(model),
      AppExecutorUtil.getAppExecutorService()
    )
      .thenCompose((sceneManager) -> requestRender())
      .whenCompleteAsync((result, ex) -> {
        // Mark the scene view panel as invalid to force the scene views to be updated
        mySceneViewPanel.invalidate();

        reactivateGuiInputHandler();
        restoreZoomOrZoomToFit();
        revalidateScrollArea();

        // TODO: The listeners have the expectation of the call happening in the EDT. We need
        //       to address that.
        for (DesignSurfaceListener listener : getListeners()) {
          listener.modelChanged(this, model);
        }
      }, EdtExecutorService.getInstance())
      .thenRun(() -> {});
  }

  /**
   * Update the status of {@link GuiInputHandler}. It will start or stop listening depending on the current layout type.
   */
  private void reactivateGuiInputHandler() {
    if (isEditable()) {
      myGuiInputHandler.startListening();
    }
    else {
      myGuiInputHandler.stopListening();
    }
  }

  @Override
  public void dispose() {
    clearListeners();
    myGuiInputHandler.stopListening();
    Toolkit.getDefaultToolkit().removeAWTEventListener(myOnHoverListener);
    synchronized (getRenderFutures()) {
      for (CompletableFuture<Void> future : getRenderFutures()) {
        try {
          future.cancel(true);
        }
        catch (CancellationException ignored) {
        }
      }
      getRenderFutures().clear();
    }
    if (getRepaintTimer().isRunning()) {
      getRepaintTimer().stop();
    }
    getModels().forEach(this::removeModelImpl);
  }

  /**
   * Re-layouts the ScreenViews contained in this design surface immediately.
   */
  @UiThread
  public void validateScrollArea() {
    // Mark both the sceneview panel and the scroll pane as invalid to force a relayout.
    mySceneViewPanel.invalidate();
    myContentContainerPane.invalidate();
    // Validate the scroll pane immediately and layout components.
    myContentContainerPane.validate();
    mySceneViewPanel.repaint();
  }

  /**
   * Asks the ScreenViews for a re-layouts the ScreenViews contained in this design surface. The re-layout will not happen immediately in
   * this call.
   */
  @UiThread
  public void revalidateScrollArea() {
    // Mark the scene view panel as invalid to force a revalidation when the scroll pane is revalidated.
    mySceneViewPanel.invalidate();
    // Schedule a layout for later.
    myContentContainerPane.revalidate();
    // Also schedule a repaint.
    mySceneViewPanel.repaint();
  }

  public JComponent getPreferredFocusedComponent() {
    return getInteractionPane();
  }

  /**
   * Returns the list of SceneViews attached to this surface
   */
  @NotNull
  public ImmutableCollection<SceneView> getSceneViews() {
    return getSceneManagers().stream()
      .flatMap(sceneManager -> sceneManager.getSceneViews().stream())
      .collect(ImmutableList.toImmutableList());
  }

  @Override
  public void onHover(@SwingCoordinate int x, @SwingCoordinate int y) {
    for (SceneView sceneView : getSceneViews()) {
      sceneView.onHover(x, y);
    }
  }

  @Override
  public void setPanning(boolean isPanning) {
    myGuiInputHandler.setPanning(isPanning);
  }

  @SwingCoordinate
  protected abstract Dimension getScrollToVisibleOffset();

  @Override
  public boolean isPanning() {
    return myGuiInputHandler.isPanning();
  }

  @Override
  public boolean isPannable() {
    return true;
  }

  public Rectangle getCurrentScrollRectangle() {
    if (myScrollPane == null) return null;
    return new Rectangle(myScrollPane.getViewport().getViewPosition(), myScrollPane.getViewport().getSize());
  }

  /**
   * Given a rectangle relative to a sceneView, find its absolute coordinates and then scroll to
   * center such rectangle. See {@link #scrollToCenter(Rectangle)}
   * @param sceneView the {@link SceneView} that contains the given rectangle.
   * @param rectangle the rectangle that should be visible, with its coordinates relative to the sceneView.
   */
  protected void scrollToCenter(@NotNull SceneView sceneView, @NotNull @SwingCoordinate Rectangle rectangle) {
    Dimension availableSpace = getExtentSize();
    Rectangle sceneViewRectangle =
      mySceneViewPanel.findMeasuredSceneViewRectangle(sceneView,
                                                      availableSpace);
    if (sceneViewRectangle != null) {
      Point topLeftCorner = new Point(sceneViewRectangle.x + rectangle.x,
                                      sceneViewRectangle.y + rectangle.y);
      scrollToCenter(new Rectangle(topLeftCorner, rectangle.getSize()));
    }
  }

  /**
   * Move the scroll position to make the given rectangle visible and centered.
   * If the given rectangle is too big for the available space, it will be centered anyway and
   * some of its borders will probably not be visible at the new scroll position.
   * @param rectangle the rectangle that should be centered.
   */
  protected void scrollToCenter(@NotNull @SwingCoordinate Rectangle rectangle) {
    Dimension availableSpace = getExtentSize();
    int extraW = availableSpace.width - rectangle.width;
    int extraH = availableSpace.height - rectangle.height;
    setScrollPosition(rectangle.x - (extraW + 1) / 2, rectangle.y - (extraH + 1) / 2);
  }

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed.
   * If the {@link SceneView} is partially visible and {@code forceScroll} is set to {@code false}, no scroll will happen.
   */
  public final void scrollToVisible(@NotNull SceneView sceneView, boolean forceScroll) {
    Rectangle rectangle = mySceneViewPanel.findSceneViewRectangle(sceneView);
    if (rectangle != null && (forceScroll || !getViewport().getViewRect().intersects(rectangle))) {
      Dimension offset = getScrollToVisibleOffset();
      setScrollPosition(rectangle.x - offset.width, rectangle.y - offset.height);
    }
  }

  /**
   * Ensures that the given model is visible in the surface by scrolling to it if needed.
   * If the {@link NlModel} is partially visible and {@code forceScroll} is set to {@code false}, no scroll will happen.
   */
  public final void scrollToVisible(@NotNull NlModel model, boolean forceScroll) {
    getSceneViews().stream().filter(sceneView -> sceneView.getSceneManager().getModel() == model).findFirst()
      .ifPresent(sceneView -> scrollToVisible(sceneView, forceScroll));
  }

  public void setScrollPosition(@SwingCoordinate int x, @SwingCoordinate int y) {
    setScrollPosition(new Point(x, y));
  }

  /**
   * Sets the offset for the scroll viewer to the specified x and y values
   * The offset will never be less than zero, and never greater that the
   * maximum value allowed by the sizes of the underlying view and the extent.
   * If the zoom factor is large enough that a scroll bars isn't visible,
   * the position will be set to zero.
   */
  @Override
  public void setScrollPosition(@SwingCoordinate Point p) {
    p.setLocation(Math.max(0, p.x), Math.max(0, p.y));

    Dimension extent = getExtentSize();
    Dimension view = getViewSize();

    int minX = Math.min(p.x, view.width - extent.width);
    int minY = Math.min(p.y, view.height - extent.height);

    p.setLocation(minX, minY);

    getViewport().setViewPosition(p);
  }

  @Override
  @NotNull
  @SwingCoordinate
  public Point getScrollPosition() {
    return getViewport().getViewPosition();
  }

  /**
   * Returns the size of the surface scroll viewport.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getExtentSize() {
    return getViewport().getExtentSize();
  }

  /**
   * Returns the size of the surface containing the ScreenViews.
   */
  @NotNull
  @SwingCoordinate
  public Dimension getViewSize() {
    return getViewport().getViewSize();
  }

  @SurfaceScale protected double getBoundedScale(@SurfaceScale double scale) {
    return Math.min(Math.max(scale, getZoomController().getMinScale()), getZoomController().getMaxScale());
  }

  @Override
  public void onScaleChange(@NotNull ScaleChange update) {
    NlModel model = Iterables.getFirst(getModels(), null);
    if(update.isAnimating()){
      revalidateScrollArea();
      return;
    }
    if (model != null) {
      storeCurrentScale(model);
    }
    revalidateScrollArea();
    notifyScaleChanged(update.getPreviousScale(), update.getNewScale());
  }

  /**
   * Save the current zoom level from the file of the given {@link NlModel}.
   */
  private void storeCurrentScale(@NotNull NlModel model) {
    if (!isKeepingScaleWhenReopen()) {
      return;
    }
    SurfaceState state = DesignSurfaceSettings.getInstance(model.getProject()).getSurfaceState();
    state.saveFileScale(getProject(), model.getVirtualFile(), getZoomController());
  }

  @NotNull
  public JComponent getLayeredPane() {
    return myLayeredPane;
  }

  @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
  @NotNull
  public JComponent getInteractionPane() {
    return mySceneViewPanel;
  }

  @NotNull
  public DesignerAnalyticsManager getAnalyticsManager() {
    return myAnalyticsManager;
  }

  protected void notifySelectionListeners(@NotNull List<NlComponent> newSelection) {
    List<DesignSurfaceListener> listeners = getSurfaceListeners();
    for (DesignSurfaceListener listener : listeners) {
      listener.componentSelectionChanged(this, newSelection);
    }
  }

  /**
   * @param x the x coordinate of the double click converted to pixels in the Android coordinate system
   * @param y the y coordinate of the double click converted to pixels in the Android coordinate system
   */
  public void notifyComponentActivate(@NotNull NlComponent component, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    notifyComponentActivate(component);
  }

  public void notifyComponentActivate(@NotNull NlComponent component) {
    activatePreferredEditor(component);
  }

  protected void activatePreferredEditor(@NotNull NlComponent component) {
    for (DesignSurfaceListener listener : getListeners()) {
      if (listener.activatePreferredEditor(this, component)) {
        break;
      }
    }
  }

  /**
   * The editor has been activated
   */
  public void activate() {
    if (Disposer.isDisposed(this)) {
      // Prevent activating a disposed surface.
      return;
    }

    if (!myIsActive) {
      for (SceneManager manager : getSceneManagers()) {
        manager.activate(this);
      }
      if (getZoomControlsPolicy() == ZoomControlsPolicy.AUTO_HIDE) {
        Toolkit.getDefaultToolkit().addAWTEventListener(myOnHoverListener, AWTEvent.MOUSE_EVENT_MASK);
      }
    }
    myIsActive = true;
    myIssueModel.activate();
  }

  public void deactivateIssueModel() {
    myIssueModel.deactivate();
  }

  public void deactivate() {
    if (myIsActive) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(myOnHoverListener);
      for (SceneManager manager : getSceneManagers()) {
        manager.deactivate(this);
      }
    }
    myIsActive = false;
    myIssueModel.deactivate();

    myGuiInputHandler.cancelInteraction();
  }

  @Override
  @Deprecated
  @Nullable
  public SceneView getSceneViewAtOrPrimary(@SwingCoordinate int x, @SwingCoordinate int y) {
    SceneView view = getSceneViewAt(x, y);
    if (view == null) {
      // TODO: For keeping the behaviour as before in multi-model case, we return primary SceneView when there is no hovered SceneView.
      SceneManager manager = getSceneManager();
      if (manager != null) {
        view = manager.getSceneView();
      }
    }
    return view;
  }

  @Override
  @Nullable
  public SceneView getSceneViewAt(@SwingCoordinate int x, @SwingCoordinate int y) {
    Collection<SceneView> sceneViews = getSceneViews();
    Dimension scaledSize = new Dimension();
    for (SceneView view : sceneViews) {
      view.getScaledContentSize(scaledSize);
      if (view.getX() <= x &&
          x <= (view.getX() + scaledSize.width) &&
          view.getY() <= y &&
          y <= (view.getY() + scaledSize.height)) {
        return view;
      }
    }
    return null;
  }

  /**
   * Returns the {@link SceneView} under the mouse cursor if the mouse is within the coordinates of this surface or null
   * otherwise.
   */
  @Nullable
  public SceneView getSceneViewAtMousePosition() {
    Point mouseLocation = !GraphicsEnvironment.isHeadless() ? MouseInfo.getPointerInfo().getLocation() : null;
    if (mouseLocation == null || contains(mouseLocation) || !isVisible() || !isEnabled()) {
      return null;
    }

    SwingUtilities.convertPointFromScreen(mouseLocation, mySceneViewPanel);
    return getSceneViewAt(mouseLocation.x, mouseLocation.y);
  }

  @Override
  @Deprecated
  @Nullable
  public Scene getScene() {
    SceneManager sceneManager = getSceneManager();
    return sceneManager != null ? sceneManager.getScene() : null;
  }

  /**
   * @return The {@link SceneManager} associated to the given {@link NlModel}.
   */
  @Nullable
  @Override
  public T getSceneManager(@NotNull NlModel model) {
    if (model.getModule().isDisposed()) {
      return null;
    }

    myModelToSceneManagersLock.readLock().lock();
    try {
      return myModelToSceneManagers.get(model);
    }
    finally {
      myModelToSceneManagersLock.readLock().unlock();
    }
  }

  /**
   * This is called before {@link #setModel(NlModel)}. After the returned future completes, we'll wait for smart mode and then invoke
   * {@link #setModel(NlModel)}. If a {@code DesignSurface} needs to do any extra work before the model is set it should be done here.
   */
  public CompletableFuture<?> goingToSetModel(NlModel model) {
    return CompletableFuture.completedFuture(null);
  }

  @NotNull
  public GuiInputHandler getGuiInputHandler() {
    return myGuiInputHandler;
  }

  /**
   * @return true if the content is editable (e.g. move position or drag-and-drop), false otherwise.
   */
  public boolean isEditable() {
    return getLayoutType().isEditable();
  }

  private final Set<ProgressIndicator> myProgressIndicators = new HashSet<>();

  @SuppressWarnings("FieldAccessedSynchronizedAndUnsynchronized")
  private final SurfaceProgressPanel myProgressPanel;

  public void registerIndicator(@NotNull ProgressIndicator indicator) {
    if (getProject().isDisposed() || Disposer.isDisposed(this)) {
      return;
    }

    synchronized (myProgressIndicators) {
      if (myProgressIndicators.add(indicator)) {
        myProgressPanel.showProgressIcon();
      }
    }
  }

  public void unregisterIndicator(@NotNull ProgressIndicator indicator) {
    synchronized (myProgressIndicators) {
      myProgressIndicators.remove(indicator);

      if (myProgressIndicators.isEmpty()) {
        myProgressPanel.hideProgressIcon();
      }
    }
  }

  /**
   * Invalidates all models and request a render of the layout. This will re-inflate the {@link NlModel}s and render them sequentially.
   * The result {@link CompletableFuture} will notify when all the renderings have completed.
   */
  @NotNull
  public CompletableFuture<Void> requestRender() {
    ImmutableList<T> managers = getSceneManagers();
    if (managers.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }
    return requestSequentialRender(manager -> manager.requestLayoutAndRenderAsync(false));
  }

  /**
   * Converts a given point that is in view coordinates to viewport coordinates.
   */
  @TestOnly
  @NotNull
  public Point getCoordinatesOnViewport(@NotNull Point viewCoordinates) {
    return SwingUtilities.convertPoint(mySceneViewPanel, viewCoordinates.x, viewCoordinates.y, getViewport().getViewportComponent());
  }

  @TestOnly
  public void setScrollViewSizeAndValidate(@SwingCoordinate int width, @SwingCoordinate int height) {
    if (myScrollPane != null) {
      myScrollPane.setSize(width, height);
      myScrollPane.doLayout();
      UIUtil.invokeAndWaitIfNeeded((Runnable)this::validateScrollArea);
    }
  }

  /**
   * Sets the tooltip for the design surface
   */
  public void setDesignToolTip(@Nullable String text) {
    mySceneViewPanel.setToolTipText(text);
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DESIGN_SURFACE.is(dataId) || PANNABLE_KEY.is(dataId) || GuiInputHandler.CURSOR_RECEIVER.is(dataId)) {
      return this;
    }
    if (ZOOMABLE_KEY.is(dataId)){
      return getZoomController();
    }
    if (CONFIGURATIONS.is(dataId)) {
      return getConfigurations();
    }
    if (PlatformCoreDataKeys.FILE_EDITOR.is(dataId)) {
      return getFileEditorDelegate();
    }
    else if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
             PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
             PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
             PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return getActionHandlerProvider().apply(this);
    }
    else if (PlatformDataKeys.CONTEXT_MENU_POINT.is(dataId)) {
      SceneView view = getFocusedSceneView();
      NlComponent selection = getSelectionModel().getPrimary();
      Scene scene = getScene();
      if (view == null || scene == null || selection == null) {
        return null;
      }
      SceneComponent sceneComponent = scene.getSceneComponent(selection);
      if (sceneComponent == null) {
        return null;
      }
      return new Point(Coordinates.getSwingXDip(view, sceneComponent.getCenterX()),
                       Coordinates.getSwingYDip(view, sceneComponent.getCenterY()));
    }
    else if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      return (DataProvider)this::getSlowData;
    }
    else {
      NlModel model = getModel();
      if (PlatformCoreDataKeys.MODULE.is(dataId) && model != null) {
        return model.getModule();
      }
    }

    return null;
  }

  /**
   * The data which should be obtained from the background thread.
   * @see PlatformCoreDataKeys#BGT_DATA_PROVIDER
   */
  @Nullable
  private Object getSlowData(@NotNull String dataId) {
    if (CommonDataKeys.PSI_ELEMENT.is(dataId)) {
      SceneView view = getFocusedSceneView();
      if (view != null) {
        SelectionModel selectionModel = view.getSelectionModel();
        NlComponent primary = selectionModel.getPrimary();
        if (primary != null) {
          return primary.getTagDeprecated();
        }
      }
    }
    else if (LangDataKeys.PSI_ELEMENT_ARRAY.is(dataId)) {
      SceneView view = getFocusedSceneView();
      if (view != null) {
        SelectionModel selectionModel = view.getSelectionModel();
        List<NlComponent> selection = selectionModel.getSelection();
        List<XmlTag> list = Lists.newArrayListWithCapacity(selection.size());
        for (NlComponent component : selection) {
          list.add(component.getTagDeprecated());
        }
        return list.toArray(XmlTag.EMPTY);
      }
    }
    return null;
  }

  @NotNull
  public IssueModel getIssueModel() {
    return myIssueModel;
  }

  public void setLintAnnotationsModel(@NotNull LintAnnotationsModel model) {
    if (myLintIssueProvider != null) {
      myLintIssueProvider.setLintAnnotationsModel(model);
    }
    else {
      myLintIssueProvider = new LintIssueProvider(model);
      getIssueModel().addIssueProvider(myLintIssueProvider);
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    //noinspection FieldAccessNotGuarded We are only accessing the reference so we do not need to guard the access
    if (myModelToSceneManagers != null) {
      // updateUI() is called in the parent constructor, at that time all class member in this class has not initialized.
      for (SceneManager manager : getSceneManagers()) {
        manager.getSceneViews().forEach(SceneView::updateUI);
      }
    }
  }

  /**
   * Enables the mouse click display. If enabled, the clicks of the user are displayed in the surface.
   */
  public void enableMouseClickDisplay() {
    myMouseClickDisplayPanel.setEnabled(true);
  }

  /**
   * Disables the mouse click display.
   */
  public void disableMouseClickDisplay() {
    myMouseClickDisplayPanel.setEnabled(false);
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);

    // setBackground is called before the class initialization is complete so we do the null checking to prevent calling mySceneViewPanel
    // before the constructor has completed. At that point mySceneViewPanel might still be null.
    //noinspection ConstantConditions
    if (mySceneViewPanel != null) {
      mySceneViewPanel.setBackground(bg);
    }
  }
}
