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

import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.analytics.NlUsageTrackerManager;
import com.android.tools.idea.common.model.*;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.scene.decorator.SceneDecoratorFactory;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.uibuilder.api.ViewGroupHandler;
import com.android.tools.idea.uibuilder.api.ViewHandler;
import com.android.tools.idea.uibuilder.menu.NavigationViewSceneView;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.scene.decorator.NlSceneDecoratorFactory;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.util.PropertiesMap;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.google.wireless.android.sdk.stats.LayoutEditorRenderResult;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.Timer;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.android.SdkConstants.ATTR_SHOW_IN;
import static com.android.SdkConstants.TOOLS_URI;
import static com.intellij.util.ui.update.Update.HIGH_PRIORITY;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

/**
 * {@link SceneManager} that creates a Scene from an NlModel representing a layout using layoutlib.
 */
public class LayoutlibSceneManager extends SceneManager {

  private static final SceneDecoratorFactory DECORATOR_FACTORY = new NlSceneDecoratorFactory();

  @Nullable private SceneView mySecondarySceneView;

  private int myDpi = 0;
  private final SelectionChangeListener mySelectionChangeListener = new SelectionChangeListener();
  private final ModelChangeListener myModelChangeListener = new ModelChangeListener();
  private final ConfigurationListener myConfigurationChangeListener = new ConfigurationChangeListener();
  private boolean myAreListenersRegistered = false;
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
  private final LinkedList<Runnable> myRenderCallbacks = new LinkedList<>();
  private final Semaphore myUpdateHierarchyLock = new Semaphore(1);

  /**
   * Logs a render action.
   *
   * @param trigger The event that triggered the render action or null if not known.
   */
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

  public LayoutlibSceneManager(@NotNull NlModel model, @NotNull DesignSurface designSurface) {
    super(model, designSurface);
    updateTrackingConfiguration();

    getDesignSurface().getSelectionModel().addListener(mySelectionChangeListener);

    Scene scene = getScene();

    model.getConfiguration().addListener(myConfigurationChangeListener);

    List<NlComponent> components = model.getComponents();
    if (!components.isEmpty()) {
      NlComponent rootComponent = components.get(0).getRoot();
      boolean previous = getScene().isAnimated();
      scene.setAnimated(false);
      SceneComponent root = createHierarchy(rootComponent);
      updateFromComponent(root, new HashSet<>());
      scene.setRoot(root);
      addTargets(root);
      scene.setAnimated(previous);
    }

    model.addListener(myModelChangeListener);
    myAreListenersRegistered = true;

    // let's make sure the selection is correct
    scene.selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
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
    }

    super.dispose();
    // dispose is called by the project close using the read lock. Invoke the render task dispose later without the lock.
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      synchronized (myRenderingTaskLock) {
        if (myRenderTask != null) {
          myRenderTask.dispose();
          myRenderTask = null;
        }
      }
      myRenderResultLock.writeLock().lock();
      try {
        myRenderResult = null;
      }
      finally {
        myRenderResultLock.writeLock().unlock();
      }
    });
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

    NlLayoutType type = model.getType();

    if (type.equals(NlLayoutType.MENU)) {
      return createSceneViewsForMenu();
    }

    SceneMode mode = getDesignSurface().getSceneMode();

    SceneView primarySceneView = mode.createPrimarySceneView(getDesignSurface(), this);

    if (!type.equals(NlLayoutType.PREFERENCE_SCREEN)) {
      mySecondarySceneView = mode.createSecondarySceneView(getDesignSurface(), this);
    }

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
    if (getScene().isAnimated()) {
      long time = System.currentTimeMillis();
      sceneComponent.setPositionTarget(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getX(component)),
                                       Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getY(component)),
                                       time, true);
      sceneComponent.setSizeTarget(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getW(component)),
                                   Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getH(component)),
                                   time, true);
    }
    else {
      sceneComponent.setPosition(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getX(component)),
                                 Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getY(component)), true);
      sceneComponent.setSize(Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getW(component)),
                             Coordinates.pxToDp(getDesignSurface(), NlComponentHelperKt.getH(component)), true);
    }
  }

  @Override
  public void update() {
    super.update();
    SelectionModel selectionModel = getDesignSurface().getSelectionModel();
    if (getScene().getRoot() != null && selectionModel.isEmpty()) {
      addTargets(getScene().getRoot());
    }
  }

  /**
   * Add targets to the given component (by asking the associated
   * {@linkplain ViewGroupHandler} to do it)
   */
  public void addTargets(@NotNull SceneComponent component) {
    SceneComponent parent = component.getParent();
    if (parent == null) {
      parent = getScene().getRoot();
    }
    if (parent == null) {
      return;
    }
    ViewHandler handler = NlComponentHelperKt.getViewHandler(parent.getNlComponent());
    if (handler instanceof ViewGroupHandler) {
      parent.setTargetProvider((ViewGroupHandler) handler);
    }
  }

  private class ModelChangeListener implements ModelListener {
    @Override
    public void modelDerivedDataChanged(@NotNull NlModel model) {
      NlDesignSurface surface = getDesignSurface();
      // TODO: this is the right behavior, but seems to unveil repaint issues. Turning it off for now.
      if (false && surface.getSceneMode() == SceneMode.BLUEPRINT_ONLY) {
        layout(true);
      }
      else {
        render(getTriggerFromChangeType(model.getLastChangeType()));
        mySelectionChangeListener
          .selectionChanged(getDesignSurface().getSelectionModel(), getDesignSurface().getSelectionModel().getSelection());
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
        layout(animate);
      }
    }
  }

  private class SelectionChangeListener implements SelectionListener {
    @Override
    public void selectionChanged(@NotNull SelectionModel model, @NotNull List<NlComponent> selection) {
      SceneComponent root = getScene().getRoot();
      if (root != null) {
        clearChildTargets(root);
        // After a new selection, we need to figure out the context
        if (!selection.isEmpty()) {
          NlComponent primary = selection.get(0);
          SceneComponent component = getScene().getSceneComponent(primary);
          if (component != null) { // TODO only add "static" target here (the ones that are not part of any an interaction
            addTargets(component); // or dependent on a specific state
          }
          else {
            addTargets(root);
          }
        }
        else {
          addTargets(root);
        }
      }
      getScene().needsRebuildList();
    }

    void clearChildTargets(SceneComponent component) {
      component.setTargetProvider(null);
      for (SceneComponent child : component.getChildren()) {
        child.setTargetProvider(null);
        clearChildTargets(child);
      }
    }
  }

  private void requestRender(@Nullable Runnable callback, @Nullable LayoutEditorRenderResult.Trigger trigger) {
    if (callback != null) {
      synchronized (myRenderCallbacks) {
        myRenderCallbacks.add(callback);
      }
    }
    // This update is low priority so the model updates take precedence
    getRenderingQueue().queue(new Update("model.render", LOW_PRIORITY) {
      @Override
      public void run() {
        render(trigger);
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    });
  }

  private class ConfigurationChangeListener implements ConfigurationListener {
    @Override
    public boolean changed(int flags) {
      if ((flags & CFG_DEVICE) != 0) {
        int newDpi = getModel().getConfiguration().getDensity().getDpiValue();
        if (myDpi != newDpi) {
          // Update from the model to update the dpi
          LayoutlibSceneManager.this.update();
        }
      }
      return true;
    }
  }

  @Override
  public void requestRender() {
    requestRender(null, getTriggerFromChangeType(getModel().getLastChangeType()));
  }

  /**
   * Similar to {@link #requestRender()} but it will be logged as a user initiated action. This is
   * not exposed at SceneManager level since it only makes sense for the Layout editor.
   */
  public void requestUserInitatedRender() {
    requestRender(null, LayoutEditorRenderResult.Trigger.USER);
  }

  public void requestLayoutAndRender(boolean animate) {
    requestRender(() -> {
      getModel().notifyListenersModelLayoutComplete(animate);
    }, getTriggerFromChangeType(getModel().getLastChangeType()));
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
        if (project.isOpen()) {
          DumbService dumbService = DumbService.getInstance(project);
          if (dumbService.isDumb()) {
            // During unit testing, the rendering queue runs on the Event Thread. Calling waitForSmartMode will throw an exception
            // when called on the Event Thread.
            // For now, we just check if we are in dumb mode (for testing we won't be) and when call waitForSmartMode.
            dumbService.waitForSmartMode();
          }
          if (model.getVirtualFile().isValid() && !model.getFacet().isDisposed()) {
            try {
              updateModel();
            }
            catch (Throwable e) {
              Logger.getInstance(NlModel.class).error(e);
            }
          }
        }

        synchronized (myProgressLock) {
          if (myCurrentIndicator != null) {
            myCurrentIndicator.stop();
            myCurrentIndicator = null;
          }
        }
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

  /**
   * Request a layout pass
   *
   * @param animate if true, the resulting layout should be animated
   */
  @Override
  public void layout(boolean animate) {
    Future<RenderResult> futureResult;
    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return;
      }
      futureResult = myRenderTask.layout();
    }

    try {
      RenderResult result = futureResult.get();

      if (result != null) {
        updateHierarchy(result);
        getModel().notifyListenersModelLayoutComplete(animate);
      }
    }
    catch (InterruptedException | ExecutionException e) {
      Logger.getInstance(NlModel.class).warn("Unable to run layout()", e);
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
  public Map<Object, PropertiesMap> getDefaultProperties() {
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
    catch (InterruptedException e) {
    }
  }

  @NotNull
  private List<ViewInfo> getRootViews(@NotNull RenderResult result) {
    return getModel().getType() == NlLayoutType.MENU ? result.getSystemRootViews() : result.getRootViews();
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
  // Note: NlModel.getFile() probably should be nullable.
  @Nullable
  private static XmlTag getRootTag(@NotNull NlModel model) {
    if (Disposer.isDisposed(model)) {
      return null;
    }
    XmlFile file = (XmlFile)AndroidPsiUtils.getPsiFileSafely(model.getProject(), model.getVirtualFile());
    if (file == null) {
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
  private boolean inflate(boolean force) {
    Configuration configuration = getModel().getConfiguration();

    Project project = getModel().getProject();
    if (project.isDisposed()) {
      return false;
    }
    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(project);

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParsers.saveFileIfNecessary(getModel().getFile());

    RenderResult result = null;
    RenderTask resultTask = null;
    synchronized (myRenderingTaskLock) {
      if (myRenderTask != null && !force) {
        // No need to inflate
        return false;
      }

      // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
      // external changes
      myRenderedVersion = resourceNotificationManager.getCurrentVersion(getModel().getFacet(), getModel().getFile(), configuration);

      RenderService renderService = RenderService.getInstance(getModel().getFacet());
      RenderLogger logger = renderService.createLogger();
      if (myRenderTask != null && !myRenderTask.isDisposed()) {
        myRenderTask.dispose();
      }
      myRenderTask = renderService.createTask(getModel().getFile(), configuration, logger, getDesignSurface());
      setupRenderTask(myRenderTask);
      if (myRenderTask != null) {
        myRenderTask.getLayoutlibCallback()
          .setAdaptiveIconMaskPath(getDesignSurface().getAdaptiveIconShape().getPathDescription());
        result = myRenderTask.inflate();
        if (result == null || !result.getRenderResult().isSuccess()) {
          myRenderTask.dispose();
          myRenderTask = null;

          if (result == null) {
            result = RenderResult.createBlank(getModel().getFile());
          }
        }
      }

      resultTask = myRenderTask;
    }

    updateHierarchy(result);
    myRenderResultLock.writeLock().lock();
    try {
      myRenderResult = result;
    }
    finally {
      myRenderResultLock.writeLock().unlock();
    }

    return resultTask != null;
  }

  @VisibleForTesting
  protected void setupRenderTask(@Nullable RenderTask task) {
  }

  /**
   * Synchronously update the model. This will inflate the layout and notify the listeners using
   * {@link ModelListener#modelDerivedDataChanged(NlModel)}.
   */
  protected void updateModel() {
    inflate(true);
    getModel().notifyListenersModelUpdateComplete();
  }

  /**
   * Renders the current model synchronously. Once the render is complete, the listeners {@link ModelListener#modelRendered(NlModel)}
   * method will be called.
   * <p/>
   * If the layout hasn't been inflated before, this call will inflate the layout before rendering.
   * <p/>
   * <b>Do not call this method from the dispatch thread!</b>
   */
  protected void render(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    try {
      renderImpl(trigger);
    }
    catch (Throwable e) {
      if (!getModel().getFacet().isDisposed()) {
        throw e;
      }
    } finally {
      ImmutableList<Runnable> callbacks;
      synchronized (myRenderCallbacks) {
        callbacks = ImmutableList.copyOf(myRenderCallbacks);
        myRenderCallbacks.clear();
      }
      callbacks.forEach(Runnable::run);
    }
  }

  private void renderImpl(@Nullable LayoutEditorRenderResult.Trigger trigger) {
    Configuration configuration = getModel().getConfiguration();
    DesignSurface surface = getDesignSurface();
    if (getModel().getConfigurationModificationCount() != configuration.getModificationCount()) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      if (!StringUtil.equals(configuration.getTheme(), myPreviousTheme)) {
        myPreviousTheme = configuration.getTheme();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.THEME_CHANGE);
      }
      else if (configuration.getTarget() != null && !StringUtil.equals(configuration.getTarget().getVersionName(), myPreviousVersion)) {
        myPreviousVersion = configuration.getTarget().getVersionName();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
      }
      else if (!configuration.getLocale().equals(myPreviousLocale)) {
        myPreviousLocale = configuration.getLocale();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.LANGUAGE_CHANGE);
      }
      else if (configuration.getDevice() != null && !StringUtil.equals(configuration.getDevice().getDisplayName(), myPreviousDeviceName)) {
        myPreviousDeviceName = configuration.getDevice().getDisplayName();
        NlUsageTrackerManager.getInstance(surface).logAction(LayoutEditorEvent.LayoutEditorEventType.DEVICE_CHANGE);
      }
    }

    NlModel.ChangeType changeType = getModel().getLastChangeType();
    getModel().resetLastChange();
    long renderStartTimeMs = System.currentTimeMillis();
    boolean inflated = inflate(false);
    long elapsedFrameTimeMs = myElapsedFrameTimeMs;

    Future<RenderResult> futureResult;
    synchronized (myRenderingTaskLock) {
      if (myRenderTask == null) {
        return;
      }
      if (elapsedFrameTimeMs != -1) {
        myRenderTask.setElapsedFrameTimeNanos(TimeUnit.MILLISECONDS.toNanos(elapsedFrameTimeMs));
      }
      futureResult = myRenderTask.render();
    }

    RenderResult result = Futures.getUnchecked(futureResult);
    // When the layout was inflated in this same call, we do not have to update the hierarchy again
    if (result != null && !inflated) {
      updateHierarchy(result);
    }
    myRenderResultLock.writeLock().lock();
    try {
      myRenderResult = result;
      // Downgrade the write lock to read lock
      myRenderResultLock.readLock().lock();
    }
    finally {
      myRenderResultLock.writeLock().unlock();
    }
    try {
      NlUsageTrackerManager.getInstance(surface).logRenderResult(trigger,
                                                                 myRenderResult,
                                                                 System.currentTimeMillis() - renderStartTimeMs);
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
      model.flattenComponents().collect(Collectors.toMap(NlComponent::getTag, Function.identity()));

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
                                   int parentX,
                                   int parentY,
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

}
