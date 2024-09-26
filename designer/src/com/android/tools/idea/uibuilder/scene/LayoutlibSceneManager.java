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
package com.android.tools.idea.uibuilder.scene;

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_URI;
import static com.android.tools.idea.common.surface.ShapePolicyKt.SQUARE_SHAPE_POLICY;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.sdklib.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.common.surface.LayoutScannerEnabled;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.rendering.ExecuteCallbacksResult;
import com.android.tools.rendering.InteractionEventResult;
import com.android.tools.rendering.RenderAsyncActionExecutor;
import com.android.tools.rendering.RenderTask;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends NewLayoutlibSceneManager implements InteractiveSceneManager {
  @NotNull private final ViewEditor myViewEditor;

  /** Counter for user events during the interactive session. */
  private final AtomicInteger myInteractiveEventsCounter = new AtomicInteger(0);

  /**
   * Creates a new LayoutlibSceneManager.
   *
   * @param model                      the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface              the {@link DesignSurface} user to present the result of the renders.
   * @param renderTaskDisposerExecutor {@link Executor} to be used for running the slow {@link #dispose()} calls.
   * @param sceneComponentProvider     a {@link SceneComponentHierarchyProvider} providing the mapping from
   *                                   {@link NlComponent} to {@link SceneComponent}s.
   * @param layoutScannerConfig        a {@link LayoutScannerConfiguration} for layout validation from Accessibility Testing Framework.
   */
  protected LayoutlibSceneManager(@NotNull NlModel model,
                                  @NotNull DesignSurface<? extends LayoutlibSceneManager> designSurface,
                                  @NotNull Executor renderTaskDisposerExecutor,
                                  @NotNull SceneComponentHierarchyProvider sceneComponentProvider,
                                  @NotNull LayoutScannerConfiguration layoutScannerConfig) {
    super(model, designSurface, renderTaskDisposerExecutor, sceneComponentProvider, layoutScannerConfig);
    updateSceneView();

    getDesignSurface().getSelectionModel().addListener(selectionChangeListener);

    Scene scene = getScene();

    myViewEditor = new ViewEditorImpl(model, scene);

    model.getConfiguration().addListener(configurationChangeListener);

    List<NlComponent> components = model.getTreeReader().getComponents();
    if (!components.isEmpty()) {
      NlComponent rootComponent = components.get(0).getRoot();

      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      List<SceneComponent> hierarchy = sceneComponentProvider.createHierarchy(this, rootComponent);
      SceneComponent root = hierarchy.isEmpty() ? null : hierarchy.get(0);
      if (root != null) {
        updateFromComponent(root, new HashSet<>());
        scene.setRoot(root);
        updateTargets();
        scene.setAnimated(previous);
      }
      else {
        Logger.getInstance(LayoutlibSceneManager.class).warn("No root component");
      }
    }

    model.addListener(modelChangeListener);
    areListenersRegistered = true;

    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests, but with accessibility testing
   * framework scanner disabled.
   * See {@link LayoutlibSceneManager#LayoutlibSceneManager(NlModel, DesignSurface, Executor, SceneComponentHierarchyProvider, LayoutScannerConfiguration)}
   *
   * @param model                  the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface          the {@link DesignSurface} user to present the result of the renders.
   * @param sceneComponentProvider a {@link SceneComponentHierarchyProvider providing the mapping from {@link NlComponent} to
   *                               {@link SceneComponent}s.
   */
  public LayoutlibSceneManager(@NotNull NlModel model,
                               @NotNull DesignSurface<LayoutlibSceneManager> designSurface,
                               @NotNull SceneComponentHierarchyProvider sceneComponentProvider) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      sceneComponentProvider,
      new LayoutScannerEnabled());
    getSceneRenderConfiguration().getLayoutScannerConfig().setLayoutScannerEnabled(false);
  }

  /**
   * Creates a new LayoutlibSceneManager with the default settings for running render requests.
   *
   * @param model the {@link NlModel} to be rendered by this {@link LayoutlibSceneManager}.
   * @param designSurface the {@link DesignSurface} user to present the result of the renders.
   * @param config configuration for layout validation when rendering.
   */
  public LayoutlibSceneManager(@NotNull NlModel model, @NotNull DesignSurface<LayoutlibSceneManager> designSurface, LayoutScannerConfiguration config) {
    this(
      model,
      designSurface,
      AppExecutorUtil.getAppExecutorService(),
      new LayoutlibSceneManagerHierarchyProvider(),
      config);
  }

  @NotNull
  public ViewEditor getViewEditor() {
    return myViewEditor;
  }

  @NotNull
  @Override
  protected SceneView doCreateSceneView() {
    NlModel model = getModel();

    DesignerEditorFileType type = model.getType();

    if (type == MenuFileType.INSTANCE) {
      return createSceneViewsForMenu();
    }

    SceneView primarySceneView = getDesignSurface().getScreenViewProvider().createPrimarySceneView(getDesignSurface(), this);
    setSecondarySceneView(getDesignSurface().getScreenViewProvider().createSecondarySceneView(getDesignSurface(), this));

    getDesignSurface().updateErrorDisplay();

    return primarySceneView;
  }

  private SceneView createSceneViewsForMenu() {
    NlModel model = getModel();
    XmlTag tag = model.getFile().getRootTag();
    SceneView sceneView;

    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the view objects?
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE)) {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this)
        .withLayersProvider((sv) -> {
          ColorBlindMode colorBlindMode = getDesignSurface().getScreenViewProvider().getColorBlindFilter();
          return ImmutableList.of(new ScreenViewLayer(sv, colorBlindMode, getDesignSurface(), getDesignSurface()::getRotateSurfaceDegree));
        })
        .withContentSizePolicy(NavigationViewSceneView.CONTENT_SIZE_POLICY)
        .withShapePolicy(SQUARE_SHAPE_POLICY)
        .build();
    }
    else {
      sceneView = ScreenView.newBuilder(getDesignSurface(), this).build();
    }

    getDesignSurface().updateErrorDisplay();
    return sceneView;
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestLayoutAsync(boolean animate) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("requestLayout after LayoutlibSceneManager has been disposed");
    }

    RenderTask currentTask = layoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    return currentTask.layout()
      .thenAccept(result -> {
        if (result != null && !isDisposed.get()) {
          layoutlibSceneRenderer.updateHierarchy(result);
          getModel().notifyListenersModelChangedOnLayout(animate);
        }
      });
  }

  /**
   * Triggers execution of the Handler and frame callbacks in the layoutlib
   * @return a boolean future that is completed when callbacks are executed that is true if there are more callbacks to execute
   */
  @NotNull
  public CompletableFuture<ExecuteCallbacksResult> executeCallbacksAsync() {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("executeCallbacks after LayoutlibSceneManager has been disposed");
    }
    RenderTask currentTask = layoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(ExecuteCallbacksResult.EMPTY);
    }
    return currentTask.executeCallbacks(currentTimeNanos());
  }

  /**
   * Executes the given block under a {@link RenderSession}. This allows the given block to access resources since they are set up
   * before executing it.
   *
   * @param block the {@link Callable} to be executed in the Render thread.
   * @param timeout maximum time to wait for the action to execute. If <= 0, the default timeout
   *                will be used (see {@link RenderAsyncActionExecutor).
   * @param timeUnit   the {@link TimeUnit} for the timeout.
   *
   * @return A {@link CompletableFuture} that completes when the block finalizes.
   * @see RenderTask#runAsyncRenderActionWithSession(Runnable, long, TimeUnit)
   */
  @NotNull
  public CompletableFuture<Void> executeInRenderSessionAsync(@NotNull Runnable block, long timeout, TimeUnit timeUnit) {
    RenderTask currentTask = layoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    return currentTask.runAsyncRenderActionWithSession(block, timeout, timeUnit);
  }

  private long currentTimeNanos() {
    return layoutlibSceneRenderer.getSessionClock().getTimeNanos();
  }

  /**
   * Pauses session clock, so that session time stops advancing.
   */
  @Override
  public void pauseSessionClock() {
    layoutlibSceneRenderer.getSessionClock().pause();
  }

  /**
   * Resumes session clock, so that session time keeps advancing.
   */
  @Override
  public void resumeSessionClock() {
    layoutlibSceneRenderer.getSessionClock().resume();
  }

  /**
   * Informs layoutlib that there was a (mouse) touch event detected of a particular type at a particular point
   * @param type type of touch event
   * @param x horizontal android coordinate of the detected touch event
   * @param y vertical android coordinate of the detected touch event
   * @return a future that is completed when layoutlib handled the touch event
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerTouchEventAsync(
    @NotNull RenderSession.TouchEventType type, @AndroidCoordinate int x, @AndroidCoordinate int y) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("triggerTouchEventAsync after LayoutlibSceneManager has been disposed");
    }

    RenderTask currentTask = layoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    myInteractiveEventsCounter.incrementAndGet();
    return currentTask.triggerTouchEvent(type, x, y, currentTimeNanos());
  }

  /**
   * Passes a Java KeyEvent from the surface to layoutlib.
   *
   * @return a future that is completed when layoutlib handled the key event
   */
  @NotNull
  public CompletableFuture<InteractionEventResult> triggerKeyEventAsync(@NotNull KeyEvent event) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("triggerKeyEventAsync after LayoutlibSceneManager has been disposed");
    }

    RenderTask currentTask = layoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    myInteractiveEventsCounter.incrementAndGet();
    return currentTask.triggerKeyEvent(event, currentTimeNanos());
  }

  /**
   * Executes the given {@link Runnable} callback synchronously with a 30ms timeout.
   */
  @Override
  public @NotNull CompletableFuture<Void> executeCallbacksAndRequestRender() {
    return executeCallbacksAsync().thenCompose(b -> requestRenderAsync());
  }

  @Override
  public boolean activate(@NotNull Object source) {
    boolean active = super.activate(source);
    layoutlibSceneRenderer.activate();
    if (active && updateAndRenderWhenActivated) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getModel().getProject());
      ResourceNotificationManager.ResourceVersion version =
        manager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), getModel().getConfiguration());
      if (!version.equals(layoutlibSceneRenderer.getRenderedVersion())) {
        getSceneRenderConfiguration().getNeedsInflation().set(true);
      }
      requestRenderAsync();
    }

    return active;
  }

  @Override
  public boolean deactivate(@NotNull Object source) {
    boolean deactivated = super.deactivate(source);
    if (deactivated) {
      layoutlibSceneRenderer.deactivate();
    }

    return deactivated;
  }

  /**
   * Resets the counter of user events received by this scene to 0.
   */
  @Override
  public void resetInteractiveEventsCounter() {
    myInteractiveEventsCounter.set(0);
  }

  /**
   * @return number of user touch or key events received by this scene since last reset.
   */
  @Override
  public int getInteractiveEventsCount() {
    return myInteractiveEventsCounter.get();
  }
}
