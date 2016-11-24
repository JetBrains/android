/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.model;

import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GradleVersion;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.sdklib.devices.State;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.avdmanager.AvdScreenData;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.configurations.ConfigurationMatcher;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleUtil;
import com.android.tools.idea.rendering.*;
import com.android.tools.idea.rendering.Locale;
import com.android.tools.idea.res.ProjectResourceRepository;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceVersion;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.uibuilder.analytics.NlUsageTrackerManager;
import com.android.tools.idea.uibuilder.api.*;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.editor.NlEditorProvider;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.lint.LintAnnotationsModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.android.util.PropertiesMap;
import com.android.utils.XmlUtils;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.*;
import com.google.wireless.android.sdk.stats.LayoutEditorEvent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Alarm;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidResourceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.GuardedBy;
import javax.swing.Timer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.InvalidDnDOperationException;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import static com.android.SdkConstants.*;
import static com.intellij.util.ui.update.Update.HIGH_PRIORITY;
import static com.intellij.util.ui.update.Update.LOW_PRIORITY;

/**
 * Model for an XML file
 */
public class NlModel implements Disposable, ResourceChangeListener, ModificationTracker {
  private static final Logger LOG = Logger.getInstance(NlModel.class);
  @AndroidCoordinate private static final int VISUAL_EMPTY_COMPONENT_SIZE = 1;
  private static final boolean CHECK_MODEL_INTEGRITY = false;
  private static final int RENDER_DELAY_MS = 10;
  private final Set<String> myPendingIds = Sets.newHashSet();

  @NotNull private final DesignSurface mySurface;
  @NotNull private final AndroidFacet myFacet;
  @NotNull private final ProjectResourceRepository myProjectResourceRepository;
  private final XmlFile myFile;
  private final ReentrantReadWriteLock myRenderResultLock = new ReentrantReadWriteLock();
  private final ConfigurationListener myConfigurationListener = new ConfigurationListener() {
    @Override
    public boolean changed(int flags) {
      if ((flags & (CFG_DEVICE | CFG_DEVICE_STATE)) != 0 && !mySurface.isCanvasResizing()) {
        mySurface.zoom(ZoomType.FIT_INTO);
      }

      return true;
    }
  };
  @GuardedBy("myRenderResultLock")
  private RenderResult myRenderResult;
  private Configuration myConfiguration;
  private final List<ModelListener> myListeners = Lists.newArrayList();
  private List<NlComponent> myComponents = Lists.newArrayList();
  private final SelectionModel mySelectionModel;
  private LintAnnotationsModel myLintAnnotationsModel;
  private final long myId;
  private boolean myActive;
  private ResourceVersion myRenderedVersion;
  private final ModelVersion myModelVersion = new ModelVersion();
  private AndroidPreviewProgressIndicator myCurrentIndicator;
  private static final Object PROGRESS_LOCK = new Object();
  private RenderTask myRenderTask;
  private final NlLayoutType myType;
  private long myConfigurationModificationCount;

  // Variables to track previous values of the configuration bar for tracking purposes
  private String myPreviousDeviceName;
  private Locale myPreviousLocale;
  private String myPreviousVersion;
  private String myPreviousTheme;
  // Variable to track what triggered the latest render (if known)
  private ChangeType myModificationTrigger;

  @NotNull
  public static NlModel create(@NotNull DesignSurface surface,
                               @Nullable Disposable parent,
                               @NotNull AndroidFacet facet,
                               @NotNull XmlFile file) {
    return new NlModel(surface, parent, facet, file);
  }

  @VisibleForTesting
  protected NlModel(@NotNull DesignSurface surface, @Nullable Disposable parent, @NotNull AndroidFacet facet, @NotNull XmlFile file) {
    mySurface = surface;
    myFacet = facet;
    myFile = file;
    myConfiguration = facet.getConfigurationManager().getConfiguration(myFile.getVirtualFile());
    myConfigurationModificationCount = myConfiguration.getModificationCount();
    mySelectionModel = new SelectionModel();
    myId = System.nanoTime() ^ file.getName().hashCode();
    if (parent != null) {
      Disposer.register(parent, this);
    }
    myType = NlLayoutType.typeOf(file);
    myProjectResourceRepository = ProjectResourceRepository.getProjectResources(myFacet, true);

    updateTrackingConfiguration();
  }

  /**
   * Notify model that it's active. A model is active by default.
   */
  public void activate() {
    if (!myActive) {
      myActive = true;

      myConfiguration.addListener(myConfigurationListener);
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
      ResourceVersion version = manager.addListener(this, myFacet, myFile, myConfiguration);

      // If the resources have changed or the configuration has been modified, request a model update
      if (!version.equals(myRenderedVersion) || (myConfiguration.getModificationCount() != myConfigurationModificationCount)) {
        String theme = myConfiguration.getTheme();
        if (theme != null && !theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) && !myProjectResourceRepository.hasResourceItem(theme)) {
          myConfiguration.setTheme(myConfiguration.getConfigurationManager().computePreferredTheme(myConfiguration));
        }
        requestModelUpdate();
        myModelVersion.myResourceVersion.incrementAndGet();
      }
    }
  }

  /**
   * Notify model that it's not active. This means it can stop watching for events etc. It may be activated again in the future.
   */
  public void deactivate() {
    if (myActive) {
      getRenderingQueue().cancelAllUpdates();
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
      manager.removeListener(this, myFacet, myFile, myConfiguration);
      myConfigurationModificationCount = myConfiguration.getModificationCount();
      myConfiguration.removeListener(myConfigurationListener);
      myActive = false;
    }
  }

  @NotNull
  public XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public NlLayoutType getType() {
    return myType;
  }

  @NotNull
  public SelectionModel getSelectionModel() {
    return mySelectionModel;
  }

  @Nullable
  public LintAnnotationsModel getLintAnnotationsModel() {
    return myLintAnnotationsModel;
  }

  public void setLintAnnotationsModel(@Nullable LintAnnotationsModel model) {
    myLintAnnotationsModel = model;
    // Deliberately not rev'ing the model version and firing changes here;
    // we know only the warnings layer cares about this change and can be
    // updated by a single repaint
  }

  /**
   * Asynchronously inflates the model and updates the view hierarchy
   */
  protected void requestModelUpdate() {
    ApplicationManager.getApplication().assertIsDispatchThread();

    synchronized (PROGRESS_LOCK) {
      if (myCurrentIndicator == null) {
        myCurrentIndicator = new AndroidPreviewProgressIndicator();
        myCurrentIndicator.start();
      }
    }

    getRenderingQueue().queue(new Update("model.update", HIGH_PRIORITY) {
      @Override
      public void run() {
        DumbService.getInstance(myFacet.getModule().getProject()).waitForSmartMode();
        if (!myFacet.isDisposed()) {
          try {
            if (myFacet.requiresAndroidModel() && myFacet.getAndroidModel() == null) {
              // Try again later - model hasn't been synced yet (and for example we won't
              // be able to resolve custom views coming from libraries like appcompat,
              // resulting in a broken render)
              ApplicationManager.getApplication().invokeLater(() -> requestModelUpdate());
              return;
            }
            updateModel();
          }
          catch (IncorrectOperationException e) {
            // We can get this exception if the module/project is closed while we are updating the model. Ignore it.
            assert myFacet.isDisposed();
          }
          catch (Throwable e) {
            Logger.getInstance(NlModel.class).error(e);
          }
        }

        synchronized (PROGRESS_LOCK) {
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
  private MergingUpdateQueue getRenderingQueue() {
    synchronized (myRenderingQueueLock) {
      if (myRenderingQueue == null) {
        myRenderingQueue = new MergingUpdateQueue("android.layout.rendering", RENDER_DELAY_MS, true, null, this, null,
                                                  Alarm.ThreadToUse.OWN_THREAD);
        myRenderingQueue.setRestartTimerOnAdd(true);
      }
      return myRenderingQueue;
    }
  }

  private final Object myRenderingQueueLock = new Object();
  private MergingUpdateQueue myRenderingQueue;
  private static final Object RENDERING_LOCK = new Object();

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

  @VisibleForTesting
  protected void setupRenderTask(@Nullable RenderTask task) {
  }

  /**
   * Synchronously inflates the model and updates the view hierarchy
   *
   * @param force forces the model to be re-inflated even if a previous version was already inflated
   * @returns whether the model was inflated in this call or not
   */
  private boolean inflate(boolean force) {
    Configuration configuration = myConfiguration;
    if (configuration == null) {
      return false;
    }

    ResourceNotificationManager resourceNotificationManager = ResourceNotificationManager.getInstance(myFile.getProject());

    // Some types of files must be saved to disk first, because layoutlib doesn't
    // delegate XML parsers for non-layout files (meaning layoutlib will read the
    // disk contents, so we have to push any edits to disk before rendering)
    LayoutPullParserFactory.saveFileIfNecessary(myFile);

    RenderResult result = null;
    synchronized (RENDERING_LOCK) {
      if (myRenderTask != null && !force) {
        // No need to inflate
        return false;
      }

      // Record the current version we're rendering from; we'll use that in #activate to make sure we're picking up any
      // external changes
      myRenderedVersion = resourceNotificationManager.getCurrentVersion(myFacet, myFile, myConfiguration);

      RenderService renderService = RenderService.get(myFacet);
      RenderLogger logger = renderService.createLogger();
      if (myRenderTask != null) {
        myRenderTask.dispose();
      }
      myRenderTask = renderService.createTask(myFile, configuration, logger, mySurface);
      setupRenderTask(myRenderTask);
      if (myRenderTask != null) {
        if (!isRenderViewPort()) {
          myRenderTask.useDesignMode(myFile);
        }
        result = myRenderTask.inflate();
        if (result == null || !result.getRenderResult().isSuccess()) {
          myRenderTask.dispose();
          myRenderTask = null;

          if (result == null) {
            result = RenderResult.createBlank(myFile);
          }
        }
      }

      myRenderResultLock.writeLock().lock();
      try {
        myRenderResult = result;
        updateHierarchy(result);
      }
      finally {
        myRenderResultLock.writeLock().unlock();
      }

      return myRenderTask != null;
    }
  }

  @NotNull
  Set<String> getPendingIds() {
    return myPendingIds;
  }

  private void updateHierarchy(@Nullable RenderResult result) {
    if (result == null || !result.getRenderResult().isSuccess()) {
      myComponents = Collections.emptyList();
    }
    else {
      XmlTag rootTag = AndroidPsiUtils.getRootTagSafely(myFile);
      List<ViewInfo> rootViews;
      rootViews = myType == NlLayoutType.MENU ? result.getSystemRootViews() : result.getRootViews();
      updateHierarchy(rootTag, rootViews);
    }
    myModelVersion.increase(ChangeType.UPDATE_HIERARCHY);

    if (CHECK_MODEL_INTEGRITY) {
      checkStructure();
    }
  }

  @VisibleForTesting
  public void updateHierarchy(@Nullable XmlTag rootTag, @NotNull Iterable<ViewInfo> rootViews) {
    ModelUpdater updater = new ModelUpdater(this);
    updater.update(rootTag, rootViews);
  }

  /**
   * Synchronously update the model. This will inflate the layout and notify the listeners using
   * {@link ModelListener#modelChanged(NlModel)}.
   */
  protected void updateModel() {
    inflate(true);
    notifyListenersModelUpdateComplete();
  }

  private void checkStructure() {
    if (CHECK_MODEL_INTEGRITY) {
      ApplicationManager.getApplication().runReadAction(() -> {
        Set<NlComponent> unique = Sets.newIdentityHashSet();
        Set<XmlTag> uniqueTags = Sets.newIdentityHashSet();
        checkUnique(myFile.getRootTag(), uniqueTags);
        uniqueTags.clear();
        for (NlComponent component : myComponents) {
          checkUnique(component.getTag(), uniqueTags);
          checkUnique(component, unique);
        }
        for (NlComponent component : myComponents) {
          checkStructure(component);
        }
      });
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(NlComponent component, Set<NlComponent> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(component);
      unique.add(component);

      for (NlComponent child : component.getChildren()) {
        checkUnique(child, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkUnique(XmlTag tag, Set<XmlTag> unique) {
    if (CHECK_MODEL_INTEGRITY) {
      assert !unique.contains(tag);
      unique.add(tag);
      for (XmlTag subTag : tag.getSubTags()) {
        checkUnique(subTag, unique);
      }
    }
  }

  @SuppressWarnings("MethodMayBeStatic")
  private void checkStructure(NlComponent component) {
    if (CHECK_MODEL_INTEGRITY) {
      // This is written like this instead of just "assert component.w != -1" to ease
      // setting breakpoint to debug problems
      if (component.w == -1) {
        assert false : component.w;
      }
      if (component.getSnapshot() == null) {
        assert false;
      }
      if (component.getTag() == null) {
        assert false;
      }
      if (!component.getTagName().equals(component.getTag().getName())) {
        assert false;
      }

      if (!component.getTag().isValid()) {
        assert false;
      }

      // Look for parent chain cycle
      NlComponent p = component.getParent();
      while (p != null) {
        if (p == component) {
          assert false;
        }
        p = p.getParent();
      }

      for (NlComponent child : component.getChildren()) {
        if (child == component) {
          assert false;
        }
        if (child.getParent() == null) {
          assert false;
        }
        if (child.getParent() != component) {
          assert false;
        }
        if (child.getTag().getParent() != component.getTag()) {
          assert false;
        }

        // Check recursively
        checkStructure(child);
      }
    }
  }

  /**
   * Renders the current model synchronously. Once the render is complete, the listeners {@link ModelListener#modelRendered(NlModel)}
   * method will be called.
   * <p/>
   * If the layout hasn't been inflated before, this call will inflate the layout before rendering.
   * <p/>
   * <b>Do not call this method from the dispatch thread!</b>
   */
  public void render() {
    if (myConfigurationModificationCount != myConfiguration.getModificationCount()) {
      // usage tracking (we only pay attention to individual changes where only one item is affected since those are likely to be triggered
      // by the user
      if (!StringUtil.equals(myConfiguration.getTheme(), myPreviousTheme)) {
        myPreviousTheme = myConfiguration.getTheme();
        NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.THEME_CHANGE);
      }
      else if (myConfiguration.getTarget() != null && !StringUtil.equals(myConfiguration.getTarget().getVersionName(), myPreviousVersion)) {
        myPreviousVersion = myConfiguration.getTarget().getVersionName();
        NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.API_LEVEL_CHANGE);
      }
      else if (!myConfiguration.getLocale().equals(myPreviousLocale)) {
        myPreviousLocale = myConfiguration.getLocale();
        NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.LANGUAGE_CHANGE);
      }
      else if (myConfiguration.getDevice() != null && !StringUtil.equals(myConfiguration.getDevice().getDisplayName(), myPreviousDeviceName)) {
        myPreviousDeviceName = myConfiguration.getDevice().getDisplayName();
        NlUsageTrackerManager.getInstance(mySurface).logAction(LayoutEditorEvent.LayoutEditorEventType.DEVICE_CHANGE);
      }
    }

    ChangeType changeType = myModificationTrigger;
    myModificationTrigger = null;
    long renderStartTimeMs = System.currentTimeMillis();
    boolean inflated = inflate(false);

    synchronized (RENDERING_LOCK) {
      if (myRenderTask != null) {
        RenderResult result = myRenderTask.render();
        myRenderResultLock.writeLock().lock();
        try {
          myRenderResult = result;
          // When the layout was inflated in this same call, we do not have to update the hierarchy again
          if (!inflated) {
            updateHierarchy(myRenderResult);
          }

          NlUsageTrackerManager.getInstance(mySurface).logRenderResult(changeType,
                                                                       myRenderResult,
                                                                       System.currentTimeMillis() - renderStartTimeMs);
        }
        finally {
          myRenderResultLock.writeLock().unlock();
        }
      }
    }

    notifyListenersRenderComplete();
  }

  /**
   * Renders the current model asynchronously. Once the render is complete, the listeners {@link ModelListener#modelRendered(NlModel)}
   * method will be called.
   */
  public void requestRender() {
    // This method will be removed once we only do direct rendering (see RenderTask.render(Graphics2D))
    // This update is low priority so the model updates take precedence
    getRenderingQueue().queue(new Update("model.render", LOW_PRIORITY) {
      @Override
      public void run() {
        if (myFacet.isDisposed()) {
          return;
        }

        render();
      }

      @Override
      public boolean canEat(Update update) {
        return this.equals(update);
      }
    });
  }

  /**
   * Request a layout pass
   *
   * @param animate if true, the resuting layout should be animated
   */
  public void requestLayout(boolean animate) {
    if (myRenderTask != null) {
      synchronized (RENDERING_LOCK) {
        RenderResult result = myRenderTask.layout();
        if (result != null) {
          updateHierarchy(result);
          notifyListenersModelLayoutComplete(animate);
        }
      }
    }
  }

  /**
   * Method that paints the current layout to the given {@link Graphics2D} object.
   */
  @SuppressWarnings("unused")
  public void paint(@NotNull Graphics2D graphics) {
    synchronized (RENDERING_LOCK) {
      if (myRenderTask != null) {
        myRenderTask.render(graphics);
      }
    }
  }

  /**
   * Adds a new {@link ModelListener}. If the listener already exists, this method will make sure that the listener is only
   * added once.
   */
  public void addListener(@NotNull ModelListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener); // prevent duplicate registration
      myListeners.add(listener);
    }
  }

  public void removeListener(@NotNull ModelListener listener) {
    synchronized (myListeners) {
      myListeners.remove(listener);
    }
  }

  /**
   * Calls all the listeners {@link ModelListener#modelChanged(NlModel)} method.
   */
  private void notifyListenersModelUpdateComplete() {
    List<ModelListener> listeners;
    synchronized (myListeners) {
      listeners = ImmutableList.copyOf(myListeners);
    }

    listeners.forEach(listener -> listener.modelChanged(this));
  }

  /**
   * Calls all the listeners {@link ModelListener#modelRendered(NlModel)} method.
   */
  private void notifyListenersRenderComplete() {
    List<ModelListener> listeners;
    synchronized (myListeners) {
      listeners = ImmutableList.copyOf(myListeners);
    }

    listeners.forEach(listener -> listener.modelRendered(this));
  }

  /**
   * Calls all the listeners {@link ModelListener#modelChangedOnLayout(NlModel, boolean)} method.
   *
   * @param animate if true, warns the listeners to animate the layout update
   */
  private void notifyListenersModelLayoutComplete(boolean animate) {
    List<ModelListener> listeners;
    synchronized (myListeners) {
      listeners = ImmutableList.copyOf(myListeners);
    }

    listeners.forEach(listener -> listener.modelChangedOnLayout(this, animate));
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

  @NotNull
  public AndroidFacet getFacet() {
    return myFacet;
  }

  @NotNull
  public Module getModule() {
    return myFacet.getModule();
  }

  @NotNull
  public Project getProject() {
    return getModule().getProject();
  }

  @NotNull
  public Configuration getConfiguration() {
    return myConfiguration;
  }

  /**
   * Returns true if the current module depends on the specified library.
   *
   * @param artifact library artifact e.g. "com.android.support:appcompat-v7"
   */
  public boolean isModuleDependency(@NotNull String artifact) {
    AndroidModuleModel gradleModel = AndroidModuleModel.get(myFacet);
    return gradleModel != null && GradleUtil.dependsOn(gradleModel, artifact);
  }

  /**
   * Returns the {@link GradleVersion} of the specified library that the current module depends on.
   *
   * @param artifact library artifact e.g. "com.android.support:appcompat-v7"
   * @return the revision or null if the module does not depend on the specified library.
   */
  @Nullable
  public GradleVersion getModuleDependencyVersion(@NotNull String artifact) {
    AndroidModuleModel gradleModel = AndroidModuleModel.get(myFacet);
    return gradleModel != null ? GradleUtil.getModuleDependencyVersion(gradleModel, artifact) : null;
  }

  /**
   * Changes the configuration to use a custom device with screen size defined by xDimension and yDimension.
   */
  public void overrideConfigurationScreenSize(@AndroidCoordinate int xDimension, @AndroidCoordinate int yDimension) {
    Device original = myConfiguration.getDevice();
    Device.Builder deviceBuilder = new Device.Builder(original); // doesn't copy tag id
    if (original != null) {
      deviceBuilder.setTagId(original.getTagId());
    }
    deviceBuilder.setName("Custom");
    deviceBuilder.setId(Configuration.CUSTOM_DEVICE_ID);
    Device device = deviceBuilder.build();
    for (State state : device.getAllStates()) {
      Screen screen = state.getHardware().getScreen();
      screen.setXDimension(xDimension);
      screen.setYDimension(yDimension);

      double dpi = screen.getPixelDensity().getDpiValue();
      double width = xDimension / dpi;
      double height = yDimension / dpi;
      double diagonalLength = Math.sqrt(width * width + height * height);

      screen.setDiagonalLength(diagonalLength);
      screen.setSize(AvdScreenData.getScreenSize(diagonalLength));

      screen.setRatio(AvdScreenData.getScreenRatio(xDimension, yDimension));

      screen.setScreenRound(device.getDefaultHardware().getScreen().getScreenRound());
      screen.setChin(device.getDefaultHardware().getScreen().getChin());
    }

    // If a custom device already exists, remove it before adding the latest one
    List<Device> devices = myConfiguration.getConfigurationManager().getDevices();
    boolean customDeviceReplaced = false;
    for (int i = 0; i < devices.size(); i++) {
      if ("Custom".equals(devices.get(i).getId())) {
        devices.set(i, device);
        customDeviceReplaced = true;
        break;
      }
    }

    if (!customDeviceReplaced) {
      devices.add(device);
    }

    VirtualFile better;
    State newState;
    //Change the orientation of the device depending on the shape of the canvas
    if (xDimension > yDimension) {
      better = ConfigurationMatcher.getBetterMatch(myConfiguration, device, "Landscape", null, null);
      newState = device.getState("Landscape");
    }
    else {
      better = ConfigurationMatcher.getBetterMatch(myConfiguration, device, "Portrait", null, null);
      newState = device.getState("Portrait");
    }

    if (better != null) {
      VirtualFile old = myConfiguration.getFile();
      assert old != null;
      Project project = mySurface.getProject();
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, better, -1);
      FileEditorManager manager = FileEditorManager.getInstance(project);
      FileEditor selectedEditor = manager.getSelectedEditor(old);
      manager.openEditor(descriptor, true);
      // Switch to the same type of editor (XML or Layout Editor) in the target file
      if (selectedEditor instanceof NlEditor) {
        manager.setSelectedEditor(better, NlEditorProvider.DESIGNER_ID);
      }
      else if (selectedEditor != null) {
        manager.setSelectedEditor(better, TextEditorProvider.getInstance().getEditorTypeId());
      }

      AndroidFacet facet = AndroidFacet.getInstance(myConfiguration.getModule());
      assert facet != null;
      Configuration configuration = facet.getConfigurationManager().getConfiguration(better);
      configuration.setEffectiveDevice(device, newState);
    }
    else {
      myConfiguration.setEffectiveDevice(device, newState);
    }
  }

  @NotNull
  public List<NlComponent> getComponents() {
    return myComponents;
  }

  @NotNull
  public Stream<NlComponent> flattenComponents() {
    return myComponents.stream().flatMap(NlComponent::flatten);
  }

  /**
   * Synchronizes a {@linkplain NlModel} after a render such that the component hierarchy
   * is up to date wrt view bounds, tag snapshots etc. Crucially, it attempts to preserve
   * component hierarchy (since XmlTags may sometimes not survive a PSI reparse, but we
   * want the {@linkplain NlComponent} instances to keep the same instances across these
   * edits such that for example the selection (a set of {@link NlComponent} instances)
   * are preserved.
   */
  private static class ModelUpdater {
    private final NlModel myModel;
    private final Map<XmlTag, NlComponent> myTagToComponentMap = Maps.newIdentityHashMap();
    private final Map<NlComponent, XmlTag> myComponentToTagMap = Maps.newIdentityHashMap();
    /**
     * Map from snapshots in the old component map to the corresponding components
     */
    private final Map<TagSnapshot, NlComponent> mySnapshotToComponent = Maps.newIdentityHashMap();
    /**
     * Map from tags in the view render tree to the corresponding snapshots
     */
    private final Map<XmlTag, TagSnapshot> myTagToSnapshot = Maps.newHashMap();

    public ModelUpdater(@NotNull NlModel model) {
      myModel = model;
    }

    private void recordComponentMapping(@NotNull XmlTag tag, @NotNull NlComponent component) {
      // Is the component already registered to some other tag?
      XmlTag prevTag = myComponentToTagMap.get(component);
      if (prevTag != null) {
        // Yes. Unregister it.
        myTagToComponentMap.remove(prevTag);
      }

      myComponentToTagMap.put(component, tag);
      myTagToComponentMap.put(tag, component);
    }

    /**
     * Update the component hierarchy associated with this {@linkplain ModelUpdater} such
     * that the associated component list correctly reflects the latest versions of the
     * XML PSI file, the given tag snapshot and {@link ViewInfo} hierarchy from layoutlib.
     */
    @VisibleForTesting
    public void update(@Nullable XmlTag newRoot, @NotNull Iterable<ViewInfo> rootViews) {
      if (newRoot == null) {
        myModel.myComponents = Collections.emptyList();
        return;
      }

      // Next find the snapshots corresponding to the missing components.
      // We have to search among the view infos in the new components.
      for (ViewInfo rootView : rootViews) {
        gatherTagsAndSnapshots(rootView, myTagToSnapshot);
      }

      NlComponent root = ApplicationManager.getApplication().runReadAction((Computable<NlComponent>)() -> {
        // Ensure that all XmlTags in the new XmlFile contents map to a corresponding component
        // form the old map
        mapOldToNew(newRoot);

        for (Map.Entry<XmlTag, NlComponent> entry : myTagToComponentMap.entrySet()) {
          XmlTag tag = entry.getKey();
          NlComponent component = entry.getValue();
          if (!component.getTagName().equals(tag.getName())) {
            // One or more incompatible changes: PSI nodes have been reused unpredictably
            // so completely recompute the hierarchy
            myTagToComponentMap.clear();
            myComponentToTagMap.clear();
            break;
          }
        }

        // Build up the new component tree
        return createTree(newRoot);
      });

      myModel.myComponents = Collections.singletonList(root);

      // Wipe out state in older components to make sure on reuse we don't accidentally inherit old
      // data
      for (NlComponent component : myTagToComponentMap.values()) {
        component.setBounds(0, 0, -1, -1); // -1: not initialized
        component.viewInfo = null;
        component.setSnapshot(null);
      }

      // Update the bounds. This is based on the ViewInfo instances.
      for (ViewInfo view : rootViews) {
        updateHierarchy(view, 0, 0);
      }

      // Finally, fix up bounds: ensure that all components not found in the view
      // info hierarchy inherit position from parent
      fixBounds(root);
    }

    private static void fixBounds(NlComponent root) {
      boolean computeBounds = false;
      if (root.w == -1 && root.h == -1) { // -1: not initialized
        computeBounds = true;

        // Look at parent instead
        NlComponent parent = root.getParent();
        if (parent != null && parent.w >= 0) {
          root.setBounds(parent.x, parent.y, 0, 0);
        }
      }

      List<NlComponent> children = root.children;
      if (children != null && !children.isEmpty()) {
        for (NlComponent child : children) {
          fixBounds(child);
        }

        if (computeBounds) {
          Rectangle rectangle = new Rectangle(root.x, root.y, root.w, root.h);
          // Grow bounds to include child bounds
          for (NlComponent child : children) {
            rectangle = rectangle.union(new Rectangle(child.x, child.y, child.w, child.h));
          }

          root.setBounds(rectangle.x, rectangle.y, rectangle.width, rectangle.height);
        }
      }
    }

    private void mapOldToNew(@NotNull XmlTag newRootTag) {
      ApplicationManager.getApplication().assertReadAccessAllowed();

      // First build up a new component tree to reflect the latest XmlFile hierarchy.
      // If there have been no structural changes, these map 1-1 from the previous hierarchy.
      // We first attempt to do it based on the XmlTags:
      //  (1) record a map from XmlTag to NlComponent in the previous component list
      for (NlComponent component : myModel.myComponents) {
        gatherTagsAndSnapshots(component);
      }

      // Look for any NlComponents no longer present in the new set
      List<XmlTag> missing = Lists.newArrayList();
      Set<XmlTag> remaining = Sets.newIdentityHashSet();
      remaining.addAll(myTagToComponentMap.keySet());
      checkMissing(newRootTag, remaining, missing);

      // If we've just removed a component, there will be no missing tags; we
      // can build the new/updated component hierarchy directly from the old
      // NlComponent instances
      if (missing.isEmpty()) {
        return;
      }

      // If we've just added a component, there will be no remaining tags from
      // old component instances. In this case all components should be new
      // instances
      if (remaining.isEmpty()) {
        return;
      }

      // Try to map more component instances from old to new.
      // We will do this via multiple heuristics:
      //   - mapping id's
      //   - looking at all component attributes (e.g. snapshots)

      // First check by id.
      // Note: We can't use XmlTag#getAttribute on the old component hierarchy;
      // those elements may not be valid and PSI will throw exceptions if we
      // attempt to access them.
      Map<String, NlComponent> oldIds = Maps.newHashMap();
      for (Map.Entry<TagSnapshot, NlComponent> entry : mySnapshotToComponent.entrySet()) {
        TagSnapshot snapshot = entry.getKey();
        if (snapshot != null) {
          String id = snapshot.getAttribute(ATTR_ID, ANDROID_URI);
          if (id != null) {
            oldIds.put(id, entry.getValue());
          }
        }
      }
      ListIterator<XmlTag> missingIterator = missing.listIterator();
      while (missingIterator.hasNext()) {
        XmlTag tag = missingIterator.next();
        String id = tag.getAttributeValue(ATTR_ID, ANDROID_URI);
        if (id != null) {
          // TODO: Consider unifying @+id/ and @id/ references here
          // (though it's unlikely for this to change across component
          // synchronization operations)
          NlComponent component = oldIds.get(id);
          if (component != null) {
            recordComponentMapping(tag, component);
            remaining.remove(component.getTag());
            missingIterator.remove();
          }
        }
      }

      if (missing.isEmpty() || remaining.isEmpty()) {
        // We've now resolved everything
        return;
      }

      // Next attempt to correlate components based on tag snapshots

      // First compute fingerprints of the old components
      Multimap<Long, TagSnapshot> snapshotIds = ArrayListMultimap.create();
      for (XmlTag old : remaining) {
        NlComponent component = myTagToComponentMap.get(old);
        if (component != null) { // this *should* be the case
          TagSnapshot snapshot = component.getSnapshot();
          if (snapshot != null) {
            snapshotIds.put(snapshot.getSignature(), snapshot);
          }
        }
      }

      // Note that we're using a multimap rather than a map for these keys,
      // so if you have the same exact element and attributes multiple times,
      // they'll be found and matched in the same order. (This works because
      // we're also tracking the missing xml tags in iteration order by using a
      // list instead of a set.)
      missingIterator = missing.listIterator();
      while (missingIterator.hasNext()) {
        XmlTag tag = missingIterator.next();
        TagSnapshot snapshot = myTagToSnapshot.get(tag);
        if (snapshot != null) {
          long signature = snapshot.getSignature();
          Collection<TagSnapshot> snapshots = snapshotIds.get(signature);
          if (!snapshots.isEmpty()) {
            TagSnapshot first = snapshots.iterator().next();
            NlComponent component = mySnapshotToComponent.get(first);
            if (component != null) {
              recordComponentMapping(tag, component);
              remaining.remove(component.getTag());
              snapshotIds.remove(tag, first);
              missingIterator.remove();
            }
          }
        }
      }

      // Finally, if there's just a single tag in question, it might have been
      // that we changed an attribute of a tag (so the fingerprint no longer matches).
      // If the tag name is identical, we'll go ahead.
      if (missing.size() == 1 && remaining.size() == 1) {
        XmlTag oldTag = remaining.iterator().next();
        NlComponent component = myTagToComponentMap.get(oldTag);
        if (component != null) {
          XmlTag newTag = missing.get(0);
          TagSnapshot snapshot = component.getSnapshot();
          if (snapshot != null) {
            if (snapshot.tagName.equals(newTag.getName())) {
              recordComponentMapping(newTag, component);
            }
          }
        }
      }
    }

    /**
     * Processes through the XML tag hierarchy recursively, and checks
     * whether the tag is in the remaining set, and if so removes it,
     * otherwise adds it to the missing set.
     */
    private static void checkMissing(XmlTag tag, Set<XmlTag> remaining, List<XmlTag> missing) {
      boolean found = remaining.remove(tag);
      if (!found) {
        missing.add(tag);
      }
      for (XmlTag child : tag.getSubTags()) {
        checkMissing(child, remaining, missing);
      }
    }

    private void gatherTagsAndSnapshots(@NotNull NlComponent component) {
      XmlTag tag = component.getTag();

      recordComponentMapping(tag, component);
      mySnapshotToComponent.put(component.getSnapshot(), component);

      for (NlComponent child : component.getChildren()) {
        gatherTagsAndSnapshots(child);
      }
    }

    private static void gatherTagsAndSnapshots(ViewInfo view, Map<XmlTag, TagSnapshot> map) {
      Object cookie = view.getCookie();
      if (cookie instanceof TagSnapshot) {
        TagSnapshot snapshot = (TagSnapshot)cookie;
        map.put(snapshot.tag, snapshot);
      }

      for (ViewInfo child : view.getChildren()) {
        gatherTagsAndSnapshots(child, map);
      }
    }

    @NotNull
    private NlComponent createTree(XmlTag tag) {
      NlComponent component = myTagToComponentMap.get(tag);
      if (component == null) {
        // New component: tag didn't exist in the previous component hierarchy,
        // and no similar tag was found
        component = new NlComponent(myModel, tag);
        recordComponentMapping(tag, component);
      }

      XmlTag[] subTags = tag.getSubTags();
      if (subTags.length > 0) {
        if (CHECK_MODEL_INTEGRITY) {
          Set<NlComponent> seen = Sets.newHashSet();
          Set<XmlTag> seenTags = Sets.newHashSet();
          for (XmlTag t : subTags) {
            if (seenTags.contains(t)) {
              assert false : t;
            }
            seenTags.add(t);
            NlComponent registeredComponent = myTagToComponentMap.get(t);
            if (registeredComponent != null) {
              if (seen.contains(registeredComponent)) {
                assert false : registeredComponent;
              }
              seen.add(registeredComponent);
            }
          }
        }

        List<NlComponent> children = new ArrayList<>(subTags.length);
        for (XmlTag subtag : subTags) {
          NlComponent child = createTree(subtag);
          children.add(child);
        }
        component.setChildren(children);
      }
      else {
        component.setChildren(null);
      }

      return component;
    }

    private void updateHierarchy(ViewInfo view,
                                 int parentX,
                                 int parentY) {
      ViewInfo bounds = RenderService.getSafeBounds(view);
      Object cookie = view.getCookie();
      NlComponent component = null;
      if (cookie != null) {
        if (cookie instanceof MergeCookie) {
          cookie = ((MergeCookie)cookie).getCookie();
        }
        if (cookie instanceof TagSnapshot) {
          TagSnapshot snapshot = (TagSnapshot)cookie;
          component = mySnapshotToComponent.get(snapshot);
          if (component == null) {
            component = myTagToComponentMap.get(snapshot.tag);
            if (component != null) {
              component.setSnapshot(snapshot);
              assert snapshot.tag != null;
              component.setTag(snapshot.tag);
            }
          }
          else {
            component.setSnapshot(snapshot);
            assert snapshot.tag != null;
            component.setTag(snapshot.tag);
          }
        }
      }

      if (component != null) {
        component.viewInfo = view;
        int left = parentX + bounds.getLeft();
        int top = parentY + bounds.getTop();
        int width = bounds.getRight() - bounds.getLeft();
        int height = bounds.getBottom() - bounds.getTop();

        component.setBounds(left, top, Math.max(width, VISUAL_EMPTY_COMPONENT_SIZE), Math.max(height, VISUAL_EMPTY_COMPONENT_SIZE));
      }

      parentX += bounds.getLeft();
      parentY += bounds.getTop();

      for (ViewInfo child : view.getChildren()) {
        updateHierarchy(child, parentX, parentY);
      }
    }
  }

  @Nullable
  public List<NlComponent> findByOffset(int offset) {
    XmlTag tag = PsiTreeUtil.findElementOfClassAtOffset(myFile, offset, XmlTag.class, false);
    return (tag != null) ? findViewsByTag(tag) : null;
  }

  /**
   * Looks up the point at the given pixel coordinates in the Android screen coordinate system, and
   * finds the leaf component there and returns it, if any. If the point is outside the screen bounds,
   * it will either return null, or the root view if {@code useRootOutsideBounds} is set and there is
   * precisely one parent.
   *
   * @param x                    the x pixel coordinate
   * @param y                    the y pixel coordinate
   * @param useRootOutsideBounds if true, return the root component when pointing outside the screen, otherwise null
   * @return the leaf component at the coordinate
   */
  @Nullable
  public NlComponent findLeafAt(@AndroidCoordinate int x, @AndroidCoordinate int y, boolean useRootOutsideBounds) {
    // Search BACKWARDS such that if the children are painted on top of each
    // other (as is the case in a FrameLayout) I pick the last one which will
    // be topmost!
    for (int i = myComponents.size() - 1; i >= 0; i--) {
      NlComponent component = myComponents.get(i);
      NlComponent leaf = component.findLeafAt(x, y);
      if (leaf != null) {
        return leaf;
      }
    }

    if (useRootOutsideBounds) {
      // If dragging outside of the screen, associate it with the
      // root widget (if there is one, and at most one (e.g. not a <merge> tag)
      List<NlComponent> components = myComponents;
      if (components.size() == 1) {
        return components.get(0);
      }
      else {
        return null;
      }
    }

    return null;
  }

  @Nullable
  private NlComponent findViewByTag(@NotNull XmlTag tag) {
    // TODO: Consider using lookup map
    for (NlComponent component : myComponents) {
      NlComponent match = component.findViewByTag(tag);
      if (match != null) {
        return match;
      }
    }

    return null;
  }

  @Nullable
  private List<NlComponent> findViewsByTag(@NotNull XmlTag tag) {
    List<NlComponent> result = null;
    for (NlComponent view : myComponents) {
      List<NlComponent> matches = view.findViewsByTag(tag);
      if (matches != null) {
        if (result != null) {
          result.addAll(matches);
        }
        else {
          result = matches;
        }
      }
    }

    return result;
  }

  @Nullable
  public NlComponent findViewByPsi(@Nullable PsiElement element) {
    assert ApplicationManager.getApplication().isReadAccessAllowed();

    while (element != null) {
      if (element instanceof XmlTag) {
        return findViewByTag((XmlTag)element);
      }
      element = element.getParent();
    }

    return null;
  }

  /**
   * Finds any components that overlap the given rectangle.
   *
   * @param x      The top left x corner defining the selection rectangle.
   * @param y      The top left y corner defining the selection rectangle.
   * @param width  The w of the selection rectangle
   * @param height The h of the selection rectangle
   */
  public List<NlComponent> findWithin(@AndroidCoordinate int x,
                                      @AndroidCoordinate int y,
                                      @AndroidCoordinate int width,
                                      @AndroidCoordinate int height) {
    List<NlComponent> within = Lists.newArrayList();
    for (NlComponent component : myComponents) {
      addWithin(within, component, x, y, width, height);
    }
    return within;
  }

  private static boolean addWithin(@NotNull List<NlComponent> result,
                                   @NotNull NlComponent component,
                                   @AndroidCoordinate int x,
                                   @AndroidCoordinate int y,
                                   @AndroidCoordinate int width,
                                   @AndroidCoordinate int height) {
    if (component.x + component.w <= x ||
        x + width <= component.x ||
        component.y + component.h <= y ||
        y + height <= component.y) {
      return false;
    }

    boolean found = false;
    for (NlComponent child : component.getChildren()) {
      found |= addWithin(result, child, x, y, width, height);
    }
    if (!found) {
      result.add(component);
    }
    return true;
  }

  public void delete(final Collection<NlComponent> components) {
    // Group by parent and ask each one to participate
    WriteCommandAction<Void> action = new WriteCommandAction<Void>(myFacet.getModule().getProject(), "Delete Component", myFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        handleDeletion(components);
      }
    };
    action.execute();

    List<NlComponent> remaining = Lists.newArrayList(mySelectionModel.getSelection());
    remaining.removeAll(components);
    mySelectionModel.setSelection(remaining);
    notifyModified(ChangeType.DELETE);
  }

  private void handleDeletion(@NotNull Collection<NlComponent> components) {
    // Segment the deleted components into lists of siblings
    Map<NlComponent, List<NlComponent>> siblingLists = groupSiblings(components);

    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(myFacet);

    // Notify parent components about children getting deleted
    for (Map.Entry<NlComponent, List<NlComponent>> entry : siblingLists.entrySet()) {
      NlComponent parent = entry.getKey();
      if (parent == null) {
        continue;
      }
      List<NlComponent> children = entry.getValue();
      boolean finished = false;

      ViewHandler handler = viewHandlerManager.getHandler(parent);
      if (handler instanceof ViewGroupHandler) {
        finished = ((ViewGroupHandler)handler).deleteChildren(parent, children);
      }

      if (!finished) {
        for (NlComponent component : children) {
          NlComponent p = component.getParent();
          if (p != null) {
            p.removeChild(component);
          }
          component.getTag().delete();
        }
      }
    }
  }

  /**
   * Partitions the given list of components into a map where each value is a list of siblings,
   * in the same order as in the original list, and where the keys are the parents (or null
   * for the components that do not have a parent).
   * <p/>
   * The value lists will never be empty. The parent key will be null for components without parents.
   *
   * @param components the components to be grouped
   * @return a map from parents (or null) to a list of components with the corresponding parent
   */
  @NotNull
  public static Map<NlComponent, List<NlComponent>> groupSiblings(@NotNull Collection<? extends NlComponent> components) {
    Map<NlComponent, List<NlComponent>> siblingLists = new HashMap<>();

    if (components.isEmpty()) {
      return siblingLists;
    }
    if (components.size() == 1) {
      NlComponent component = components.iterator().next();
      siblingLists.put(component.getParent(), Collections.singletonList(component));
      return siblingLists;
    }

    for (NlComponent component : components) {
      NlComponent parent = component.getParent();
      List<NlComponent> children = siblingLists.get(parent);
      if (children == null) {
        children = new ArrayList<>();
        siblingLists.put(parent, children);
      }
      children.add(component);
    }

    return siblingLists;
  }

  /**
   * Creates a new component of the given type. It will optionally insert it as a child of the given parent (and optionally
   * right before the given sibling or null to append at the end.)
   * <p/>
   * Note: This operation can only be called when the caller is already holding a write lock. This will be the
   * case from {@link ViewHandler} callbacks such as {@link ViewHandler#onCreate} and {@link DragHandler#commit}.
   *
   * @param screenView The target screen, if known. Used to handle pixel to dp computations in view handlers, etc.
   * @param fqcn       The fully qualified name of the widget to insert, such as {@code android.widget.LinearLayout}.
   *                   You can also pass XML tags here (this is typically the same as the fully qualified class name
   *                   of the custom view, but for Android framework views in the android.view or android.widget packages,
   *                   you can omit the package.)
   * @param parent     The optional parent to add this component to
   * @param before     The sibling to insert immediately before, or null to append
   * @param insertType The type of insertion
   */
  public NlComponent createComponent(@Nullable ScreenView screenView,
                                     @NotNull String fqcn,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before,
                                     @NotNull InsertType insertType) {
    String tagName = NlComponent.viewClassToTag(fqcn);

    XmlTag tag;
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      tag = parent.getTag().createChildTag(tagName, null, null, false);
    }
    else {
      // Creating a component not yet inserted into a layout. Typically done when trying to perform
      // a drag from palette, etc.
      XmlElementFactory elementFactory = XmlElementFactory.getInstance(getProject());
      String text = "<" + fqcn + " xmlns:android=\"http://schemas.android.com/apk/res/android\"/>"; // SIZES?
      tag = elementFactory.createTagFromText(text);
    }

    return createComponent(screenView, tag, parent, before, insertType);
  }

  public NlComponent createComponent(@Nullable ScreenView screenView,
                                     @NotNull XmlTag tag,
                                     @Nullable NlComponent parent,
                                     @Nullable NlComponent before,
                                     @NotNull InsertType insertType) {
    if (parent != null) {
      // Creating a component intended to be inserted into an existing layout
      XmlTag parentTag = parent.getTag();
      if (before != null) {
        tag = (XmlTag)parentTag.addBefore(tag, before.getTag());
      }
      else {
        tag = parentTag.addSubTag(tag, false);
      }

      // Required attribute for all views; drop handlers can adjust as necessary
      if (tag.getAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI) == null) {
        tag.setAttribute(ATTR_LAYOUT_WIDTH, ANDROID_URI, VALUE_WRAP_CONTENT);
      }
      if (tag.getAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI) == null) {
        tag.setAttribute(ATTR_LAYOUT_HEIGHT, ANDROID_URI, VALUE_WRAP_CONTENT);
      }
    }
    else {
      // No namespace yet: use the default prefix instead
      if (tag.getAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH) == null) {
        tag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_WIDTH, VALUE_WRAP_CONTENT);
      }
      if (tag.getAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT) == null) {
        tag.setAttribute(ANDROID_NS_NAME_PREFIX + ATTR_LAYOUT_HEIGHT, VALUE_WRAP_CONTENT);
      }
    }

    NlComponent child = new NlComponent(this, tag);

    if (parent != null) {
      parent.addChild(child, before);
    }

    // Notify view handlers
    ViewHandlerManager viewHandlerManager = ViewHandlerManager.get(getProject());
    ViewHandler childHandler = viewHandlerManager.getHandler(child);
    if (childHandler != null && screenView != null) {
      ViewEditor editor = new ViewEditorImpl(screenView);
      boolean ok = childHandler.onCreate(editor, parent, child, insertType);
      if (!ok) {
        if (parent != null) {
          parent.removeChild(child);
        }
        tag.delete();
        return null;
      }
    }
    if (parent != null) {
      ViewHandler parentHandler = viewHandlerManager.getHandler(parent);
      if (parentHandler instanceof ViewGroupHandler) {
        ((ViewGroupHandler)parentHandler).onChildInserted(parent, child, insertType);
      }
    }

    return child;
  }

  @NotNull
  public Transferable getSelectionAsTransferable() {
    return mySelectionModel.getTransferable(myId);
  }

  /**
   * Returns true if the specified components can be added to the specified receiver.
   */
  public boolean canAddComponents(@Nullable List<NlComponent> toAdd, @NotNull NlComponent receiver, @Nullable NlComponent before) {
    if (before != null && before.getParent() != receiver) {
      return false;
    }

    Object parentHandler = receiver.getViewHandler();

    if (!(parentHandler instanceof ViewGroupHandler)) {
      return false;
    }
    final ViewGroupHandler groupHandler = (ViewGroupHandler)parentHandler;

    if (toAdd == null || toAdd.isEmpty()) {
      return false;
    }
    for (NlComponent component : toAdd) {
      if (!groupHandler.acceptsChild(receiver, component)) {
        return false;
      }

      ViewHandler handler = ViewHandlerManager.get(getProject()).getHandler(component);

      if (handler != null && !handler.acceptsParent(receiver, component)) {
        return false;
      }

      // If the receiver is a (possibly indirect) child of any of the dragged components, then reject the operation
      NlComponent same = receiver;
      while (same != null) {
        if (same == component) {
          return false;
        }
        same = same.getParent();
      }
    }

    return true;
  }

  /**
   * Adds components to the specified receiver before the given sibling.
   * If insertType is a move the components specified should be components from this model.
   */
  public void addComponents(@Nullable final List<NlComponent> toAdd,
                            @NotNull final NlComponent receiver,
                            @Nullable final NlComponent before,
                            @NotNull final InsertType insertType) {
    if (!canAddComponents(toAdd, receiver, before)) {
      return;
    }
    assert toAdd != null;

    WriteCommandAction<Void> action = new WriteCommandAction<Void>(getProject(), insertType.getDragType().getDescription(), myFile) {
      @Override
      protected void run(@NotNull Result<Void> result) throws Throwable {
        handleAddition(toAdd, receiver, before, insertType);
      }
    };
    action.execute();
    notifyModified(ChangeType.ADD_COMPONENTS);
  }

  private void handleAddition(@NotNull List<NlComponent> added,
                              @NotNull NlComponent receiver,
                              @Nullable NlComponent before,
                              @NotNull InsertType insertType) {
    Set<String> ids = Sets.newHashSet(NlComponent.getIds(this));

    ViewGroupHandler groupHandler = (ViewGroupHandler)receiver.getViewHandler();
    assert groupHandler != null;

    for (NlComponent component : added) {
      if (insertType.isMove()) {
        insertType = component.getParent() == receiver ? InsertType.MOVE_WITHIN : InsertType.MOVE_INTO;
      }
      if (component.needsDefaultId() && (StringUtil.isEmpty(component.getId()) || !insertType.isMove())) {
        ids.add(NlComponent.assignId(component, ids));
      }
      groupHandler.onChildInserted(receiver, component, insertType);

      NlComponent parent = component.getParent();
      if (parent != null) {
        parent.removeChild(component);
      }
      receiver.addChild(component, before);
      if (receiver.getTag() != component.getTag()) {
        XmlTag prev = component.getTag();
        transferNamespaces(prev);
        if (before != null) {
          component.setTag((XmlTag)receiver.getTag().addBefore(component.getTag(), before.getTag()));
        }
        else {
          component.setTag(receiver.getTag().addSubTag(component.getTag(), false));
        }
        if (insertType.isMove()) {
          prev.delete();
        }
      }
      removeNamespaceAttributes(component);
      TemplateUtils.reformatAndRearrange(getProject(), component.getTag());
    }
  }

  /**
   * Given a root tag which is not yet part of the current document, (1) look up any namespaces defined on that root tag, transfer
   * those to the current document, and (2) update all attribute prefixes for namespaces to match those in the current document
   */
  private void transferNamespaces(@NotNull XmlTag tag) {
    // Transfer namespace attributes
    XmlDocument xmlDocument = myFile.getDocument();
    assert xmlDocument != null;
    XmlTag rootTag = xmlDocument.getRootTag();
    assert rootTag != null;
    Map<String, String> prefixToNamespace = rootTag.getLocalNamespaceDeclarations();
    Map<String, String> namespaceToPrefix = Maps.newHashMap();
    for (Map.Entry<String, String> entry : prefixToNamespace.entrySet()) {
      namespaceToPrefix.put(entry.getValue(), entry.getKey());
    }
    Map<String, String> oldPrefixToPrefix = Maps.newHashMap();

    for (Map.Entry<String, String> entry : tag.getLocalNamespaceDeclarations().entrySet()) {
      String namespace = entry.getValue();
      String prefix = entry.getKey();
      String currentPrefix = namespaceToPrefix.get(namespace);
      if (currentPrefix == null) {
        // The namespace isn't used in the document. Import it.
        String newPrefix = AndroidResourceUtil.ensureNamespaceImported(myFile, namespace, prefix);
        if (!prefix.equals(newPrefix)) {
          // We imported the namespace, but the prefix used in the new document isn't available
          // so we need to update all attribute references to the new name
          oldPrefixToPrefix.put(prefix, newPrefix);
          namespaceToPrefix.put(namespace, newPrefix);
        }
      }
      else if (!prefix.equals(currentPrefix)) {
        // The namespace is already imported, but using a different prefix. We need
        // to switch the prefixes.
        oldPrefixToPrefix.put(prefix, currentPrefix);
      }
    }

    if (!oldPrefixToPrefix.isEmpty()) {
      updatePrefixes(tag, oldPrefixToPrefix);
    }
  }

  /**
   * Recursively update all attributes such that XML attributes with prefixes in the {@code oldPrefixToPrefix} key set
   * are replaced with the corresponding values
   */
  private static void updatePrefixes(@NotNull XmlTag tag, @NotNull Map<String, String> oldPrefixToPrefix) {
    for (XmlAttribute attribute : tag.getAttributes()) {
      String prefix = attribute.getNamespacePrefix();
      if (!prefix.isEmpty()) {
        if (prefix.equals(XMLNS)) {
          String newPrefix = oldPrefixToPrefix.get(attribute.getLocalName());
          if (newPrefix != null) {
            attribute.setName(XMLNS_PREFIX + newPrefix);
          }
        }
        else {
          String newPrefix = oldPrefixToPrefix.get(prefix);
          if (newPrefix != null) {
            attribute.setName(newPrefix + ':' + attribute.getLocalName());
          }
        }
      }
    }

    for (XmlTag child : tag.getSubTags()) {
      updatePrefixes(child, oldPrefixToPrefix);
    }
  }

  private static void removeNamespaceAttributes(NlComponent component) {
    for (XmlAttribute attribute : component.getTag().getAttributes()) {
      if (attribute.getName().startsWith(XMLNS_PREFIX)) {
        attribute.delete();
      }
    }
  }

  @Nullable
  public static DnDTransferItem getTransferItem(@NotNull Transferable transferable, boolean allowPlaceholder) {
    DnDTransferItem item = null;
    try {
      if (transferable.isDataFlavorSupported(ItemTransferable.DESIGNER_FLAVOR)) {
        item = (DnDTransferItem)transferable.getTransferData(ItemTransferable.DESIGNER_FLAVOR);
      }
      else if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        String xml = (String)transferable.getTransferData(DataFlavor.stringFlavor);
        if (!StringUtil.isEmpty(xml)) {
          item = new DnDTransferItem(new DnDTransferComponent("", xml, 200, 100));
        }
      }
    }
    catch (InvalidDnDOperationException ex) {
      if (!allowPlaceholder) {
        return null;
      }
      String defaultXml = "<placeholder xmlns:android=\"http://schemas.android.com/apk/res/android\"/>";
      item = new DnDTransferItem(new DnDTransferComponent("", defaultXml, 200, 100));
    }
    catch (IOException | UnsupportedFlavorException ex) {
      LOG.warn(ex);
    }
    return item;
  }

  @Nullable
  public List<NlComponent> createComponents(@NotNull ScreenView screenView,
                                            @NotNull DnDTransferItem item,
                                            @NotNull InsertType insertType) {
    List<NlComponent> components = new ArrayList<>(item.getComponents().size());
    for (DnDTransferComponent dndComponent : item.getComponents()) {
      XmlTag tag = createTag(screenView.getModel().getProject(), dndComponent.getRepresentation());
      NlComponent component = createComponent(screenView, tag, null, null, insertType);
      if (component == null) {
        return null;  // User may have cancelled
      }
      component.w = dndComponent.getWidth();
      component.h = dndComponent.getHeight();
      components.add(component);
    }
    return components;
  }

  @NotNull
  @VisibleForTesting
  public static XmlTag createTag(@NotNull Project project, @NotNull String text) {
    XmlElementFactory elementFactory = XmlElementFactory.getInstance(project);
    XmlTag tag = null;
    if (XmlUtils.parseDocumentSilently(text, false) != null) {
      try {
        tag = elementFactory.createTagFromText(text);

        setNamespaceUri(tag, ANDROID_NS_NAME, ANDROID_URI);
        setNamespaceUri(tag, APP_PREFIX, AUTO_URI);
      }
      catch (IncorrectOperationException ignore) {
        // Thrown by XmlElementFactory if you try to parse non-valid XML. User might have tried
        // to drop something like plain text -- insert this as a text view instead.
        // However, createTagFromText may not always throw this for invalid XML, so we perform the above parseDocument
        // check first instead.
      }
    }
    if (tag == null) {
      tag = elementFactory.createTagFromText("<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" " +
                                             " android:text=\"" + XmlUtils.toXmlAttributeValue(text) + "\"" +
                                             " android:layout_width=\"wrap_content\"" +
                                             " android:layout_height=\"wrap_content\"" +
                                             "/>");
    }
    return tag;
  }

  private static void setNamespaceUri(@NotNull XmlTag tag, @NotNull String prefix, @NotNull String uri) {
    boolean anyMatch = Arrays.stream(tag.getAttributes())
      .anyMatch(attribute -> attribute.getNamespacePrefix().equals(prefix));

    if (anyMatch) {
      tag.setAttribute("xmlns:" + prefix, uri);
    }
  }

  @NotNull
  public InsertType determineInsertType(@NotNull DragType dragType, @Nullable DnDTransferItem item, boolean asPreview) {
    if (item != null && item.isFromPalette()) {
      return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
    }
    switch (dragType) {
      case CREATE:
        return asPreview ? InsertType.CREATE_PREVIEW : InsertType.CREATE;
      case MOVE:
        return item != null && myId != item.getModelId() ? InsertType.COPY : InsertType.MOVE_INTO;
      case COPY:
        return InsertType.COPY;
      case PASTE:
      default:
        return InsertType.PASTE;
    }
  }

  @Override
  public void dispose() {
    deactivate(); // ensure listeners are unregistered if necessary

    synchronized (myListeners) {
      myListeners.clear();
    }

    // dispose is called by the project close using the read lock. Invoke the render task dispose later without the lock.
    ApplicationManager.getApplication().invokeLater(() -> {
      synchronized (RENDERING_LOCK) {
        if (myRenderTask != null) {
          myRenderTask.dispose();
          myRenderTask = null;
        }
      }
      myRenderResultLock.writeLock().lock();
      try {
        myRenderResult = null;
      } finally {
        myRenderResultLock.writeLock().unlock();
      }
    });
  }

  @Override
  public String toString() {
    return NlModel.class.getSimpleName() + " for " + myFile;
  }

  // ---- Implements ResourceNotificationManager.ResourceChangeListener ----

  @Override
  public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reason) {
    for (ResourceNotificationManager.Reason r : reason) {
      switch (r) {
        case RESOURCE_EDIT:
          notifyModified(ChangeType.RESOURCE_EDIT);
          break;
        case EDIT:
          notifyModified(ChangeType.EDIT);
          break;
        case IMAGE_RESOURCE_CHANGED:
          RefreshRenderAction.clearCache(mySurface);
          break;
        case GRADLE_SYNC:
        case PROJECT_BUILD:
        case VARIANT_CHANGED:
        case SDK_CHANGED:
          notifyModified(ChangeType.BUILD);
          break;
        case CONFIGURATION_CHANGED:
          notifyModified(ChangeType.CONFIGURATION_CHANGE);
          break;
      }
    }
  }

  // ---- Implements ModificationTracker ----

  public enum ChangeType {
    RESOURCE_EDIT,
    EDIT,
    RESOURCE_CHANGED,
    ADD_COMPONENTS,
    DELETE,
    DND_COMMIT,
    DND_END,
    DROP,
    RESIZE_END, RESIZE_COMMIT,
    REQUEST_RENDER,
    UPDATE_HIERARCHY,
    BUILD,
    CONFIGURATION_CHANGE
  }

  /**
   * Maintains multiple counter depending on what did change in the model
   */
  static class ModelVersion {
    private final AtomicLong myVersion = new AtomicLong();
    private final AtomicLong myResourceVersion = new AtomicLong();
    private final AtomicLong myHierarchyVersion = new AtomicLong();
    @SuppressWarnings("unused") ChangeType mLastReason;

    public void increase(ChangeType reason) {
      myVersion.incrementAndGet();
      mLastReason = reason;
      switch (reason) {
        case RESOURCE_EDIT:
        case EDIT:
        case RESOURCE_CHANGED:
        case DELETE:
        case RESIZE_END:
        case RESIZE_COMMIT:
        case ADD_COMPONENTS: {
          myResourceVersion.incrementAndGet();
        }
        break;
        default:
          myHierarchyVersion.incrementAndGet();
      }
    }

    public long getVersion() {
      return myVersion.get();
    }

    public long getResourceVersion() {
      return myResourceVersion.get();
    }
  }

  @Override
  public long getModificationCount() {
    return myModelVersion.getVersion();
  }

  public long getResourceVersion() {
    return myModelVersion.getResourceVersion();
  }

  public void notifyModified(ChangeType reason) {
    String theme = myConfiguration.getTheme();
    if (theme != null && !theme.startsWith(ANDROID_STYLE_RESOURCE_PREFIX) && !myProjectResourceRepository.hasResourceItem(theme)) {
      myConfiguration.setTheme(myConfiguration.getConfigurationManager().computePreferredTheme(myConfiguration));
    }
    myModelVersion.increase(reason);
    myModificationTrigger = reason;
    requestModelUpdate();
  }

  /**
   * Updates the saved values that are used to log user changes to the configuration toolbar.
   */
  private void updateTrackingConfiguration() {
    myPreviousDeviceName = myConfiguration.getDevice() != null ? myConfiguration.getDevice().getDisplayName() : null;
    myPreviousVersion = myConfiguration.getTarget() != null ? myConfiguration.getTarget().getVersionName() : null;
    myPreviousLocale = myConfiguration.getLocale();
    myPreviousTheme = myConfiguration.getTheme();
  }

  public void setConfiguration(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myFile.getProject());
    ResourceVersion version = manager.addListener(this, myFacet, myFile, myConfiguration);
    if (!version.equals(myRenderedVersion)) {
      requestModelUpdate();
      myModelVersion.myResourceVersion.incrementAndGet();
    }
    myConfigurationModificationCount = myConfiguration.getModificationCount();

    updateTrackingConfiguration();
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
              mySurface.registerIndicator(this);
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
        ApplicationManager.getApplication().invokeLater(() -> mySurface.unregisterIndicator(this));
      }
    }
  }
}
