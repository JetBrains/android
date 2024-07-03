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
import com.android.annotations.concurrency.UiThread;
import com.android.tools.idea.common.analytics.DesignerAnalyticsManager;
import com.android.tools.idea.common.editor.ActionManager;
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.OverlayLayout;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A generic design surface for use in a graphical editor.
 */
public abstract class DesignSurface<T extends SceneManager> extends PreviewSurface<T> {

  @Nullable protected final JScrollPane myScrollPane;

  @Nullable
  @Override
  public JScrollPane getScrollPane() {
    return myScrollPane;
  }

  /**
   * Component that wraps the displayed content. If this is a scrollable surface, that will be the Scroll Pane.
   * Otherwise, it will be the ScreenViewPanel container.
   */
  @NotNull private final JComponent myContentContainerPane;
  @NotNull protected final DesignSurfaceViewport myViewport;
  @NotNull protected final SceneViewPanel mySceneViewPanel;

  @Override
  protected @NotNull SceneViewPanel getSceneViewPanel() { return mySceneViewPanel; }

  @VisibleForTesting
  private final GuiInputHandler myGuiInputHandler;

  private final ActionManager<? extends DesignSurface<T>> myActionManager;

  private boolean myIsActive = false;

  @Override
  protected boolean isActive() {
    return myIsActive;
  }

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
    if (myScrollPane != null) {
      getLayeredPane().setLayout(new MatchParentLayoutManager());
      getLayeredPane().add(myScrollPane, JLayeredPane.POPUP_LAYER);
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
      getLayeredPane().setLayout(new OverlayLayout(getLayeredPane()));
      mySceneViewPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
      getLayeredPane().add(mySceneViewPanel, JLayeredPane.POPUP_LAYER);
      myContentContainerPane = mySceneViewPanel;
      myViewport = new NonScrollableDesignSurfaceViewport(this);
    }

    add(getLayeredPane());

    Interactable interactable = interactableProvider.apply(this);
    myGuiInputHandler = new GuiInputHandler(this, interactable, interactionProviderCreator.apply(this));
    myGuiInputHandler.startListening();
    //noinspection AbstractMethodCallInConstructor
    myActionManager = actionManagerProvider.apply(this);
    myActionManager.registerActionsShortcuts(getLayeredPane());

    if (hasZoomControls) {
      JPanel zoomControlsLayerPane = new JPanel();
      zoomControlsLayerPane.setBorder(JBUI.Borders.empty(UIUtil.getScrollBarWidth()));
      zoomControlsLayerPane.setOpaque(false);
      zoomControlsLayerPane.setLayout(new BorderLayout());
      zoomControlsLayerPane.setFocusable(false);

      getLayeredPane().add(zoomControlsLayerPane, JLayeredPane.DRAG_LAYER);
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
  @Override
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
  @Override
  public void validateScrollArea() {
    // Mark both the sceneview panel and the scroll pane as invalid to force a relayout.
    mySceneViewPanel.invalidate();
    myContentContainerPane.invalidate();
    // Validate the scroll pane immediately and layout components.
    myContentContainerPane.validate();
    mySceneViewPanel.repaint();
  }

  @UiThread
  @Override
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
    getIssueModel().activate();
  }

  public void deactivate() {
    if (myIsActive) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(myOnHoverListener);
      for (SceneManager manager : getSceneManagers()) {
        manager.deactivate(this);
      }
    }
    myIsActive = false;
    getIssueModel().deactivate();

    myGuiInputHandler.cancelInteraction();
  }

  @NotNull
  @Override
  public GuiInputHandler getGuiInputHandler() {
    return myGuiInputHandler;
  }

  @Override
  public Object getData(@NotNull @NonNls String dataId) {
    if (DESIGN_SURFACE.is(dataId) || GuiInputHandler.CURSOR_RECEIVER.is(dataId)) {
      return this;
    }
    if (PANNABLE_KEY.is(dataId)) {
      return getPannable();
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

  @Override
  public void updateUI() {
    super.updateUI();
    //noinspection FieldAccessNotGuarded We are only accessing the reference so we do not need to guard the access
    if (getModelToSceneManagers() != null) {
      // updateUI() is called in the parent constructor, at that time all class member in this class has not initialized.
      for (SceneManager manager : getSceneManagers()) {
        manager.getSceneViews().forEach(SceneView::updateUI);
      }
    }
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
