/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.common.editor;


import com.android.annotations.concurrency.UiThread;
import com.android.ide.common.rendering.api.Bridge;
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.actions.DesignerDataKeys;
import com.android.tools.idea.common.error.IssuePanelSplitter;
import com.android.tools.idea.common.lint.ModelLintIssueAnnotator;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DesignSurfaceHelper;
import com.android.tools.idea.common.surface.DesignSurfaceListener;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.editors.notifications.NotificationPanel;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.editor.NlActionManager;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider;
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider;
import com.android.tools.idea.uibuilder.type.FileTypeUtilsKt;
import com.android.tools.idea.util.SyncUtil;
import com.intellij.ProjectTopics;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.BorderLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Assembles a designer editor from various components.
 *
 * This panel has three states:
 * <li>
 *   <ul>{@link State#FULL}: The panel is in a state designed to be displayed occupying a full editor.
 *   <ul>{@link State#SPLIT}: The panel is in a state designed to share the screen with a text editor.
 *   <ul>{@link State#DEACTIVATED}: The panel completely hidden.
 * </li>
 *
 * The panel will start in the {@link State#DEACTIVATED}. Some heavy initialization might be deferred until the panel changes to one of the
 * other states.
 */
public class DesignerEditorPanel extends JPanel implements Disposable {

  private static final String DESIGN_UNAVAILABLE_MESSAGE = "Design editor is unavailable until after a successful project sync";
  private static final String ACCESSORY_PROPORTION = "AndroidStudio.AccessoryProportion";

  @NotNull private final DesignerEditor myEditor;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final DesignSurface<?> mySurface;
  @NotNull private final Map<LayoutlibSceneManager, RenderListener> mySceneManagerToRenderListeners = new HashMap<>();

  @NotNull private final ModelLintIssueAnnotator myModelLintIssueAnnotator;
  @NotNull private final Consumer<NlComponent> myComponentRegistrar;
  @NotNull private final ModelProvider myModelProvider;
  @NotNull private final MyContentPanel myContentPanel;
  @NotNull private final WorkBench<DesignSurface<?>> myWorkBench;
  private final JBSplitter mySplitter;
  @Nullable private final JPanel myAccessoryPanel;
  @Nullable private JComponent myBottomComponent;
  /**
   * Whether we listened to a gradle sync that happened after model creation. {@link #initNeleModel} calls {@link #initNeleModelWhenSmart}
   * when a Gradle Sync has happened in the project. In some situations, we want to make sure to listen to the gradle sync that happens only
   * after this model is created, so we can be sure about the state we're in. For instance, when adding a new module to the project,
   * {@link AndroidFacet} might still be null in the first call to {@link #initNeleModel}.
   */
  private boolean myGradleSyncHappenedAfterModelCreation;
  /**
   * Which {@link ToolWindowDefinition} should be added to {@link #myWorkBench}.
   */
  @NotNull private final Function<AndroidFacet, List<ToolWindowDefinition<DesignSurface<?>>>> myToolWindowDefinitions;

  /**
   * Current {@link State} of the panel.
   */
  @NotNull private State myState;

  /**
   * Whether the {@link NlModel} has been already initialized. If the preview is not visible, we will delay the full initialization and this
   * will be false until the preview is visible.
   */
  @NotNull private final AtomicBoolean myIsModelInitializated = new AtomicBoolean(false);

  /** Notification panel to be used for the surface. */
  NotificationPanel myNotificationPanel = new NotificationPanel(
    ExtensionPointName.create("com.android.tools.idea.uibuilder.editorNotificationProvider"));

  /**
   * Creates a new {@link DesignerEditorPanel}.
   *
   * @param editor the editor containing this panel.
   * @param project the project associated with the file being open by the editor.
   * @param file the file being open by the editor.
   * @param workBench workbench containing a design surface and a number of tool window definitions (also passed in the constructor).
   * @param surface a function that produces a design surface given a design editor panel. Ideally, this panel is passed to the function.
   * @param componentConsumer The registrar to enhance the given {@link NlComponent} with layout information.
   * @param modelProvider a model provider to provide a {@link NlModel} for this editor.
   * @param toolWindowDefinitions list of tool windows to be added to the workbench.
   * @param bottomModelComponent function that receives a {@link DesignSurface} and an {@link NlModel}, and returns a {@link JComponent} to
   *                             be added on the bottom of this panel. The component might be associated with the model, so we need to
   *                             listen to modelChanged events and update it as needed.
   * @param defaultEditorPanelState default {@link State] to initialize the panel to.
   */
  public DesignerEditorPanel(@NotNull DesignerEditor editor, @NotNull Project project, @NotNull VirtualFile file,
                             @NotNull WorkBench<DesignSurface<?>> workBench,
                             @NotNull Function<DesignerEditorPanel, DesignSurface<?>> surface,
                             @NotNull Consumer<NlComponent> componentConsumer,
                             @NotNull ModelProvider modelProvider,
                             @NotNull Function<AndroidFacet, List<ToolWindowDefinition<DesignSurface<?>>>> toolWindowDefinitions,
                             @Nullable BiFunction<DesignerEditorPanel, ? super NlModel, JComponent> bottomModelComponent,
                             @NotNull State defaultEditorPanelState) {
    super(new BorderLayout());
    myEditor = editor;
    myProject = project;
    myFile = file;
    myWorkBench = workBench;

    myContentPanel = new MyContentPanel();
    mySurface = surface.apply(this);
    Disposer.register(this, mySurface);
    myModelLintIssueAnnotator = new ModelLintIssueAnnotator(mySurface);
    myComponentRegistrar = componentConsumer;
    myModelProvider = modelProvider;

    myAccessoryPanel = mySurface.getAccessoryPanel();

    JComponent toolbar = createSurfaceToolbar(mySurface);
    JPanel toolbarAndNotification = new JPanel();
    toolbarAndNotification.setLayout(new BorderLayout());
    toolbarAndNotification.add(toolbar, BorderLayout.NORTH);
    toolbarAndNotification.add(myNotificationPanel, BorderLayout.SOUTH);
    myContentPanel.add(toolbarAndNotification, BorderLayout.NORTH);

    myWorkBench.setLoadingText("Loading...");

    myState = defaultEditorPanelState;
    mySurface.getAnalyticsManager().setEditorModeWithoutTracking(myState);
    onStateChange();

    // The rest of the initialization is done once the state of the surface is set to a visible state. This allows to defer the heavy
    // initialization of the model to when the user actually needs it.

    mySplitter = new IssuePanelSplitter(file, mySurface, myWorkBench);
    add(mySplitter);

    myToolWindowDefinitions = toolWindowDefinitions;

    if (bottomModelComponent != null) {
      mySurface.addListener(new DesignSurfaceListener() {
        @Override
        @UiThread
        public void modelChanged(@NotNull DesignSurface<?> surface, @Nullable NlModel model) {
          if (myBottomComponent != null) {
            myContentPanel.remove(myBottomComponent);
          }
          myBottomComponent = bottomModelComponent.apply(DesignerEditorPanel.this, model);
          if (myBottomComponent != null) {
            myContentPanel.add(myBottomComponent, BorderLayout.SOUTH);
          }
        }
      });
    }
  }

  /**
   * Sets the {@link State} of the {@link DesignSurface}.
   */
  public void setState(@NotNull State state) {
    // This is the only method that can change the state. We will forward the information to the DesignAnalyticsManager but it will not
    // record any actions. The actual user action will be recorded by the UI actions using DesignAnalyticsManager#trackEditorModeChange.
    mySurface.getAnalyticsManager().setEditorModeWithoutTracking(state);

    if (myState != state) {
      myState = state;
      onStateChange();
    }
  }

  private void onStateChange() {
    State currentState = getState();

    // Update the workbench context on state change, so we can have different contexts for each mode.
    myWorkBench.setContext(currentState.name());
    myWorkBench.setDefaultPropertiesForContext(currentState == State.SPLIT);

    if (currentState != State.DEACTIVATED && !myIsModelInitializated.getAndSet(true)) {
      // We might have delayed some initialization until the surface was not in the DEACTIVATED state. Run it now.
      ClearResourceCacheAfterFirstBuild.getInstance(myProject).runWhenResourceCacheClean(this::initNeleModel, this::buildError);
    }
  }

  @NotNull
  public State getState() {
    return myState;
  }

  public DesignerEditorPanel(@NotNull DesignerEditor editor, @NotNull Project project, @NotNull VirtualFile file,
                             @NotNull WorkBench<DesignSurface<?>> workBench, @NotNull Function<DesignerEditorPanel, DesignSurface<?>> surface,
                             @NotNull Consumer<NlComponent> componentRegistrar,
                             @NotNull Function<AndroidFacet, List<ToolWindowDefinition<DesignSurface<?>>>> toolWindowDefinitions,
                             @NotNull State defaultState) {
    this(editor, project, file, workBench, surface, componentRegistrar,
         ModelProvider.defaultModelProvider, toolWindowDefinitions, null, defaultState);
  }

  /**
   * Method called when the notifications of this panel need to be updated.
   */
  void updateNotifications(@NotNull VirtualFile file, @NotNull DesignerEditor editor, @NotNull Project project) {
    myNotificationPanel.updateNotifications(file, editor, project);
  }

  @NotNull
  private static JComponent createSurfaceToolbar(@NotNull DesignSurface<?> surface) {
    return surface.getActionManager().createToolbar();
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped(DESIGN_UNAVAILABLE_MESSAGE);
  }

  /**
   * This is called by the constructor to set up the UI, and in the normal case shouldn't be called again. However,
   * if there's an error during setup that can be detected and fixed, this should be called again afterward to retry
   * setting up the UI.
   */
  public void initNeleModel() {
    SyncUtil.runWhenSmartAndSynced(myProject, this, result -> {
      if (result.isSuccessful()) {
        initNeleModelWhenSmart();
      }
      else {
        buildError();
        SyncUtil.listenUntilNextSync(myProject, this, ignore -> initNeleModel());
      }
    });
  }

  private void initNeleModelWhenSmart() {
    if (Disposer.isDisposed(myEditor)) {
      return;
    }

    CompletableFuture.supplyAsync(this::createAndInitNeleModel, AppExecutorUtil.getAppExecutorService())
      .whenComplete((model, exception) -> {
        if (exception == null) {
          // We are running on the AppExecutorService so wait for goingToSetModel async operation to complete
          mySurface.goingToSetModel(model).join();
          myWorkBench.setLoadingText("Waiting for build to finish...");
          SyncUtil.runWhenSmartAndSyncedOnEdt(myProject, this, result -> {
            if (Bridge.hasNativeCrash()) {
              DesignSurfaceHelper.handleLayoutlibNativeCrash(myWorkBench, this::initNeleModel);
              return;
            }
            myWorkBench.setLoadingText("Initializing...");
            if (result.isSuccessful()) {
              initNeleModelOnEventDispatchThread(model);
            }
            else {
              buildError();
              SyncUtil.listenUntilNextSync(myProject, this, ignore -> initNeleModel());
            }
          });
        }
        else {
          Throwable cause = exception.getCause();
          if (cause instanceof WaitingForGradleSyncException) {
            // Expected exception. Just log the message and listen to the next Gradle sync.
            Logger.getInstance(DesignerEditorPanel.class).info(cause.getMessage());
            SyncUtil.listenUntilNextSync(myProject, this, ignore -> initNeleModel());
            myWorkBench.loadingStopped("Design editor is unavailable until next gradle sync.");
            return;
          }

          myWorkBench.loadingStopped("Failed to initialize editor.");
          Logger.getInstance(DesignerEditorPanel.class).warn("Failed to initialize DesignerEditorPanel", exception);
        }
      });
  }

  @NotNull
  private NlModel createAndInitNeleModel() {
    XmlFile file = ReadAction.compute(() -> getFile());
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet == null) {
      if (myGradleSyncHappenedAfterModelCreation) {
        throw new IllegalStateException("Could not init NlModel. AndroidFacet is unexpectedly null. " +
                                        "That might happen if the file does not belong to an Android module of the project.");
      }
      else {
        myGradleSyncHappenedAfterModelCreation = true;
        throw new WaitingForGradleSyncException("Waiting for next gradle sync to set AndroidFacet.");
      }
    }
    NlModel model = myModelProvider.createModel(myEditor, myProject, facet, myComponentRegistrar, myFile);

    Module modelModule = AndroidPsiUtils.getModuleSafely(myProject, myFile);
    // Dispose the surface if we remove the module from the project, and show some text warning the user.
    myProject.getMessageBus().connect(mySurface).subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
        if (module.equals(modelModule)) {
          Disposer.dispose(mySurface);
          myWorkBench.loadingStopped("This file does not belong to the project.");
        }
      }
    });
    return model;
  }

  @UiThread
  private void initNeleModelOnEventDispatchThread(@NotNull NlModel model) {
    if (Disposer.isDisposed(model)) {
      return;
    }

    mySurface.getModels().stream().map(mySurface::getSceneManager).filter(it -> it instanceof LayoutlibSceneManager)
      .forEach(it -> {
        LayoutlibSceneManager manager = (LayoutlibSceneManager)it;
        RenderListener listener = mySceneManagerToRenderListeners.remove(manager);
        if (listener != null) {
          manager.removeRenderListener(listener);
        }
      });
    CompletableFuture<Void> modelSetFuture = mySurface.setModel(model);
    modelSetFuture.whenComplete((result, ex) -> {
      LayoutlibSceneManager manager = (LayoutlibSceneManager)mySurface.getSceneManager(model);
      assert manager != null;
      RenderListener listener = new RenderListener() {
        @Override
        public void onRenderCompleted() {
          annotateRenderInformation();
        }

        @Override
        public void onRenderFailed(@NotNull Throwable e) {
          annotateRenderInformation();
        }

        private void annotateRenderInformation() {
          myModelLintIssueAnnotator.annotateRenderInformationToLint(model);
        }
      };
      mySceneManagerToRenderListeners.put(manager, listener);
      manager.addRenderListener(listener);
    });

    if (myAccessoryPanel != null) {
      boolean verticalSplitter = StudioFlags.NELE_MOTION_HORIZONTAL.get();
      float initialProportion = PropertiesComponent.getInstance().getFloat(ACCESSORY_PROPORTION, 0.5f);
      OnePixelSplitter splitter = new OnePixelSplitter(verticalSplitter, initialProportion, 0.05f, 0.95f);
      splitter.setHonorComponentsMinimumSize(false);
      splitter.setFirstComponent(mySurface);
      splitter.setSecondComponent(myAccessoryPanel);
      mySurface.addComponentListener(new ComponentAdapter() {
        @Override
        public void componentResized(ComponentEvent e) {
          if (myAccessoryPanel.isVisible()) {
            float proportion = splitter.getProportion();
            PropertiesComponent.getInstance().setValue(ACCESSORY_PROPORTION, proportion, 0.5f);
          }
        }
      });
      myContentPanel.add(splitter, BorderLayout.CENTER);
    }
    else {
      myContentPanel.add(mySurface, BorderLayout.CENTER);
    }

    modelSetFuture.whenCompleteAsync(
      (result, ex) -> {
        // Update the workbench context on state change, so we can have different contexts for each mode.
        myWorkBench.setContext(getState().name());
        myWorkBench.init(myContentPanel, mySurface, myToolWindowDefinitions.apply(model.getFacet()),
                         getState() == State.SPLIT);
      },
      EdtExecutorService.getInstance());
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void activate() {
    mySurface.activate();
  }

  public void deactivate() {
    mySurface.deactivate();
  }

  @NotNull
  public DesignSurface<?> getSurface() {
    return mySurface;
  }

  @NotNull
  public ModelLintIssueAnnotator getModelLintIssueAnnotator() {
    return myModelLintIssueAnnotator;
  }

  @NotNull
  private XmlFile getFile() {
    XmlFile file = (XmlFile)AndroidPsiUtils.getPsiFileSafely(myProject, myFile);
    assert file != null;
    return file;
  }

  @TestOnly
  public void setIssuePanelProportion(float proportion) {
    mySplitter.setProportion(proportion);
  }

  @Override
  public void dispose() {
    Set<LayoutlibSceneManager> keys = mySceneManagerToRenderListeners.keySet();
    for (LayoutlibSceneManager manager : keys) {
      RenderListener listener = mySceneManagerToRenderListeners.remove(manager);
      if (listener != null) {
        manager.removeRenderListener(listener);
      }
    }
  }

  @NotNull
  public WorkBench<DesignSurface<?>> getWorkBench() {
    return myWorkBench;
  }

  /**
   * Represents the {@link DesignerEditorPanel} state within the split editor. The state can be changed by the user or automatically based
   * on saved preferences.
   */
  public enum State {
    /** Surface is taking the total space of the design editor. */
    FULL,
    /** Surface is sharing the design editor horizontal space with a text editor. */
    SPLIT,
    /** Surface is deactivated and not being displayed. */
    DEACTIVATED
  }

  /**
   * Used to provide the {@link NlModel}s for the editor file.
   */
  public interface ModelProvider {

    ModelProvider defaultModelProvider = (disposable, project, facet, componentRegistrar, file) -> {
      Configuration configuration = FileTypeUtilsKt.getConfiguration(file, ConfigurationManager.getOrCreateInstance(facet.getModule()));
      NlModel model = NlModel.builder(facet, file, configuration)
        .withParentDisposable(disposable)
        .withComponentRegistrar(componentRegistrar)
        .build();
      model.setModelDisplayName(""); // For the Layout Editor, set an empty name to enable SceneView toolbars.
      return model;
    };

    /**
     * The function Create the {@link NlModel}s for the given virtual file.
     */
    NlModel createModel(@NotNull Disposable parentDisposable,
                        @NotNull Project project,
                        @NotNull AndroidFacet facet,
                        @NotNull Consumer<NlComponent> componentRegistrar,
                        @NotNull VirtualFile file);
  }

  private static class WaitingForGradleSyncException extends RuntimeException {
    private WaitingForGradleSyncException(@NotNull String message) {
      super(message);
    }
  }

  private class MyContentPanel extends AdtPrimaryPanel implements DataProvider {
    private MyContentPanel() {
      super(new BorderLayout());
    }

    @Nullable
    @Override
    public Object getData(@NotNull String dataId) {
      // This class is the parent of ActionToolBar, DesignSurface, and Accessory Panel. The data of editor actions should be provided here.
      // For example, the refresh action can be performed when focusing ActionToolBar or DesignSurface.
      if (DesignerDataKeys.DESIGN_SURFACE.is(dataId)) {
        return getSurface();
      }
      else if (NlActionManager.LAYOUT_EDITOR.is(dataId)) {
        DesignSurface<?> surface = getSurface();
        if (surface instanceof NlDesignSurface) {
          ScreenViewProvider mode = ((NlDesignSurface)surface).getScreenViewProvider();
          if (mode == NlScreenViewProvider.RENDER ||
              mode == NlScreenViewProvider.BLUEPRINT ||
              mode == NlScreenViewProvider.RENDER_AND_BLUEPRINT) {
            // This editor is Layout Editor. TODO: Can we have better condition to know if it is Layout Editor?
            return surface;
          }
        }
      }
      return null;
    }
  }
}
