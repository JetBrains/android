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
import static com.intellij.util.ui.update.Update.HIGH_PRIORITY;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

import com.android.ide.common.rendering.api.ResourceReference;
import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.analytics.CommonUsageTracker;
import com.android.tools.idea.common.diagnostics.NlDiagnosticsManager;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.ModelListener;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionListener;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.TemporarySceneComponent;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.type.DesignerEditorFileType;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.RenderService;
import com.android.tools.idea.rendering.RenderSettings;
import com.android.tools.idea.rendering.RenderTask;
import com.android.tools.idea.rendering.parsers.LayoutPullParsers;
import com.android.tools.idea.rendering.parsers.TagSnapshot;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.constraint.targets.ConstraintDragDndTarget;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.type.MenuFileType;
import com.android.tools.idea.util.ListenerCollection;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.annotation.concurrent.GuardedBy;
import javax.swing.Timer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.ide.PooledThreadExecutor;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends SceneManager {

  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NlSceneDecoratorFactory();
  private final Supplier<RenderSettings> myRenderSettingsProvider;

  @Nullable private SceneView mySecondarySceneView;

  private int myDpi = 0;
  private final SelectionChangeListener mySelectionChangeListener = new SelectionChangeListener();
  private final ModelChangeListener myModelChangeListener = new ModelChangeListener();
  private final ConfigurationListener myConfigurationChangeListener = new ConfigurationChangeListener();
  private boolean myAreListenersRegistered;
  private final Object myProgressLock = new Object();
  @GuardedBy("myProgressLock")
  private AndroidPreviewProgressIndicator myCurrentIndicator;
  // Protects all accesses to the rendering queue reference
  private final Object myRenderingQueueLock = new Object();
  @GuardedBy("myRenderingQueueLock")
  private MergingUpdateQueue myRenderingQueue;
  private static final int RENDER_DELAY_MS = 10;
  private RenderTask myRenderTask;
  // Protects all accesses to the myRenderTask reference. RenderTask calls to render and layout do not need to be protected
  // since RenderTask is able to handle those safely.
  private final Object myRenderingTaskLock = new Object();
  private ResourceNotificationManager.ResourceVersion myRenderedVersion;
  // Protects all read/write accesses to the myRenderResult reference
  private final ReentrantReadWriteLock myRenderResultLock = new ReentrantReadWriteLock();
  @GuardedBy("myRenderResultLock")
  private RenderResult myRenderResult;
  // Variables to track previous values of the configuration bar for tracking purposes
  private String myPreviousDeviceName;
  private Locale myPreviousLocale;
  private String myPreviousVersion;
  private String myPreviousTheme;
  @AndroidCoordinate private static final int VISUAL_EMPTY_COMPONENT_SIZE = 1;
  private long myElapsedFrameTimeMs = -1;
  private final LinkedList<CompletableFuture<Void>> myRenderFutures = new LinkedList<>();
  private final Semaphore myUpdateHierarchyLock = new Semaphore(1);
  @NotNull private final ViewEditor myViewEditor;
  private final ListenerCollection<RenderListener> myRenderListeners = ListenerCollection.createWithDirectExecutor();
  /**
   * {@code Executor} to run the {@code Runnable} that disposes {@code RenderTask}s. This allows
   * {@code SyncLayoutlibSceneManager} to use a different strategy to dispose the tasks that does not involve using
   * pooled threads.
   */
  @NotNull private final Executor myRenderTaskDisposerExecutor;
  /**
   * True if we are currently in the middle of a render. This attribute is used to prevent listeners from triggering unnecessary renders.
   * If we try to schedule a new render while this is true, we simply re-use the last render in progress.
   */
  private final AtomicBoolean myIsCurrentlyRendering = new AtomicBoolean(false);

  /**
   * If true, the renders using this LayoutlibSceneManager will use transparent backgrounds
   */
  private boolean useTransparentRendering = false;

  /**
   * If true, the renders will use {@link com.android.ide.common.rendering.api.SessionParams.RenderingMode.SHRINK}
   */
  private boolean useShrinkRendering = false;

  protected static LayoutEditorRenderResult.Trigger getTriggerFromChangeType(@Nullable NlModel.ChangeType changeType) {
    if (changeType == null) {
      return null;
    }

    switch (changeType) {
      case RESOURCE_EDIT:
      case RESOURCE_CHANGED:
        return LayoutEditorRenderResult.Trigger.RESOURCE_CHANGE;
      case EDIT:
      case ADD_COMPONENTS:
      case DELETE:
      case DND_COMMIT:
      case DND_END:
      case DROP:
      case RESIZE_END:
      case RESIZE_COMMIT:
        return LayoutEditorRenderResult.Trigger.EDIT;
      case BUILD:
        return LayoutEditorRenderResult.Trigger.BUILD;
      case CONFIGURATION_CHANGE:
      case UPDATE_HIERARCHY:
        break;
    }

    return null;
  }

  protected LayoutlibSceneManager(@NotNull NlModel model,
                                  @NotNull DesignSurface designSurface,
                                  @NotNull Supplier<RenderSettings> settingsProvider,
                                  @NotNull Executor renderTaskDisposerExecutor) {
    super(model, designSurface, settingsProvider);
    myRenderSettingsProvider = settingsProvider;
    myRenderTaskDisposerExecutor = renderTaskDisposerExecutor;
    createSceneView();
    updateTrackingConfiguration();

    getDesignSurface().getSelectionModel().addListener(mySelectionChangeListener);

    Scene scene = getScene();

    myViewEditor = new ViewEditorImpl(model, scene);

    model.getConfiguration().addListener(myConfigurationChangeListener);

    List<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      NlComponent rootComponent = components.get(0).getRoot();
      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      List<SceneComponent> hierarchy = createHierarchy(rootComponent);
      SceneComponent root = hierarchy.isEmpty() ? null : hierarchy.get(0);
      updateFromComponent(root, new HashSet<>());
      scene.setRoot(root);
      updateTargets();
      scene.setAnimated(previous);
    }

    model.addListener(myModelChangeListener);
    myAreListenersRegistered = true;

    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
  }

  public LayoutlibSceneManager(@NotNull NlModel model,
                               @NotNull DesignSurface designSurface,
                               @NotNull Supplier<RenderSettings> renderSettingsProvider) {
    this(model, designSurface, renderSettingsProvider, PooledThreadExecutor.INSTANCE);
  }

  @NotNull
  public ViewEditor getViewEditor() {
    return myViewEditor;
  }

  @Override
  @NotNull
  public TemporarySceneComponent createTemporaryComponent(@NotNull NlComponent component) {
    Scene scene = getScene();

    assert scene.getRoot() != null;

    TemporarySceneComponent tempComponent = new TemporarySceneComponent(getScene(), component);
    tempComponent.setTargetProvider(sceneComponent -> ImmutableList.of(new ConstraintDragDndTarget()));
    scene.setAnimated(false);
    scene.getRoot().addChild(tempComponent);
    updateFromComponent(tempComponent);
    scene.setAnimated(true);

    return tempComponent;
  }

  @Override
  @NotNull
  public SceneDecoratorFactory getSceneDecoratorFactory() {
    return DECORATOR_FACTORY;
  }

  @Override
  public void dispose() {
    if (myAreListenersRegistered) {
      NlModel model = getModel();
      getDesignSurface().getSelectionModel().removeListener(mySelectionChangeListener);
      model.getConfiguration().removeListener(myConfigurationChangeListener);
      model.removeListener(myModelChangeListener);
      model.removeListener(myModelChangeListener);
    }
    myRenderListeners.clear();

    stopProgressIndicator();

    super.dispose();
    // dispose is called by the project close using the read lock. Invoke the render task dispose later without the lock.
    myRenderTaskDisposerExecutor.execute(() -> {
      synchronized (myRenderingTaskLock) {
        if (myRenderTask != null) {
          myRenderTask.dispose();
          myRenderTask = null;
        }
      }
      myRenderResultLock.writeLock().lock();
      try {
        if (myRenderResult != null) {
          myRenderResult.dispose();
        }
        myRenderResult = null;
      }
      finally {
        myRenderResultLock.writeLock().unlock();
      }
    });
  }

  private void stopProgressIndicator() {
    synchronized (myProgressLock) {
      if (myCurrentIndicator != null) {
        myCurrentIndicator.stop();
        myCurrentIndicator = null;
      }
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

    SceneMode mode = getDesignSurface().getSceneMode();

    SceneView primarySceneView = mode.createPrimarySceneView(getDesignSurface(), this);

    mySecondarySceneView = mode.createSecondarySceneView(getDesignSurface(), this);

    getDesignSurface().updateErrorDisplay();
    getDesignSurface().getLayeredPane().setPreferredSize(primarySceneView.getPreferredSize());

    return primarySceneView;
  }

  private SceneView createSceneViewsForMenu() {
    NlModel model = getModel();
    XmlTag tag = model.getFile().getRootTag();
    SceneView sceneView;

    // TODO See if there's a better way to trigger the NavigationViewSceneView. Perhaps examine the view objects?
    if (tag != null && Objects.equals(tag.getAttributeValue(ATTR_SHOW_IN, TOOLS_URI), NavigationViewSceneView.SHOW_IN_ATTRIBUTE_VALUE)) {
      sceneView = new NavigationViewSceneView(getDesignSurface(), this);
    }
    else {
      sceneView = new ScreenView(getDesignSurface(), this);
    }

    getDesignSurface().updateErrorDisplay();
    getDesignSurface().getLayeredPane().setPreferredSize(sceneView.getPreferredSize());
    return sceneView;
  }

  @NotNull
  @Override
  public ImmutableList<Layer> getLayers() {
    ImmutableList.Builder<Layer> builder = new ImmutableList.Builder<>();
    builder.addAll(super.getLayers());
    if (mySecondarySceneView != null) {
      builder.addAll(mySecondarySceneView.getLayers());
    }
    return builder.build();
  }

  @Nullable
  public SceneView getSecondarySceneView() {
    return mySecondarySceneView;
  }

  @Override
  protected void updateFromComponent(SceneComponent sceneComponent) {
    super.updateFromComponent(sceneComponent);
    NlComponent component = sceneComponent.getNlComponent();
    boolean animate = getScene().isAnimated() && !sceneComponent.hasNoDimension();
    if (animate) {
      long time = System.currentTimeMillis();
      sceneComponent.setPositionTarget(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getX(component)),
                                       Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getY(component)),
                                       time);
      sceneComponent.setSizeTarget(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getW(component)),
                                   Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getH(component)),
                                   time);
    }
    else {
      sceneComponent.setPosition(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getX(component)),
                                 Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getY(component)));
      sceneComponent.setSize(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getW(component)),
                             Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getH(component)));
    }
  }

  public void updateTargets() {
    SceneComponent root = getScene().getRoot();
    if (root != null) {
      updateTargetProviders(root);
      root.updateTargets();
    }
  }


  private static void updateTargetProviders(@NotNull SceneComponent component) {
    ViewHandler handler = NlComponentHelperKt.getViewHandler(component.getNlComponent());
    component.setTargetProvider(handler);

    for (SceneComponent child : component.getChildren()) {
      updateTargetProviders(child);
    }
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      NlDesignSurface surface = getDesignSurface();
      // TODO: this is the right behavior, but seems to unveil repaint issues. Turning it off for now.
      if (false && surface.getSceneMode() == SceneMode.BLUEPRINT_ONLY) {
        requestLayout(true);
      }
      else {
        requestRender(getTriggerFromChangeType(model.getLastChangeType()), false)
          .thenRunAsync(() ->
            // Selection change listener should run in UI thread not in the layoublib rendering thread. This avoids race condition.
            mySelectionChangeListener.selectionChanged(surface.getSelectionModel(), surface.getSelectionModel().getSelection())
          , EdtExecutorService.getInstance());
      }
    }

    @Override
    public void modelChanged(@NotNull NlModel model) {
      requestModelUpdate();
      ApplicationManager.getApplication().invokeLater(() -> {
        if (!Disposer.isDisposed(LayoutlibSceneManager.this)) {
          mySelectionChangeListener
            .selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
        }
      });
    }

    @Override
    public void modelChangedOnLayout(@NotNull NlModel model, boolean animate) {
      UIUtil.invokeLaterIfNeeded(() -> {
        if (!Disposer.isDisposed(LayoutlibSceneManager.this)) {
          boolean previous = getScene().isAnimated();
          getScene().setAnimated(animate);
          update();
          getScene().setAnimated(previous);
        }
      });
    }

    @Override
    public void modelActivated(@NotNull NlModel model) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(getModel().getProject());
      ResourceNotificationManager.ResourceVersion version =
        manager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), getModel().getConfiguration());
      if (!version.equals(myRenderedVersion)) {
        requestModelUpdate();
        model.updateTheme();
      }
    }

    @Override
    public void modelDeactivated(@NotNull NlModel model) {
      synchronized (myRenderingQueueLock) {
        if (myRenderingQueue != null) {
          myRenderingQueue.cancelAllUpdates();
        }
      }
    }

    @Override
    public void modelLiveUpdate(@NotNull NlModel model, boolean animate) {
      NlDesignSurface surface = getDesignSurface();

      /*
      We only need to render if we are not in Blueprint mode. If we are in blueprint mode only, we only need a layout.
       */
      boolean needsRender = (surface.getSceneMode() != SceneMode.BLUEPRINT_ONLY);
      if (needsRender) {
        requestLayoutAndRender(animate);
      }
      else {
        requestLayout(animate);
      }
    }
  }

  private class SelectionChangeListener implements SelectionListener {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      updateTargets();
      Scene scene = getScene();
      scene.needsRebuildList();
      scene.repaint();
    }
  }

  /**
   * Adds a new render request to the queue.
   * @param trigger build trigger for reporting purposes
   * @param forceInflate if true, the layout will be re-inflated
   * @return {@link CompletableFuture} that will be completed once the render has been done.
   */
  @NotNull
  private CompletableFuture<Void> requestRender(@Nullable LayoutEditorRenderResult.Trigger trigger, boolean forceInflate) {
    CompletableFuture<Void> callback = new CompletableFuture<>();
    synchronized (myRenderFutures) {
      myRenderFutures.add(callback);
    }

    if (myIsCurrentlyRendering.get()) {
      return callback;
    }

    // This update is low priority so the model updates take precedence
    getRenderingQueue().queue(new Update("model.render", LOW_PRIORITY) {
      @Override
      public void run() {
        render(trigger, forceInflate);
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    });

    return callback;
  }

  private CompletableFuture<Void> requestRender(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    return requestRender(trigger, false);
  }

  private class ConfigurationChangeListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
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
  public CompletableFuture<Void> requestRender() {
    return requestRender(getTriggerFromChangeType(getModel().getLastChangeType()), false);
  }

  /**
   * Similar to {@link #requestRender()} but it will be logged as a user initiated action. This is
   * not exposed at SceneManager level since it only makes sense for the Layout editor.
   */
  @NotNull
  public CompletableFuture<Void> requestUserInitiatedRender() {
    return requestRender(LayoutEditorRenderResult.Trigger.USER, true);
  }

  @Override
  public void requestLayoutAndRender(boolean animate) {
    // Don't render if we're just showing the blueprint
    if (getDesignSurface().getSceneMode() == SceneMode.BLUEPRINT_ONLY) {
      requestLayout(animate);
      return;
    }

    doRequestLayoutAndRender(animate);
  }

  void doRequestLayoutAndRender(boolean animate) {
    requestRender(getTriggerFromChangeType(getModel().getLastChangeType()), false)
      .whenCompleteAsync((result, ex) -> notifyListenersModelLayoutComplete(animate), PooledThreadExecutor.INSTANCE);
  }

  /**
   * Asynchronously inflates the model and updates the view hierarchy
   */
  protected void requestModelUpdate() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (myProgressLock) {
      if (myCurrentIndicator == null) {
        myCurrentIndicator = new AndroidPreviewProgressIndicator();
        myCurrentIndicator.start();
      }
    }

    getRenderingQueue().queue(new Update("model.update", HIGH_PRIORITY) {
      @Override
      public void run() {
        NlModel model = getModel();
        Project project = model.getModule().getProject();
        if (!project.isOpen()) {
          return;
        }
        DumbService.getInstance(project).runWhenSmart(() -> {
          if (model.getVirtualFile().isValid() && !model.getFacet().isDisposed()) {
            updateModel()
              .whenComplete((result, ex) -> stopProgressIndicator());
          }
          else {
            stopProgressIndicator();
          }
        });
      }

      @Override
      public boolean canEat(Update update) {
        return equals(update);
      }
    });
  }

  @NotNull
  public MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", RENDER_DELAY_MS, true, null, this, null,
                                                  Alarm.ThreadToUse.POOLED_THREAD);
        myRenderingQueue.setRestartTimerOnAdd(true);
      }
      return myRenderingQueue;
    }
  }

  /**
   * Whether we should render just the viewport
   */
  private static boolean ourRenderViewPort;

  public static void setRenderViewPort(boolean state) {
    ourRenderViewPort = state;
  }

  public static boolean isRenderViewPort() {
    return ourRenderViewPort;
  }

  public void setTransparentRendering(boolean enabled) {
    useTransparentRendering = enabled;
  }

  public void setShrinkRendering(boolean enabled) {
    useShrinkRendering = enabled;
  }

  @Override
  @NotNull
  public CompletableFuture<Void> requestLayout(boolean animate) {
    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return CompletableFuture.completedFuture(null);
      }
      return myRenderTask.layout()
        .thenAccept(result -> {
          if (result != null) {
            updateHierarchy(result);
            notifyListenersModelLayoutComplete(animate);
          }
        });
    }
  }

  /**
   * Request a layout pass
   *
   * @param animate if true, the resulting layout should be animated
   */
  @Override
  public void layout(boolean animate) {
    try {
      requestLayout(animate).get(2, TimeUnit.SECONDS);
    }
    catch (InterruptedException | ExecutionException | TimeoutException e) {
      Logger.getInstance(LayoutlibSceneManager.class).warn("Unable to run layout()", e);
    }
  }

  @Nullable
  public RenderResult getRenderResult() {
    myRenderResultLock.readLock().lock();
    try {
      return myRenderResult;
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  @Override
  @NotNull
  public Map<Object, Map<ResourceReference, ResourceValue>> getDefaultProperties() {
    myRenderResultLock.readLock().lock();
    try {
      if (myRenderResult == null) {
        return Collections.emptyMap();
      }
      return myRenderResult.getDefaultProperties();
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  @Override
  @NotNull
  public Map<Object, String> getDefaultStyles() {
    myRenderResultLock.readLock().lock();
    try {
      if (myRenderResult == null) {
        return Collections.emptyMap();
      }
      return myRenderResult.getDefaultStyles();
    }
    finally {
      myRenderResultLock.readLock().unlock();
    }
  }

  private void updateHierarchy(@Nullable RenderResult result) {
    try {
      myUpdateHierarchyLock.acquire();
      try {
        if (result == null || !result.getRenderResult().isSuccess()) {
          updateHierarchy(Collections.emptyList(), getModel());
        }
        else {
          updateHierarchy(getRootViews(result), getModel());
        }
      } finally {
        myUpdateHierarchyLock.release();
      }
      getModel().checkStructure();
    }
    catch (InterruptedException ignored) {
    }
  }

  @NotNull
  private List<ViewInfo> getRootViews(@NotNull RenderResult result) {
    return getModel().getType() == MenuFileType.INSTANCE ? result.getSystemRootViews() : result.getRootViews();
  }

  @VisibleForTesting
  public static void updateHierarchy(@NotNull XmlTag rootTag, @NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    model.syncWithPsi(rootTag, rootViews.stream().map(ViewInfoTagSnapshotNode::new).collect(Collectors.toList()));
    updateBounds(rootViews, model);
  }

  @VisibleForTesting
  public static void updateHierarchy(@NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    XmlTag root = getRootTag(model);
    if (root != null) {
      updateHierarchy(root, rootViews, model);
    }
  }

  // Get the root tag of the xml file associated with the specified model.
  // Since this code may be called on a non UI thread be extra careful about expired objects.
  @Nullable
  private static XmlTag getRootTag(@NotNull NlModel model) {
    if (Disposer.isDisposed(model)) {
      return null;
    }
    return AndroidPsiUtils.getRootTagSafely(model.getFile());
  }

  /**
   * Synchronously inflates the model and updates the view hierarchy
   *
   * @param force forces the model to be re-inflated even if a previous version was already inflated
   * @returns whether the model was inflated in this call or not
   */
  private CompletableFuture<Boolean> inflate(boolean force) {
    Configuration configuration = getModel().getConfiguration();

    Project project = getModel().getProject();
    if (project.isDisposed()) {
      return CompletableFuture.completedFuture(false);
    }

    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(project);

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParsers.saveFileIfNecessary(getModel().getFile());

    synchronized (myRenderingTaskLock) {
      if (myRenderTask != null && !force) {
        // No need to inflate
        return CompletableFuture.completedFuture(false);
      }
    }

    // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
    // external changes
    AndroidFacet facet = getModel().getFacet();
    myRenderedVersion = resourceNotificationManager.getCurrentVersion(facet, getModel().getFile(), configuration);

    RenderService renderService = RenderService.getInstance(getModel().getProject());
    RenderService.RenderTaskBuilder renderTaskBuilder = renderService.taskBuilder(facet, configuration)
      .withPsiFile(getModel().getFile());
    return setupRenderTaskBuilder(renderTaskBuilder).build()
      .thenCompose(newTask -> {
        if (newTask != null) {
          newTask.getLayoutlibCallback()
            .setAdaptiveIconMaskPath(getDesignSurface().getAdaptiveIconShape().getPathDescription());
          return newTask.inflate().whenComplete((result, exception) -> {
            if (exception != null) {
              Logger.getInstance(LayoutlibSceneManager.class).warn(exception);
            }

            if (result == null || !result.getRenderResult().isSuccess()) {
              newTask.dispose();
            }
            else {
              // Update myRenderTask with the new task
              synchronized (myRenderingTaskLock) {
                if (myRenderTask != null && !myRenderTask.isDisposed()) {
                  myRenderTask.dispose();
                }
                myRenderTask = newTask;
              }
            }
          })
            .thenApply(result -> result != null ? result : RenderResult.createBlank(getModel().getFile()))
            .thenApply(result -> {
              if (project.isDisposed()) {
                return false;
              }

              updateHierarchy(result);
              myRenderResultLock.writeLock().lock();
              try {
                updateCachedRenderResult(result);
              }
              finally {
                myRenderResultLock.writeLock().unlock();
              }

              return true;
            });
        }
        else {
          synchronized (myRenderingTaskLock) {
            if (myRenderTask != null && !myRenderTask.isDisposed()) {
              myRenderTask.dispose();
            }
          }
        }

        return CompletableFuture.completedFuture(false);
      });
  }

  @GuardedBy("myRenderResultLock")
  private void updateCachedRenderResult(RenderResult result) {
    if (myRenderResult != null && myRenderResult != result) {
      myRenderResult.dispose();
    }
    myRenderResult = result;
  }

  @VisibleForTesting
  @NotNull
  protected RenderService.RenderTaskBuilder setupRenderTaskBuilder(@NotNull RenderService.RenderTaskBuilder taskBuilder) {
    RenderSettings settings = myRenderSettingsProvider.get();
    if (!settings.getUseLiveRendering()) {
      // When we are not using live rendering, we do not need the pool
      taskBuilder.disableImagePool();
    }
    if (settings.getQuality() < 1f) {
      taskBuilder.withDownscaleFactor(settings.getQuality());
    }

    if (!settings.getShowDecorations()) {
      taskBuilder.disableDecorations();
    }

    if (useShrinkRendering) {
      taskBuilder.withRenderingMode(SessionParams.RenderingMode.SHRINK);
    }

    if (useTransparentRendering) {
      taskBuilder.useTransparentBackground();
    }

    return taskBuilder;
  }

  /**
   * Asynchronously update the model. This will inflate the layout and notify the listeners using
   * {@link ModelListener#modelDerivedDataChanged(NlModel)}.
   */
  protected CompletableFuture<Void> updateModel() {
    return inflate(true)
      .whenCompleteAsync((result, exception) -> notifyListenersModelUpdateComplete(), PooledThreadExecutor.INSTANCE)
      .thenApply(result -> null);
  }

  protected void notifyListenersModelLayoutComplete(boolean animate) {
    getModel().notifyListenersModelLayoutComplete(animate);
  }

  protected void notifyListenersModelUpdateComplete() {
    getModel().notifyListenersModelUpdateComplete();
  }

  private void logConfigurationChange(@NotNull DesignSurface surface) {
    Configuration configuration = getModel().getConfiguration();

    if (getModel().getConfigurationModificationCount() != configuration.getModificationCount()) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      NlAnalyticsManager analyticsManager = ((NlDesignSurface)surface).getAnalyticsManager();
      if (!StringUtil.equals(configuration.getTheme(), myPreviousTheme)) {
        myPreviousTheme = configuration.getTheme();
        analyticsManager.trackThemeChange();
      }
      else if (configuration.getTarget() != null && !StringUtil.equals(configuration.getTarget().getVersionName(), myPreviousVersion)) {
        myPreviousVersion = configuration.getTarget().getVersionName();
        analyticsManager.trackApiLevelChange();
      }
      else if (!configuration.getLocale().equals(myPreviousLocale)) {
        myPreviousLocale = configuration.getLocale();
        analyticsManager.trackLanguageChange();
      }
      else if (configuration.getDevice() != null && !StringUtil.equals(configuration.getDevice().getDisplayName(), myPreviousDeviceName)) {
        myPreviousDeviceName = configuration.getDevice().getDisplayName();
        analyticsManager.trackDeviceChange();
      }
    }
  }

  /**
   * Renders the current model asynchronously. Once the render is complete, the render callbacks will be called.
   * <p/>
   * If the layout hasn't been inflated before, this call will inflate the layout before rendering.
   */
  @NotNull
  protected CompletableFuture<RenderResult> render(@Nullable LayoutEditorRenderResult.Trigger trigger, boolean forceInflate) {
    myIsCurrentlyRendering.set(true);
    try {
      DesignSurface surface = getDesignSurface();
      logConfigurationChange(surface);
      getModel().resetLastChange();

      long renderStartTimeMs = System.currentTimeMillis();
      return renderImpl(forceInflate)
        .thenApply(result -> {
          if (result == null) {
            completeRender();
            return null;
          }

          myRenderResultLock.writeLock().lock();
          try {
            updateCachedRenderResult(result);
            // TODO(nro): this may not be ideal -- forcing direct results immediately
            if (!Disposer.isDisposed(this)) {
              update();
            }
            // Downgrade the write lock to read lock
            myRenderResultLock.readLock().lock();
          }
          finally {
            myRenderResultLock.writeLock().unlock();
          }
          try {
            long renderTimeMs = System.currentTimeMillis() - renderStartTimeMs;
            NlDiagnosticsManager.getWriteInstance(surface).recordRender(renderTimeMs,
                                                                        myRenderResult.getRenderedImage().getWidth() * myRenderResult.getRenderedImage().getHeight() * 4);
            CommonUsageTracker.Companion.getInstance(surface).logRenderResult(trigger, myRenderResult, renderTimeMs);
          }
          finally {
            myRenderResultLock.readLock().unlock();
          }

          UIUtil.invokeLaterIfNeeded(() -> {
            if (!Disposer.isDisposed(this)) {
              update();
            }
          });
          fireRenderListeners();
          completeRender();

          return result;
        });
    }
    catch (Throwable e) {
      if (!getModel().getFacet().isDisposed()) {
        completeRender();
        throw e;
      }
    }
    completeRender();
    return CompletableFuture.completedFuture(null);
  }

  /**
   * Completes all the futures created by {@link #requestRender()} and signals the current render as finished by
   * setting {@link #myIsCurrentlyRendering} to false.
   */
  private void completeRender() {
    ImmutableList<CompletableFuture<Void>> callbacks;
    synchronized (myRenderFutures) {
      callbacks = ImmutableList.copyOf(myRenderFutures);
      myRenderFutures.clear();
    }
    callbacks.forEach(callback -> callback.complete(null));
    myIsCurrentlyRendering.set(false);
  }

  @NotNull
  private CompletableFuture<RenderResult> renderImpl(boolean forceInflate) {
    return inflate(forceInflate)
      .whenCompleteAsync((result, ex) -> {
        if (result) {
          notifyListenersModelUpdateComplete();
        }
      }, PooledThreadExecutor.INSTANCE)
      .thenCompose(inflated -> {
        long elapsedFrameTimeMs = myElapsedFrameTimeMs;

        synchronized (myRenderingTaskLock) {
          if (myRenderTask == null) {
            getDesignSurface().updateErrorDisplay();
            return CompletableFuture.completedFuture(null);
          }
          if (elapsedFrameTimeMs != -1) {
            myRenderTask.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(elapsedFrameTimeMs));
          }
          return myRenderTask.render().thenApply(result -> {
            // When the layout was inflated in this same call, we do not have to update the hierarchy again
            if (result != null && !inflated) {
              updateHierarchy(result);
            }

            return result;
          });
        }
      });
  }

  public void setElapsedFrameTimeMs(long ms) {
    myElapsedFrameTimeMs = ms;
  }

  /**
   * Updates the saved values that are used to log user changes to the configuration toolbar.
   */
  private void updateTrackingConfiguration() {
    Configuration configuration = getModel().getConfiguration();
    myPreviousDeviceName = configuration.getDevice() != null ? configuration.getDevice().getDisplayName() : null;
    myPreviousVersion = configuration.getTarget() != null ? configuration.getTarget().getVersionName() : null;
    myPreviousLocale = configuration.getLocale();
    myPreviousTheme = configuration.getTheme();
  }

  private class AndroidPreviewProgressIndicator extends ProgressIndicatorBase {
    private final Object myLock = new Object();

    @Override
    public void start() {
      super.start();
      UIUtil.invokeLaterIfNeeded(() -> {
        final Timer timer = UIUtil.createNamedTimer("Android rendering progress timer", 0, event -> {
          synchronized (myLock) {
            if (isRunning()) {
              getDesignSurface().registerIndicator(this);
            }
          }
        });
        timer.setRepeats(false);
        timer.start();
      });
    }

    @Override
    public void stop() {
      synchronized (myLock) {
        super.stop();
        ApplicationManager.getApplication().invokeLater(() -> getDesignSurface().unregisterIndicator(this));
      }
    }
  }

  /**
   * A TagSnapshot tree that mirrors the ViewInfo tree.
   */
  private static class ViewInfoTagSnapshotNode implements NlModel.TagSnapshotTreeNode {

    private final ViewInfo myViewInfo;

    public ViewInfoTagSnapshotNode(ViewInfo info) {
      myViewInfo = info;
    }

    @Nullable
    @Override
    public TagSnapshot getTagSnapshot() {
      Object result = myViewInfo.getCookie();
      return result instanceof TagSnapshot ? (TagSnapshot)result : null;
    }

    @NotNull
    @Override
    public List<NlModel.TagSnapshotTreeNode> getChildren() {
      return myViewInfo.getChildren().stream().map(ViewInfoTagSnapshotNode::new).collect(Collectors.toList());
    }
  }

  private static void clearDerivedData(@NotNull NlComponent component) {
    NlComponentHelperKt.setBounds(component, 0, 0, -1, -1); // -1: not initialized
    NlComponentHelperKt.setViewInfo(component, null);
  }

  // TODO: we shouldn't be going back in and modifying NlComponents here
  private static void updateBounds(@NotNull List<ViewInfo> rootViews, @NotNull NlModel model) {
    model.flattenComponents().forEach(LayoutlibSceneManager::clearDerivedData);
    Map<TagSnapshot, NlComponent> snapshotToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getSnapshot, Function.identity(), (n1, n2) -> n1));
    Map<XmlTag, NlComponent> tagToComponent =
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getTagDeprecated, Function.identity()));

    // Update the bounds. This is based on the ViewInfo instances.
    for (ViewInfo view : rootViews) {
      updateBounds(view, 0, 0, snapshotToComponent, tagToComponent);
    }

    ImmutableList<NlComponent> components = model.getComponents();
    if (!rootViews.isEmpty() && !components.isEmpty()) {
      // Finally, fix up bounds: ensure that all components not found in the view
      // info hierarchy inherit position from parent
      fixBounds(components.get(0));
    }
  }

  private static void fixBounds(@NotNull NlComponent root) {
    boolean computeBounds = false;
    if (NlComponentHelperKt.getW(root) == -1 && NlComponentHelperKt.getH(root) == -1) { // -1: not initialized
      computeBounds = true;

      // Look at parent instead
      NlComponent parent = root.getParent();
      if (parent != null && NlComponentHelperKt.getW(parent) >= 0) {
        NlComponentHelperKt.setBounds(root, NlComponentHelperKt.getX(parent), NlComponentHelperKt.getY(parent), 0, 0);
      }
    }

    List<NlComponent> children = root.getChildren();
    if (!children.isEmpty()) {
      for (NlComponent child : children) {
        fixBounds(child);
      }

      if (computeBounds) {
        Rectangle rectangle = new Rectangle(NlComponentHelperKt.getX(root), NlComponentHelperKt.getY(root), NlComponentHelperKt.getW(root),
                                            NlComponentHelperKt.getH(root));
        // Grow bounds to include child bounds
        for (NlComponent child : children) {
          rectangle = rectangle.union(new Rectangle(NlComponentHelperKt.getX(child), NlComponentHelperKt.getY(child),
                                                    NlComponentHelperKt.getW(child), NlComponentHelperKt.getH(child)));
        }

        NlComponentHelperKt.setBounds(root, rectangle.x, rectangle.y, rectangle.width, rectangle.height);
      }
    }
  }

  private static void updateBounds(@NotNull ViewInfo view,
                                   @AndroidCoordinate int parentX,
                                   @AndroidCoordinate int parentY,
                                   Map<TagSnapshot, NlComponent> snapshotToComponent,
                                   Map<XmlTag, NlComponent> tagToComponent) {
    ViewInfo bounds = RenderService.getSafeBounds(view);
    Object cookie = view.getCookie();
    NlComponent component;
    if (cookie != null) {
      if (cookie instanceof TagSnapshot) {
        TagSnapshot snapshot = (TagSnapshot)cookie;
        component = snapshotToComponent.get(snapshot);
        if (component == null) {
          component = tagToComponent.get(snapshot.tag);
        }
        if (component != null && NlComponentHelperKt.getViewInfo(component) == null) {
          NlComponentHelperKt.setViewInfo(component, view);
          int left = parentX + bounds.getLeft();
          int top = parentY + bounds.getTop();
          int width = bounds.getRight() - bounds.getLeft();
          int height = bounds.getBottom() - bounds.getTop();

          NlComponentHelperKt.setBounds(component, left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE),
                                        Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));
        }
      }
    }
    parentX += bounds.getLeft();
    parentY += bounds.getTop();

    for (ViewInfo child : view.getChildren()) {
      updateBounds(child, parentX, parentY, snapshotToComponent, tagToComponent);
    }
  }

  protected void fireRenderListeners() {
    myRenderListeners.forEach(RenderListener::onRenderCompleted);
  }

  public void addRenderListener(@NotNull RenderListener listener) {
    myRenderListeners.add(listener);
  }

  public void removeRenderListener(@NotNull RenderListener listener) {
    myRenderListeners.remove(listener);
  }
}
