/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.visual;

import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_X;
import static com.android.tools.idea.uibuilder.graphics.NlConstants.DEFAULT_SCREEN_OFFSET_Y;

import com.android.annotations.concurrency.GuardedBy;
import com.android.annotations.concurrency.UiThread;
import com.android.resources.ResourceFolderType;
import com.android.tools.adtui.actions.DropDownAction;
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.adtui.util.ActionToolbarUtil;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.editor.PanZoomListener;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.LayoutScannerConfiguration;
import com.android.tools.idea.res.IdeResourcesUtil;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.analytics.NlAnalyticsManager;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.visual.analytics.MultiViewMetricTrackerKt;
import com.android.tools.idea.util.SyncUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import icons.StudioIcons;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultFocusTraversalPolicy;
import java.awt.event.AdjustmentEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Form of layout visualization which offers multiple previews for different devices in the same time. It provides a
 * convenient way to user to preview the layout in different devices.
 */
public class VisualizationForm
  implements Disposable, ConfigurationSetListener, ResourceNotificationManager.ResourceChangeListener, PanZoomListener {

  public static final String VISUALIZATION_DESIGN_SURFACE = "VisualizationFormDesignSurface";

  /**
   * horizontal gap between different previews
   */
  @SwingCoordinate private static final int HORIZONTAL_SCREEN_DELTA = 100;

  /**
   * vertical gap between different previews
   */
  @SwingCoordinate private static final int VERTICAL_SCREEN_DELTA = 48;

  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final JPanel myRoot = new JPanel(new BorderLayout());
  @Nullable private VirtualFile myFile;
  private final ReentrantLock myResourceNotifyingFilesLock = new ReentrantLock();
  @GuardedBy("myResourceNotifyingFilesLock")
  private final Set<VirtualFile> myResourceNotifyingFiles = new HashSet<>();
  private boolean isActive = false;
  private JComponent myContentPanel;
  private JComponent myActionToolbarPanel;

  private final ReentrantLock myCancelRenderingTaskLock = new ReentrantLock();
  @GuardedBy("myCancelRenderingTaskLock")
  @Nullable
  private Runnable myCancelRenderingTask = null;

  /**
   * Contains the editor that is currently being loaded.
   * Once the file is loaded, myPendingEditor will be null.
   */
  private FileEditor myPendingEditor;

  private FileEditor myEditor;

  @NotNull private ConfigurationSet myCurrentConfigurationSet;
  @NotNull private VisualizationModelsProvider myCurrentModelsProvider;

  /**
   * {@link CompletableFuture} of the next model load. This is kept so the load can be cancelled.
   */
  private AtomicBoolean myCancelPendingModelLoad = new AtomicBoolean(false);

  @NotNull private final EmptyProgressIndicator myProgressIndicator = new EmptyProgressIndicator();

  public VisualizationForm(@NotNull Project project, @NotNull Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
    myProject = project;
    myCurrentConfigurationSet = VisualizationToolSettings.getInstance().getGlobalState().getConfigurationSet();
    myCurrentModelsProvider = myCurrentConfigurationSet.getModelsProviderCreator().invoke(this);

    mySurface = NlDesignSurface.builder(myProject, VisualizationForm.this)
      .showModelNames()
      .setIsPreview(false)
      .setEditable(true)
      .setSceneManagerProvider((surface, model) -> {
        LayoutlibSceneManager sceneManager = new LayoutlibSceneManager(model, surface, LayoutScannerConfiguration.getDISABLED());
        sceneManager.setListenResourceChange(false);
        sceneManager.setShowDecorations(VisualizationToolSettings.getInstance().getGlobalState().getShowDecoration());
        sceneManager.setRerenderWhenModelDerivedDataChanged(false);
        sceneManager.setUpdateAndRenderWhenActivated(false);
        sceneManager.setUseImagePool(false);
        // 0.0f makes it spend 50% memory. See document in RenderTask#MIN_DOWNSCALING_FACTOR.
        sceneManager.setQuality(0.0f);
        return sceneManager;
      })
      .setActionManagerProvider((surface) -> new VisualizationActionManager((NlDesignSurface)surface, () -> myCurrentModelsProvider))
      .setInteractionHandlerProvider((surface) -> new VisualizationInteractionHandler(surface, () -> myCurrentModelsProvider))
      .setLayoutManager(new GridSurfaceLayoutManager(DEFAULT_SCREEN_OFFSET_X,
                                                     DEFAULT_SCREEN_OFFSET_Y,
                                                     HORIZONTAL_SCREEN_DELTA,
                                                     VERTICAL_SCREEN_DELTA,
                                                     false))
      .setMinScale(0.10)
      .setMaxScale(4)
      .build();
    mySurface.addPanZoomListener(this);

    updateScreenMode();
    Disposer.register(this, mySurface);
    mySurface.setName(VISUALIZATION_DESIGN_SURFACE);

    myWorkBench = new WorkBench<>(myProject, "Visualization", null, this);
    myWorkBench.setLoadingText("Loading...");
    myWorkBench.setToolContext(mySurface);

    myRoot.add(createToolbarPanel(), BorderLayout.NORTH);
    myRoot.add(myWorkBench, BorderLayout.CENTER);
    myRoot.setFocusCycleRoot(true);
    myRoot.setFocusTraversalPolicy(new VisualizationTraversalPolicy(mySurface));
  }

  private void updateScreenMode() {
    switch (myCurrentConfigurationSet) {
      case COLOR_BLIND_MODE:
        mySurface.setScreenViewProvider(NlScreenViewProvider.COLOR_BLIND, false);
        break;
      default:
        mySurface.setScreenViewProvider(NlScreenViewProvider.VISUALIZATION, false);
        break;
    }
  }

  @NotNull
  private JComponent createToolbarPanel() {
    myActionToolbarPanel = new AdtPrimaryPanel(new BorderLayout());
    myActionToolbarPanel.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createMatteBorder(0, 0, 1, 0, StudioColorsKt.getBorder()),
      BorderFactory.createEmptyBorder(0, 6, 0, 0))
    );
    updateActionToolbar();
    return myActionToolbarPanel;
  }

  private void updateActionToolbar() {
    myActionToolbarPanel.removeAll();
    DefaultActionGroup group = new DefaultActionGroup();
    String fileName = myFile != null ? myFile.getName() : "";
    // Add an empty action and disable it permanently for displaying file name.
    group.add(new TextLabelAction(fileName));
    group.addSeparator();
    group.add(new DefaultActionGroup(new ConfigurationSetMenuAction(this, myCurrentConfigurationSet)));
    if (myFile != null) {
      PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
      AndroidFacet facet = file != null ? AndroidFacet.getInstance(file) : null;
      if (facet != null) {
        ActionGroup configurationActions = myCurrentModelsProvider.createActions(file, facet);
        group.addAll(configurationActions);
      }
    }
    DropDownAction viewOptions = new DropDownAction(null, "View Options", StudioIcons.Common.VISIBILITY_INLINE);
    viewOptions.add(new ToggleShowDecorationAction());
    viewOptions.setPopup(true);
    group.add(viewOptions);
    // Use ActionPlaces.EDITOR_TOOLBAR as place to update the ui when appearance is changed.
    // In IJ's implementation, only the actions in ActionPlaces.EDITOR_TOOLBAR toolbar will be tweaked when ui is changed.
    // See com.intellij.openapi.actionSystem.impl.ActionToolbarImpl.tweakActionComponentUI()
    ActionToolbar actionToolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, group, true);
    actionToolbar.updateActionsImmediately();
    ActionToolbarUtil.makeToolbarNavigable(actionToolbar);
    myActionToolbarPanel.add(actionToolbar.getComponent(), BorderLayout.CENTER);
  }

  private void createContentPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(mySurface, BorderLayout.CENTER);
  }

  private void setEditor(@Nullable FileEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;
      mySurface.setFileEditorDelegate(editor);
      updateActionToolbar();
    }
  }

  @SuppressWarnings("unused") // Used by Kotlin plugin
  @Nullable
  public JComponent getToolbarComponent() {
    return null;
  }

  @NotNull
  public JComponent getComponent() {
    return myRoot;
  }

  @Override
  public void dispose() {
    deactivate();
    Set<VirtualFile> registeredFiles;
    myResourceNotifyingFilesLock.lock();
    try {
      registeredFiles = new HashSet<>(myResourceNotifyingFiles);
      myResourceNotifyingFiles.clear();
    }
    finally {
      myResourceNotifyingFilesLock.unlock();
    }
    for (VirtualFile file : registeredFiles) {
      unregisterResourceNotification(file);
    }
    removeAndDisposeModels(mySurface.getModels());
  }

  private void removeAndDisposeModels(@NotNull List<NlModel> models) {
    for (NlModel model : models) {
      mySurface.removeModel(model);
      Disposer.dispose(model);
    }
  }

  /**
   * Specifies the next editor the preview should be shown for.
   * The update of the preview may be delayed.
   *
   * @return true on success. False if the preview update is not possible (e.g. the file for the editor cannot be found).
   */
  public boolean setNextEditor(@NotNull FileEditor editor) {
    if (IdeResourcesUtil.getFolderType(editor.getFile()) != ResourceFolderType.LAYOUT) {
      return false;
    }
    myPendingEditor = editor;
    myFile = editor.getFile();

    myCancelPendingModelLoad.set(true);

    if (isActive) {
      initPreviewForm();
    }

    return true;
  }

  private void initPreviewForm() {
    if (myContentPanel == null) {
      // First time: Make sure we have compiled the project at least once...
      ClearResourceCacheAfterFirstBuild.getInstance(myProject)
        .runWhenResourceCacheClean(this::initPreviewFormAfterInitialBuild, this::buildError);
    }
    else {
      // Subsequent times: Setup a Nele model in preparation for creating a preview image
      initNeleModel();
    }
  }

  private void initPreviewFormAfterInitialBuild() {
    myWorkBench.setLoadingText("Waiting for build to finish...");
    SyncUtil.runWhenSmartAndSyncedOnEdt(myProject, this, result -> {
      if (result.isSuccessful()) {
        initPreviewFormAfterBuildOnEventDispatchThread();
      }
      else {
        buildError();
        SyncUtil.listenUntilNextSync(myProject, this, ignore -> initPreviewFormAfterBuildOnEventDispatchThread());
      }
    });
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped("Previews are unavailable until after a successful project sync");
  }

  private void initPreviewFormAfterBuildOnEventDispatchThread() {
    if (Disposer.isDisposed(this)) {
      return;
    }
    if (myContentPanel == null) {
      createContentPanel();
      myWorkBench.init(myContentPanel, mySurface, ImmutableList.of(), false);
      // The toolbar is in the root panel which contains myWorkBench. To traverse to toolbar we need to traverse out from myWorkBench.
      myWorkBench.setFocusCycleRoot(false);
    }
    initNeleModel();
  }

  private void initNeleModel() {
    DumbService.getInstance(myProject).smartInvokeLater(this::initNeleModelWhenSmart);
  }

  @UiThread
  private void initNeleModelWhenSmart() {
    setNoActiveModel();

    interruptRendering();

    if (myFile == null) {
      return;
    }
    PsiFile file = PsiManager.getInstance(myProject).findFile(myFile);
    AndroidFacet facet = file != null ? AndroidFacet.getInstance(file) : null;
    if (facet == null) {
      return;
    }

    updateActionToolbar();

    // isRequestCancelled allows us to cancel the ongoing computation if it is not needed anymore. There is no need to hold
    // to the Future since Future.cancel does not really interrupt the work.
    AtomicBoolean isRequestCancelled = new AtomicBoolean(false);
    myCancelPendingModelLoad = isRequestCancelled;
    // Asynchronously load the model and refresh the preview once it's ready
    CompletableFuture
      .supplyAsync(() -> {
        // Hide the content while adding the models.
        List<NlModel> models = myCurrentModelsProvider.createNlModels(this, file, facet);
        if (models.isEmpty()) {
          myWorkBench.showLoading("No Device Found");
          return null;
        }
        return models;
      }, AppExecutorUtil.getAppExecutorService()).thenAcceptAsync(models -> {
      if (models == null || isRequestCancelled.get()) {
        unregisterResourceNotification(myFile);
        myFile = null;
        return;
      }
      myFile = file.getVirtualFile();
      myWorkBench.showContent();

      interruptRendering();

      ApplicationManager.getApplication().invokeLater(() -> mySurface.registerIndicator(myProgressIndicator));
      // In visualization tool, we add model and layout the scroll pane before rendering
      models.forEach(mySurface::addModelWithoutRender);
      // Re-layout and set scale before rendering. This may be processed delayed but we have known the preview number and sizes because the
      // models are added, so it would layout correctly.
      ApplicationManager.getApplication().invokeLater(() -> {
        mySurface.invalidate();
        double lastScaling = VisualizationToolProjectSettings.getInstance(myProject).getProjectState().getScale();
        if (!mySurface.setScale(lastScaling)) {
          // Update scroll area because the scaling doesn't change, which keeps the old scroll area and may not suitable to new
          // configuration set.
          mySurface.revalidateScrollArea();
        }
      });

      // We render the model sequentially to avoid memory and performance issue.
      CompletableFuture<Void> renderFuture = renderCurrentModels();

      renderFuture.thenRunAsync(() -> {
        ApplicationManager.getApplication().invokeLater(() -> mySurface.unregisterIndicator(myProgressIndicator));
        if (!isRequestCancelled.get() && !facet.isDisposed()) {
          activateEditor(!models.isEmpty());
        }
        else {
          removeAndDisposeModels(models);
        }
      }, EdtExecutorService.getInstance());
    }, EdtExecutorService.getInstance());
  }

  // A file editor was closed. If our editor no longer exists, cleanup our state.
  public void fileClosed(@NotNull FileEditorManager editorManager, @NotNull VirtualFile file) {
    if (myEditor == null) {
      setNoActiveModel();
    }
    else if (file.equals(myFile)) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myEditor) < 0) {
        setNoActiveModel();
      }
    }
    if (myPendingEditor != null && file.equals(myPendingEditor.getFile())) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myPendingEditor) < 0) {
        myPendingEditor = null;
      }
    }
  }

  private void setNoActiveModel() {
    myCancelPendingModelLoad.set(true);
    setEditor(null);

    myWorkBench.setFileEditor(null);

    removeAndDisposeModels(mySurface.getModels());
  }

  private void activateEditor(boolean hasModel) {
    myCancelPendingModelLoad.set(true);
    if (!hasModel) {
      setEditor(null);
      myWorkBench.setFileEditor(null);
    }
    else {
      setEditor(myPendingEditor);
      myPendingEditor = null;

      registerResourceNotification(myFile);

      myWorkBench.setFileEditor(myEditor);
    }
  }

  private void registerResourceNotification(@Nullable VirtualFile file) {
    if (file == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file, myProject);
    if (facet != null) {
      myResourceNotifyingFilesLock.lock();
      try {
        if (!myResourceNotifyingFiles.add(file)) {
          // File is registered already.
          return;
        }
      }
      finally {
        myResourceNotifyingFilesLock.unlock();
      }
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myProject);
      manager.addListener(this, facet, file, null);
    }
  }

  private void unregisterResourceNotification(@Nullable VirtualFile file) {
    if (file == null) {
      return;
    }

    AndroidFacet facet = AndroidFacet.getInstance(file, myProject);
    if (facet != null) {
      myResourceNotifyingFilesLock.lock();
      try {
        if (!myResourceNotifyingFiles.remove(file)) {
          // File is not registered.
          return;
        }
      }
      finally {
        myResourceNotifyingFilesLock.unlock();
      }
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myProject);
      manager.removeListener(this, facet, myFile, null);
    }
  }

  @Override
  public void resourcesChanged(@NotNull Set<ResourceNotificationManager.Reason> reasons) {
    boolean needsRenderModels = false;
    checkReasonLoop: for (ResourceNotificationManager.Reason reason : reasons) {
      switch (reason) {
        case RESOURCE_EDIT:
        case EDIT:
        case IMAGE_RESOURCE_CHANGED:
        case GRADLE_SYNC:
        case PROJECT_BUILD:
        case VARIANT_CHANGED:
        case SDK_CHANGED:
        case CONFIGURATION_CHANGED:
          needsRenderModels = true;
          break checkReasonLoop;
        default:
          // Do nothing.
      }
    }
    if (needsRenderModels) {
      // Show and hide progress indicator during rendering.
      ApplicationManager.getApplication().invokeLater(() -> mySurface.registerIndicator(myProgressIndicator));
      renderCurrentModels().thenRun(() -> {
        ApplicationManager.getApplication().invokeLater(() -> mySurface.unregisterIndicator(myProgressIndicator));
      });
    }
  }

  @NotNull
  private CompletableFuture<Void> renderCurrentModels() {
    interruptRendering();
    AtomicBoolean isRenderingCanceled = new AtomicBoolean(false);
    Runnable cancelTask = () -> isRenderingCanceled.set(true);
    myCancelRenderingTaskLock.lock();
    try {
      myCancelRenderingTask = cancelTask;
    }
    finally {
      myCancelRenderingTaskLock.unlock();
    }
    CompletableFuture<Void> renderFuture = CompletableFuture.completedFuture(null);
    // This render the added components.
    for (SceneManager manager : mySurface.getSceneManagers()) {
      renderFuture = renderFuture.thenCompose(it -> {
        if (isRenderingCanceled.get()) {
          return CompletableFuture.completedFuture(null);
        }
        else {
          CompletableFuture<Void> modelUpdateFuture = ((LayoutlibSceneManager)manager).updateModel();
          if (isRenderingCanceled.get()) {
            return CompletableFuture.completedFuture(null);
          }
          else {
            return modelUpdateFuture.thenCompose(nothing -> manager.requestRender());
          }
        }
      });
    }
    return renderFuture;
  }

  private void interruptRendering() {
    Runnable task;
    myCancelRenderingTaskLock.lock();
    try {
      task = myCancelRenderingTask;
    }
    finally {
      myCancelRenderingTaskLock.unlock();
    }
    if (task != null) {
      task.run();
    }
  }

  @NotNull
  public NlDesignSurface getSurface() {
    return mySurface;
  }

  /**
   * Re-enables updates for this preview form. See {@link #deactivate()}
   */
  public void activate() {
    if (isActive) {
      return;
    }

    registerResourceNotification(myFile);

    isActive = true;
    initPreviewForm();
    mySurface.activate();
    getAnalyticsManager().trackVisualizationToolWindow(true);
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until {@link #activate()} is called.
   */
  public void deactivate() {
    interruptRendering();
    if (!isActive) {
      return;
    }

    myCancelPendingModelLoad.set(true);
    mySurface.deactivate();
    isActive = false;

    unregisterResourceNotification(myFile);

    if (myContentPanel != null) {
      setNoActiveModel();
    }
    getAnalyticsManager().trackVisualizationToolWindow(false);
  }

  @Override
  public void onSelectedConfigurationSetChanged(@NotNull ConfigurationSet newConfigurationSet) {
    if (myCurrentConfigurationSet != newConfigurationSet) {
      myCurrentConfigurationSet = newConfigurationSet;

      MultiViewMetricTrackerKt.trackOpenConfigSet(mySurface, myCurrentConfigurationSet);
      VisualizationToolSettings.getInstance().getGlobalState().setConfigurationSet(newConfigurationSet);
      myCurrentModelsProvider = newConfigurationSet.getModelsProviderCreator().invoke(this);
      refresh();
    }
  }

  @Override
  public void onCurrentConfigurationSetUpdated() {
    refresh();
  }

  /**
   * Refresh the previews. This recreates the {@link NlModel}s from the current {@link ConfigurationSet}.
   */
  private void refresh() {
    updateScreenMode();
    updateActionToolbar();
    // Dispose old models and create new models with new configuration set.
    initNeleModel();
  }

  private NlAnalyticsManager getAnalyticsManager() {
    return mySurface.getAnalyticsManager();
  }

  @Nullable
  public final FileEditor getEditor() {
    return myEditor;
  }

  @Override
  public void zoomChanged(double previousScale, double newScale) {
    VisualizationToolProjectSettings.getInstance(myProject).getProjectState().setScale(mySurface.getScale());
  }

  @Override
  public void panningChanged(AdjustmentEvent adjustmentEvent) {
    // Do nothing.
  }

  /**
   * An disabled action for displaying text in action toolbar.
   */
  private static final class TextLabelAction extends AnAction {

    TextLabelAction(@NotNull String text) {
      super((String)null);
      getTemplatePresentation().setText(text, false);
      getTemplatePresentation().setEnabled(false);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      // Do nothing
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(false);
    }

    @Override
    public boolean displayTextInToolbar() {
      return true;
    }
  }

  private final class ToggleShowDecorationAction extends ToggleAction {
    private ToggleShowDecorationAction() {
      super("Show System UI");
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return VisualizationToolSettings.getInstance().getGlobalState().getShowDecoration();
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      VisualizationToolSettings.getInstance().getGlobalState().setShowDecoration(state);

      mySurface.getModels().stream()
        .map(mySurface::getSceneManager)
        .filter(manager -> manager instanceof LayoutlibSceneManager)
        .forEach(manager -> ((LayoutlibSceneManager)manager).setShowDecorations(state));

      mySurface.requestRender().thenRun(() -> {
        if (!Disposer.isDisposed(myWorkBench)) {
          myWorkBench.showContent();
        }
      });
    }
  }

  private static class VisualizationTraversalPolicy extends DefaultFocusTraversalPolicy {
    @NotNull private final DesignSurface mySurface;

    private VisualizationTraversalPolicy(@NotNull DesignSurface surface) {
      mySurface = surface;
    }

    @Override
    public Component getDefaultComponent(Container aContainer) {
      return mySurface.getLayeredPane();
    }
  }

  private static class EmptyProgressIndicator extends AbstractProgressIndicatorBase {
    @Override
    public boolean isRunning() {
      return true;
    }
  }
}
