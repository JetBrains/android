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
package com.android.tools.idea.uibuilder.editor;

import static com.android.tools.idea.common.model.NlModel.DELAY_AFTER_TYPING_MS;

import com.android.SdkConstants;
import com.android.annotations.concurrency.UiThread;
import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.common.StudioColorsKt;
import com.android.tools.adtui.workbench.AutoHide;
import com.android.tools.adtui.workbench.Side;
import com.android.tools.adtui.workbench.Split;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.common.editor.ActionsToolbar;
import com.android.tools.idea.common.error.IssuePanelSplitter;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutComponentHelper;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.palette.PaletteDefinition;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.uibuilder.type.AdaptiveIconFileType;
import com.android.tools.idea.util.SyncUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NlPreviewForm implements Disposable, CaretListener {

  public static final String PREVIEW_DESIGN_SURFACE = "NlPreviewFormDesignSurface";

  private final NlPreviewManager myManager;
  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final JPanel myRoot = new JPanel(new BorderLayout());
  private final MergingUpdateQueue myRenderingQueue;
  private boolean myUseInteractiveSelector = true;
  private boolean myIgnoreListener;
  private RenderResult myRenderResult;
  private VirtualFile myFile;
  private boolean isActive = false;
  private ActionsToolbar myActionsToolbar;
  private JComponent myContentPanel;
  @Nullable private AnimationToolbar myAnimationToolbar;

  private NlModel myModel;

  /**
   * Contains the editor that is currently being loaded.
   * Once the file is loaded, myPendingEditor will be null.
   */
  private TextEditor myPendingEditor;

  private TextEditor myEditor;
  private CaretModel myCaretModel;
  private SceneMode mySceneMode;
  /**
   * {@link CompletableFuture} of the next model load. This is kept so the load can be cancelled.
   */
  private AtomicBoolean myCancelPendingModelLoad = new AtomicBoolean(false);

  public NlPreviewForm(NlPreviewManager manager) {
    myManager = manager;
    myProject = myManager.getProject();
    mySurface = NlDesignSurface.builder(myProject, this)
      .setIsPreview(true)
      .build();
    Disposer.register(this, mySurface);
    mySurface.setCentered(true);
    mySurface.setScreenMode(SceneMode.SCREEN_ONLY, false);
    mySurface.setName(PREVIEW_DESIGN_SURFACE);

    myRenderingQueue =
      new MergingUpdateQueue("android.layout.preview.caret", DELAY_AFTER_TYPING_MS, true, null,
                             this, null, Alarm.ThreadToUse.SWING_THREAD);
    myRenderingQueue.setRestartTimerOnAdd(true);

    myWorkBench = new WorkBench<>(myProject, "Preview", null, this);
    myWorkBench.setLoadingText("Loading...");
    myRoot.add(new IssuePanelSplitter(mySurface, myWorkBench));
  }

  private void createContentPanel() {
    myContentPanel = new JPanel(new BorderLayout());
    myContentPanel.add(mySurface, BorderLayout.CENTER);
  }

  private void setEditor(@Nullable TextEditor editor) {
    if (editor != myEditor) {
      myEditor = editor;
      mySurface.setFileEditorDelegate(editor);

      if (myCaretModel != null) {
        myCaretModel.removeCaretListener(this);
        myCaretModel = null;
      }

      if (editor != null) {
        myCaretModel = myEditor.getEditor().getCaretModel();
        myCaretModel.addCaretListener(this);
      }
    }
  }

  private void updateCaret() {
    if (myCaretModel != null && !myIgnoreListener && myUseInteractiveSelector) {
      SceneView screenView = mySurface.getFocusedSceneView();
      if (screenView != null) {
        int offset = myCaretModel.getOffset();
        if (offset != -1) {
          ImmutableList<NlComponent> views = screenView.getModel().findByOffset(offset);
          if (views.isEmpty()) {
            views = screenView.getModel().getComponents();
          }

          // When previewing PreferenceScreen and the caret is on intent tag, change selection to its parent
          if (mySurface.isPreviewSurface() && views.size() == 1) {
            NlComponent selectedComponent = views.get(0);
            if (SdkConstants.PreferenceTags.INTENT.equals(selectedComponent.getTagName()) &&
                SdkConstants.PreferenceTags.PREFERENCE_SCREEN.equals(selectedComponent.getRoot().getTagName())) {
              NlComponent parent = selectedComponent.getParent();
              if (parent != null) {
                views = ImmutableList.of(parent);
              }
            }
          }

          try {
            myIgnoreListener = true;
            SelectionModel selectionModel = screenView.getSelectionModel();
            selectionModel.setSelection(views);
            myRenderingQueue.queue(new Update("Preview update") {
              @Override
              public void run() {
                mySurface.repaint();
              }

              @Override
              public boolean canEat(Update update) {
                return true;
              }
            });
          }
          finally {
            myIgnoreListener = false;
          }
        }
      }
    }
  }

  @SuppressWarnings("unused") // Used by Kotlin plugin
  @Nullable
  public JComponent getToolbarComponent() {
    return myActionsToolbar.getToolbarComponent();
  }

  /**
   * True if this preview form has a file assigned to display
   */
  public boolean hasFile() {
    return getFile() != null;
  }

  /**
   * Returns the current file name or null. If no file is assigned, this method will return an empty string.
   */
  @NotNull
  public String getFileName() {
    XmlFile xmlFile = getFile();
    return xmlFile != null ? xmlFile.getName() : "";
  }

  @Nullable
  private XmlFile getFile() {
    if (myFile == null) {
      return null;
    }
    return myManager.getBoundXmlFile(PsiManager.getInstance(myProject).findFile(myFile));
  }

  @NotNull
  public JComponent getComponent() {
    return myRoot;
  }

  @Override
  public void dispose() {
    deactivate();
    disposeActionsToolbar();
    disposeModel();
  }

  private void disposeModel() {
    if (myModel != null) {
      myModel.deactivate(this);
      Disposer.dispose(myModel);
      myModel = null;
      mySurface.setModel(null);
    }
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }

  /**
   * Specifies the next editor the preview should be shown for.
   * The update of the preview may be delayed.
   *
   * @return true on success. False if the preview update is not possible (e.g. the file for the editor cannot be found).
   */
  public boolean setNextEditor(@NotNull TextEditor editor) {
    if (myAnimationToolbar != null) {
      myAnimationToolbar.stop();
    }

    myPendingEditor = editor;
    myFile = editor.getFile();

    myCancelPendingModelLoad.set(true);

    if (isActive) {
      initPreviewForm();
    }

    return true;
  }

  public void clearRenderResult() {
    XmlFile file = getFile();
    if (file == null) {
      myRenderResult = null;
    } else if (myRenderResult != null && myRenderResult.getFile() != file) {
      myRenderResult = RenderResult.createBlank(file);
    }
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
    myWorkBench.loadingStopped("Preview is unavailable until after a successful project sync");
  }

  private void initPreviewFormAfterBuildOnEventDispatchThread() {
    if (Disposer.isDisposed(this)) {
      return;
    }
    if (myContentPanel == null) {
      createContentPanel();
      List<ToolWindowDefinition<DesignSurface>> tools = new ArrayList<>(4);
      tools.add(new PaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.AUTO_HIDE));
      myWorkBench.init(myContentPanel, mySurface, tools);
    }
    initNeleModel();
  }

  private void initAnimationsToolbar() {
    if (myAnimationToolbar != null) {
      myContentPanel.remove(myAnimationToolbar);
    }

    boolean animationsBarEnabled = StudioFlags.NELE_ANIMATIONS_PREVIEW.get() ||
                                   StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get();

    if (!animationsBarEnabled || myModel == null) {
      myAnimationToolbar = null;
      return;
    }

    if (StudioFlags.NELE_MOTION_LAYOUT_ANIMATIONS.get()) {
      // Find if there is a transtion layout
      NlComponent transitionLayout = myModel.flattenComponents()
        .filter(component -> NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT))
        .findAny()
        .orElse(null);
      MotionLayoutComponentHelper helper = transitionLayout != null ?
                                           new MotionLayoutComponentHelper(transitionLayout) :
                                           null;
      long maxTimeMs = helper != null ? helper.getMaxTimeMs() : -1;


      // All animations are offset to start at 500ms. The reason is that some animated drawables like the progress bars are not
      // really visible at 0 since their animation starts in an empty state. 500ms works well for progress bars.
      if (helper != null) {
        myAnimationToolbar = AnimationToolbar.createAnimationToolbar(this, (timeMs) -> {
          LayoutlibSceneManager sceneManager = mySurface.getSceneManager();
          if (myModel != null && sceneManager != null) {
            sceneManager.setElapsedFrameTimeMs(timeMs);
            helper.setProgress((timeMs - 500L) / (float)maxTimeMs);
          }
        }, 16, 500L, maxTimeMs + 500L);
      }
    }

    if (myAnimationToolbar == null) {
      myAnimationToolbar = AnimationToolbar.createUnlimitedAnimationToolbar(this, (timeMs) -> {
        LayoutlibSceneManager sceneManager = mySurface.getSceneManager();
        if (myModel != null && sceneManager != null) {
          sceneManager.setElapsedFrameTimeMs(timeMs);
          sceneManager.requestRender();
        }
      }, 16, 500L);
    }

    myAnimationToolbar.setBackground(StudioColorsKt.getPrimaryPanelBackground());
    myAnimationToolbar.setOpaque(true);
    myContentPanel.add(myAnimationToolbar, BorderLayout.SOUTH);
  }

  private void initNeleModel() {
    DumbService.getInstance(myProject).smartInvokeLater(() -> initNeleModelWhenSmart());
  }

  @UiThread
  private void initNeleModelWhenSmart() {
    setActiveModel(null);

    XmlFile xmlFile = getFile();
    AndroidFacet facet = xmlFile != null ? AndroidFacet.getInstance(xmlFile) : null;
    myCancelPendingModelLoad.set(true);

    if (facet == null) {
      return;
    }

    // Asynchronously load the model and refresh the preview once it's ready

    // isRequestCancelled allows us to cancel the ongoing computation if it is not needed anymore. There is no need to hold
    // to the Future since Future.cancel does not really interrupt the work.
    AtomicBoolean isRequestCancelled = new AtomicBoolean(false);
    myCancelPendingModelLoad = isRequestCancelled;
    CompletableFuture
      .supplyAsync(() -> NlModel.create(null, null, facet, xmlFile.getVirtualFile(), mySurface.getComponentRegistrar()))
      .thenAcceptAsync(model -> {
        // Set the default density to XXXHDPI for adaptive icon preview
        if (model.getType() == AdaptiveIconFileType.INSTANCE) {
          Device device = model.getConfiguration().getDevice();
          if (device != null && !NlModelHelperKt.CUSTOM_DENSITY_ID.equals(device.getId())) {
            NlModelHelperKt.overrideConfigurationDensity(model, Density.XXXHIGH);
          }
        }

        if (isRequestCancelled.get()) {
          Disposer.dispose(model);
          return;
        }

        // This will trigger a render of the model
        mySurface.setModel(model).thenRunAsync(() -> {
          if (!isRequestCancelled.get() && !facet.isDisposed()) {
            setActiveModel(model);
          }
          else {
            Disposer.dispose(model);
          }
        }, EdtExecutorService.getInstance());
      }, EdtExecutorService.getInstance());
  }

  // A file editor was closed. If our editor no longer exists, cleanup our state.
  public void fileClosed(@NotNull FileEditorManager editorManager, @NotNull VirtualFile file) {
    if (myEditor != null && file.equals(myFile)) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myEditor) < 0) {
        setActiveModel(null);
      }
    }
    if (myPendingEditor != null && file.equals(myPendingEditor.getFile())) {
      if (ArrayUtil.find(editorManager.getAllEditors(file), myPendingEditor) < 0) {
        myPendingEditor = null;
      }
    }
  }

  @SuppressWarnings("WeakerAccess") // This method needs to be public as it's used by the Anko DSL preview
  public void setActiveModel(@Nullable NlModel model) {
    myCancelPendingModelLoad.set(true);
    if (myModel != model) {
      disposeModel();
      myModel = model;
    }
    if (model == null) {
      setEditor(null);
      disposeActionsToolbar();

      myWorkBench.setToolContext(null);
      myWorkBench.setFileEditor(null);
    }
    else {
      myFile = model.getVirtualFile();
      if (!mySurface.isCanvasResizing()) {
        // If we are resizing, keep the zoom level constant
        // only if the zoom was previously set to FIT
        mySurface.zoomToFit();
      }
      else {
        mySurface.updateScrolledAreaSize();
      }
      setEditor(myPendingEditor);
      myPendingEditor = null;

      model.activate(this);
      myWorkBench.setToolContext(mySurface);
      myWorkBench.setFileEditor(myEditor);

      disposeActionsToolbar();

      myActionsToolbar = new ActionsToolbar(mySurface, mySurface);

      myContentPanel.add(myActionsToolbar.getToolbarComponent(), BorderLayout.NORTH);

      if (!model.getType().isEditable()) {
        mySceneMode = mySurface.getSceneMode();
        mySurface.setScreenMode(SceneMode.SCREEN_ONLY, false);
        myWorkBench.setMinimizePanelsVisible(false);
      }
      else if (mySceneMode != null && mySurface.getSceneMode() == SceneMode.SCREEN_ONLY) {
        mySurface.setScreenMode(mySceneMode, false);
        myWorkBench.setMinimizePanelsVisible(true);
      }
    }
    updateCaret(); // otherwise the selection stays on the previous model.
    initAnimationsToolbar();
  }

  private void disposeActionsToolbar() {
    if (myActionsToolbar == null) {
      return;
    }

    myContentPanel.remove(myActionsToolbar.getToolbarComponent());

    Disposer.dispose(myActionsToolbar);
    myActionsToolbar = null;
  }

  @NotNull
  public NlDesignSurface getSurface() {
    return mySurface;
  }

  // ---- Implements CaretListener ----

  @Override
  public void caretPositionChanged(@NotNull CaretEvent e) {
    if (!myIgnoreListener) {
      updateCaret();
      // TODO: implement
      //ActionBarHandler.showMenu(false, myContext, true);
    }
  }

  /**
   * Re-enables updates for this preview form. See {@link #deactivate()}
   */
  public void activate() {
    if (isActive) {
      return;
    }

    isActive = true;
    initPreviewForm();
    mySurface.activate();
  }

  /**
   * Disables the updates for this preview form. Any changes to resources or the layout won't update
   * this preview until {@link #activate()} is called.
   */
  public void deactivate() {
    if (!isActive) {
      return;
    }

    myCancelPendingModelLoad.set(true);
    mySurface.deactivate();
    isActive = false;
    if (myContentPanel != null) {
      setActiveModel(null);
    }
  }

  @Nullable
  public final TextEditor getEditor() {
    return myEditor;
  }
}
