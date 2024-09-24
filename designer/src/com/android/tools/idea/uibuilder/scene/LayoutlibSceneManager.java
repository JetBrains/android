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
import static com.android.resources.Density.DEFAULT_DENSITY;
import static com.android.tools.idea.common.surface.ShapePolicyKt.SQUARE_SHAPE_POLICY;
import static com.android.tools.idea.uibuilder.scene.LayoutlibSceneManagerUtilsKt.getTriggerFromChangeType;
import static com.android.tools.idea.uibuilder.scene.LayoutlibSceneManagerUtilsKt.updateTargetProviders;

import com.android.ide.common.rendering.api.RenderSession;
import com.android.tools.configurations.ConfigurationListener;
import com.android.sdklib.AndroidCoordinate;
import com.android.sdklib.AndroidDpCoordinate;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneComponentHierarchyProvider;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.common.surface.LayoutScannerEnabled;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ScreenViewLayer;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintMode;
import com.android.tools.rendering.ExecuteCallbacksResult;
import com.android.tools.rendering.InteractionEventResult;
import com.android.tools.rendering.RenderAsyncActionExecutor;
import com.android.tools.rendering.RenderResult;
import com.android.tools.rendering.RenderTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends SceneManager implements InteractiveSceneManager {
  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NlSceneDecoratorFactory();

  private int myDpi = 0;
  private final SelectionChangeListener mySelectionChangeListener = new SelectionChangeListener();
  private final ModelChangeListener myModelChangeListener = new ModelChangeListener();
  private final ConfigurationListener myConfigurationChangeListener = new ConfigurationChangeListener();
  private final boolean myAreListenersRegistered;
  // Variables to track previous values of the configuration bar for tracking purposes
  private final AtomicInteger myConfigurationUpdatedFlags = new AtomicInteger(0);
  @NotNull private final ViewEditor myViewEditor;

  /**
   * Helper class in charge of some render related responsibilities
   */
  // TODO(b/335424569): add a better explanation after moving more responsibilities to
  //  LayoutlibSceneRenderer
  private final LayoutlibSceneRenderer myLayoutlibSceneRenderer;

  /**
   * If true, listen the resource change.
   */
  private boolean myListenResourceChange = true;

  /**
   * If true, automatically update (if needed) and re-render when being activated. Which happens after {@link #activate(Object)} is called.
   * Note that if it is activated already, then it will not re-render.
   */
  private boolean myUpdateAndRenderWhenActivated = true;

  private final AtomicBoolean isDisposed = new AtomicBoolean(false);

  /** Counter for user events during the interactive session. */
  private final AtomicInteger myInteractiveEventsCounter = new AtomicInteger(0);

  @NotNull
  private VisualLintMode myVisualLintMode = VisualLintMode.DISABLED;

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
    super(model, designSurface, sceneComponentProvider);
    myLayoutlibSceneRenderer = new LayoutlibSceneRenderer(this, renderTaskDisposerExecutor, model, (NlDesignSurface) designSurface, layoutScannerConfig);
    createSceneView();

    getDesignSurface().getSelectionModel().addListener(mySelectionChangeListener);

    Scene scene = getScene();

    myViewEditor = new ViewEditorImpl(model, scene);

    model.getConfiguration().addListener(myConfigurationChangeListener);

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

    model.addListener(myModelChangeListener);
    myAreListenersRegistered = true;

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

  @Override
  @NotNull
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    return DECORATOR_FACTORY;
  }

  /**
   * In the layout editor, Scene uses {@link AndroidDpCoordinate}s whereas rendering is done in (zoomed and offset)
   * {@link AndroidCoordinate}s. The scaling factor between them is the ratio of the screen density to the standard density (160).
   */
  @Override
  public float getSceneScalingFactor() {
    return getModel().getConfiguration().getDensity().getDpiValue() / (float)DEFAULT_DENSITY;
  }

  @NotNull
  public LayoutlibSceneRenderConfiguration getSceneRenderConfiguration() {
    return myLayoutlibSceneRenderer.getSceneRenderConfiguration();
  }

  public void setVisualLintMode(@NotNull VisualLintMode visualLintMode) {
    myVisualLintMode = visualLintMode;
  }

  @NotNull
  public VisualLintMode getVisualLintMode() {
    return myVisualLintMode;
  }

  @Override
  public void dispose() {
    if (isDisposed.getAndSet(true)) {
      return;
    }

    try {
      if (myAreListenersRegistered) {
        NlModel model = getModel();
        getDesignSurface().getSelectionModel().removeListener(mySelectionChangeListener);
        model.getConfiguration().removeListener(myConfigurationChangeListener);
        model.removeListener(myModelChangeListener);
      }
    }
    finally {
      super.dispose();
    }
  }

  @NotNull
  @Override
  protected NlDesignSurface getDesignSurface() {
    return (NlDesignSurface) super.getDesignSurface();
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
    mySecondarySceneView = getDesignSurface().getScreenViewProvider().createSecondarySceneView(getDesignSurface(), this);

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

  public void updateTargets() {
    Runnable updateAgain = this::updateTargets;
    SceneComponent root = getScene().getRoot();
    if (root != null) {
      updateTargetProviders(root, updateAgain);
      root.updateTargets();
    }
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      // After the model derived data is changed, we need to update the selection in Edt thread.
      // Changing selection should run in UI thread to avoid race condition.
      NlDesignSurface surface = getDesignSurface();
      CompletableFuture.runAsync(() -> {
        // Ensure the new derived that is passed to the Scene components hierarchy
        if (!isDisposed.get()) {
          update();
        }

        // Selection change listener should run in UI thread not in the layoublib rendering thread. This avoids race condition.
        mySelectionChangeListener.selectionChanged(surface.getSelectionModel(), surface.getSelectionModel().getSelection());
      }, EdtExecutorService.getInstance());
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      NlDesignSurface surface = getDesignSurface();
      // The structure might have changed, force a re-inflate
      getSceneRenderConfiguration().getNeedsInflation().set(true);
      // If the update is reversed (namely, we update the View hierarchy from the component hierarchy because information about scrolling is
      // located in the component hierarchy and is lost in the view hierarchy) we need to run render again to propagate the change
      // (re-layout) in the scrolling values to the View hierarchy (position, children etc.) and render the updated result.
      myLayoutlibSceneRenderer.getSceneRenderConfiguration().getDoubleRenderIfNeeded().set(true);
      requestRenderAsync(getTriggerFromChangeType(model.getLastChangeType()))
        .thenRunAsync(() ->
                        mySelectionChangeListener.selectionChanged(surface.getSelectionModel(), surface.getSelectionModel().getSelection())
          , EdtExecutorService.getInstance());
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!isDisposed.get()) {
          boolean previous = getScene().isAnimated();
          getScene().setAnimated(animate);
          update();
          getScene().setAnimated(previous);
        }
      });
    }

    @Override
    public void modelLiveUpdate(@NotNull NlModel model) {
      requestRenderAsync();
    }
  }

  private class SelectionChangeListener implements SelectionListener {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      updateTargets();
      getScene().needsRebuildList();
    }
  }

  /**
   * Adds a new render request to the queue.
   * @param trigger build trigger for reporting purposes
   * @return {@link CompletableFuture} that will be completed once the render has been done.
   */
  @NotNull
  protected CompletableFuture<Void> requestRenderAsync(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("requestRender after LayoutlibSceneManager has been disposed");
      return CompletableFuture.completedFuture(null);
    }

    NlDesignSurface surface = getDesignSurface();
    logConfigurationChange(surface);
    getModel().resetLastChange();

    return myLayoutlibSceneRenderer.renderAsync(trigger).thenCompose((unit) -> CompletableFuture.completedFuture(null));
  }

  private class ConfigurationChangeListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
      myConfigurationUpdatedFlags.getAndUpdate((value) -> value |= flags);
      if ((flags & CFG_DEVICE) != 0) {
        int newDpi = getModel().getConfiguration().getDensity().getDpiValue();
        if (myDpi != newDpi) {
          myDpi = newDpi;
          // Update from the model to update the dpi
          LayoutlibSceneManager.this.update();
        }
      }
      return true;
    }
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestRenderAsync() {
    return requestRenderAsync(getTriggerFromChangeType(getModel().getLastChangeType()));
  }

  /**
   * If true, register the {@link com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener} which calls
   * {@link #resourcesChanged(ImmutableSet)} when any resource is changed.
   * By default, it is enabled.
   */
  public void setListenResourceChange(boolean enabled) {
    myListenResourceChange = enabled;
  }

  public void setUpdateAndRenderWhenActivated(boolean enable) {
    myUpdateAndRenderWhenActivated = enable;
  }

  public float getLastRenderQuality() {
    return myLayoutlibSceneRenderer.getLastRenderQuality();
  }

  public void invalidateCachedResponse() {
    myLayoutlibSceneRenderer.setRenderResult(null);
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestLayoutAsync(boolean animate) {
    if (isDisposed.get()) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("requestLayout after LayoutlibSceneManager has been disposed");
    }

    RenderTask currentTask = myLayoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    return currentTask.layout()
      .thenAccept(result -> {
        if (result != null && !isDisposed.get()) {
          myLayoutlibSceneRenderer.updateHierarchy(result);
          notifyListenersModelLayoutComplete(animate);
        }
      });
  }

  @Nullable
  public RenderResult getRenderResult() {
    return myLayoutlibSceneRenderer.getRenderResult();
  }

  private void notifyListenersModelLayoutComplete(boolean animate) {
    getModel().notifyListenersModelChangedOnLayout(animate);
  }

  private void logConfigurationChange(@NotNull DesignSurface<?> surface) {
    int flags = myConfigurationUpdatedFlags.getAndSet(0);  // Get and reset the saved flags
    if (flags != 0) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      NlAnalyticsManager analyticsManager = (NlAnalyticsManager)(surface.getAnalyticsManager());

      if ((flags & ConfigurationListener.CFG_THEME) != 0) {
        analyticsManager.trackThemeChange();
      }
      if ((flags & ConfigurationListener.CFG_TARGET) != 0) {
        analyticsManager.trackApiLevelChange();
      }
      if ((flags & ConfigurationListener.CFG_LOCALE) != 0) {
        analyticsManager.trackLanguageChange();
      }
      if ((flags & ConfigurationListener.CFG_DEVICE) != 0) {
        analyticsManager.trackDeviceChange();
      }
    }
  }

  /**
   * Returns if there are any pending render requests.
   */
  @TestOnly
  public boolean isRendering() {
    return myLayoutlibSceneRenderer.isRendering();
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
    RenderTask currentTask = myLayoutlibSceneRenderer.getRenderTask();
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
    RenderTask currentTask = myLayoutlibSceneRenderer.getRenderTask();
    if (currentTask == null) {
      return CompletableFuture.completedFuture(null);
    }
    return currentTask.runAsyncRenderActionWithSession(block, timeout, timeUnit);
  }

  private long currentTimeNanos() {
    return myLayoutlibSceneRenderer.getSessionClock().getTimeNanos();
  }

  /**
   * Pauses session clock, so that session time stops advancing.
   */
  @Override
  public void pauseSessionClock() {
    myLayoutlibSceneRenderer.getSessionClock().pause();
  }

  /**
   * Resumes session clock, so that session time keeps advancing.
   */
  @Override
  public void resumeSessionClock() {
    myLayoutlibSceneRenderer.getSessionClock().resume();
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

    RenderTask currentTask = myLayoutlibSceneRenderer.getRenderTask();
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

    RenderTask currentTask = myLayoutlibSceneRenderer.getRenderTask();
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
    myLayoutlibSceneRenderer.activate();
    if (active && myUpdateAndRenderWhenActivated) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getModel().getProject());
      ResourceNotificationManager.ResourceVersion version =
        manager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), getModel().getConfiguration());
      if (!version.equals(myLayoutlibSceneRenderer.getRenderedVersion())) {
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
      myLayoutlibSceneRenderer.deactivate();
    }

    return deactivated;
  }

  @Override
  public void resourcesChanged(@NotNull ImmutableSet<ResourceNotificationManager.Reason> reasons) {
    if (myListenResourceChange) {
      super.resourcesChanged(reasons);
    }
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
