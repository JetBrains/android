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
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.OverlayLayout;
import kotlin.jvm.functions.Function1;
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

  @NotNull private final JComponent myContentContainerPane;
  protected @NotNull JComponent getContentContainerPane() { return myContentContainerPane; }

  @NotNull protected final DesignSurfaceViewport myViewport;
  @NotNull protected final SceneViewPanel mySceneViewPanel;

  @Override
  protected @NotNull SceneViewPanel getSceneViewPanel() { return mySceneViewPanel; }

  @VisibleForTesting
  private final GuiInputHandler myGuiInputHandler;

  private final ActionManager<? extends DesignSurface<T>> myActionManager;

  /**
   * Responsible for converting this surface state and send it for tracking (if logging is enabled).
   */
  @NotNull
  private final DesignerAnalyticsManager myAnalyticsManager;

  @NotNull
  private final AWTEventListener myOnHoverListener;

  @NotNull
  @Override
  public AWTEventListener getOnHoverListener() {
    return myOnHoverListener;
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function1<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function1<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function1<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function1<DesignSurface<T>, DesignSurfaceActionHandler> designSurfaceActionHandlerProvider,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    this(project, parentDisposable, actionManagerProvider, SurfaceInteractable::new, interactionProviderCreator,
         positionableLayoutManagerProvider, designSurfaceActionHandlerProvider, new DefaultSelectionModel(), zoomControlsPolicy);
  }

  public DesignSurface(
    @NotNull Project project,
    @NotNull Disposable parentDisposable,
    @NotNull Function1<DesignSurface<T>, ActionManager<? extends DesignSurface<T>>> actionManagerProvider,
    @NotNull Function1<DesignSurface<T>, Interactable> interactableProvider,
    @NotNull Function1<DesignSurface<T>, InteractionHandler> interactionProviderCreator,
    @NotNull Function1<DesignSurface<T>, PositionableContentLayoutManager> positionableLayoutManagerProvider,
    @NotNull Function1<DesignSurface<T>, DesignSurfaceActionHandler> actionHandlerProvider,
    @NotNull SelectionModel selectionModel,
    @NotNull ZoomControlsPolicy zoomControlsPolicy) {
    super(project, parentDisposable, actionManagerProvider, interactableProvider, interactionProviderCreator,
          positionableLayoutManagerProvider, actionHandlerProvider,  selectionModel, zoomControlsPolicy);

    Disposer.register(parentDisposable, this);

    boolean hasZoomControls = getZoomControlsPolicy() != ZoomControlsPolicy.HIDDEN;

    myAnalyticsManager = new DesignerAnalyticsManager(this);

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
      getPositionableLayoutManagerProvider().invoke(this));
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

    Interactable interactable = interactableProvider.invoke(this);
    myGuiInputHandler = new GuiInputHandler(this, interactable, interactionProviderCreator.invoke(this));
    myGuiInputHandler.startListening();
    //noinspection AbstractMethodCallInConstructor
    myActionManager = actionManagerProvider.invoke(this);
    myActionManager.registerActionsShortcuts(getLayeredPane());

    if (getZoomControlsLayerPane() != null) {
      getLayeredPane().add(getZoomControlsLayerPane(), JLayeredPane.DRAG_LAYER);
      getZoomControlsLayerPane().add(myActionManager.getDesignSurfaceToolbar(), BorderLayout.EAST);
      if (getZoomControlsPolicy() == ZoomControlsPolicy.AUTO_HIDE) {
        myOnHoverListener = DesignSurfaceHelper.createZoomControlAutoHiddenListener(this, getZoomControlsLayerPane());
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

  @NotNull
  @Override
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

  @NotNull
  @Override
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
      return getActionHandlerProvider().invoke(this);
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
