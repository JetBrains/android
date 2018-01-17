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
package com.android.tools.idea.uibuilder.editor;

import com.android.SdkConstants;
import com.android.resources.Density;
import com.android.sdklib.devices.Device;
import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.common.editor.ActionsToolbar;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.projectsystem.ProjectSystemSyncManager;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.uibuilder.error.IssuePanelSplitter;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.handlers.motion.MotionLayoutHandler;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.palette2.PaletteDefinition;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.scene.RenderListener;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.SceneMode;
import com.android.tools.idea.util.SyncUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.event.CaretEvent;
import com.intellij.openapi.editor.event.CaretListener;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NlPreviewForm implements Disposable, CaretListener {

  public static final String PREVIEW_DESIGN_SURFACE = "NlPreviewFormDesignSurface";

  private final NlPreviewManager myManager;
  private final Project myProject;
  private final NlDesignSurface mySurface;
  private final WorkBench<DesignSurface> myWorkBench;
  private final JPanel myRoot = new JPanel(new BorderLayout());
  private final MergingUpdateQueue myRenderingQueue =
    new MergingUpdateQueue("android.layout.preview.caret", 250/*ms*/, true, null, this, null, Alarm.ThreadToUse.SWING_THREAD);
  private boolean myUseInteractiveSelector = true;
  private boolean myIgnoreListener;
  private RenderResult myRenderResult;
  private XmlFile myFile;
  private boolean isActive = false;
  private ActionsToolbar myActionsToolbar;
  private JComponent myContentPanel;
  @Nullable private AnimationToolbar myAnimationToolbar;

  private NlModel myModel;

  /**
   * Contains the file that is currently being loaded (it might take a while to get a preview rendered).
   * Once the file is loaded, myPendingFile will be null.
   */
  private Pending myPendingFile;
  /**
   * Contains the editor that is currently being loaded.
   * Once the file is loaded, myPendingEditor will be null.
   */
  private TextEditor myPendingEditor;
  private TextEditor myEditor;
  private CaretModel myCaretModel;
  private SceneMode mySceneMode;

  public NlPreviewForm(NlPreviewManager manager) {
    myManager = manager;
    myProject = myManager.getProject();
    mySurface = new NlDesignSurface(myProject, true, this);
    Disposer.register(this, mySurface);
    mySurface.setCentered(true);
    mySurface.setScreenMode(SceneMode.SCREEN_ONLY, false);
    mySurface.setName(PREVIEW_DESIGN_SURFACE);

    myRenderingQueue.setRestartTimerOnAdd(true);

    myWorkBench = new WorkBench<>(myProject, "Preview", null);
    myWorkBench.setLoadingText("Waiting for build to finish...");
    myRoot.add(new IssuePanelSplitter(mySurface, myWorkBench));

    Disposer.register(this, myWorkBench);
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
      SceneView screenView = mySurface.getCurrentSceneView();
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

  @Nullable
  public XmlFile getFile() {
    return myFile;
  }

  @NotNull
  public JComponent getComponent() {
    return myRoot;
  }

  @Override
  public void dispose() {
    deactivate();
    disposeActionsToolbar();

    if (myModel != null) {
      Disposer.dispose(myModel);
      myModel = null;
    }
  }

  public void setUseInteractiveSelector(boolean useInteractiveSelector) {
    this.myUseInteractiveSelector = useInteractiveSelector;
  }

  private class Pending implements RenderListener, Runnable {
    public final XmlFile file;
    public final NlModel model;
    public final SceneView screenView;
    public boolean valid = true;

    public Pending(XmlFile file, NlModel model) {
      this.file = file;
      this.model = model;
      screenView = mySurface.getCurrentSceneView();
      if (screenView != null) {
        screenView.getSceneManager().addRenderListener(this);
        screenView.getSceneManager().requestRender();
      }
    }

    @Override
    public void onRenderCompleted() {
      if (screenView != null) {
        screenView.getSceneManager().removeRenderListener(this);
      }
      if (valid) {
        valid = false;
        ApplicationManager.getApplication().invokeLater(this, model.getProject().getDisposed());
      }
    }

    public void invalidate() {
      valid = false;
    }

    @Override
    public void run() {
      // This method applies the given pending update to the UI thread; this must be done from a read thread
      ApplicationManager.getApplication().assertIsDispatchThread();
      setActiveModel(model);
    }
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

    PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(editor.getEditor().getDocument());
    if (psiFile == null) {
      return false;
    }

    myPendingEditor = editor;
    myFile = myManager.getBoundXmlFile(psiFile);

    if (myPendingFile != null) {
      myPendingFile.invalidate();
      // Set the model to null so the progressbar is displayed
      // TODO: find another way to decide that the progress indicator should be shown, so that the design surface model can be non-null
      // mySurface.setModel(null);
    }

    if (isActive) {
      initPreviewForm();
    }

    return true;
  }

  public void clearRenderResult() {
    if (myRenderResult != null && myRenderResult.getFile() != myFile) {
      myRenderResult = RenderResult.createBlank(myFile);
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
    ProjectSystemSyncManager syncManager = ProjectSystemUtil.getSyncManager(myProject);

    if (!syncManager.isSyncInProgress()) {
      if (syncManager.getLastSyncResult().isSuccessful()) {
        UIUtil.invokeLaterIfNeeded(this::initPreviewFormAfterBuildOnEventDispatchThread);
        return;
      }
      else {
        buildError();
      }
    }

    // Wait for a successful sync in case the module containing myFile was
    // just added and the Android facet isn't available yet.
    SyncUtil.listenUntilNextSuccessfulSync(myProject, this, result -> {
      if (result.isSuccessful()) {
        UIUtil.invokeLaterIfNeeded(this::initPreviewFormAfterBuildOnEventDispatchThread);
      }
      else {
        buildError();
      }
    });
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped("Preview is unavailable until a successful build");
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
                                   StudioFlags.NELE_TRANSITION_LAYOUT_ANIMATIONS.get();

    if (!animationsBarEnabled || myModel == null) {
      myAnimationToolbar = null;
      return;
    }

    if (StudioFlags.NELE_TRANSITION_LAYOUT_ANIMATIONS.get()) {
      // Find if there is a transtion layout
      NlComponent transitionLayout = myModel.flattenComponents()
        .filter(component -> NlComponentHelperKt.isOrHasSuperclass(component, SdkConstants.MOTION_LAYOUT))
        .findAny()
        .orElse(null);
      MotionLayoutHandler.MotionLayoutComponentHelper helper = transitionLayout != null ?
                                                               new MotionLayoutHandler.MotionLayoutComponentHelper(transitionLayout) :
                                                               null;
      long maxTimeMs = helper != null ? helper.getMaxTimeMs() : -1;


      // All animations are offset to start at 500ms. The reason is that some animated drawables like the progress bars are not
      // really visible at 0 since their animation starts in an empty state. 500ms works well for progress bars.
      if (helper != null) {
        myAnimationToolbar = AnimationToolbar.createAnimationToolbar(this, (timeMs) -> {
          LayoutlibSceneManager sceneManager = mySurface.getSceneManager();
          if (myModel != null && sceneManager != null) {
            sceneManager.setElapsedFrameTimeMs(timeMs);
            helper.setValue((timeMs - 500L) / (float)maxTimeMs);
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

    myContentPanel.add(myAnimationToolbar, BorderLayout.SOUTH);
  }

  private void initNeleModel() {
    XmlFile xmlFile = myFile;
    AndroidFacet facet = xmlFile != null ? AndroidFacet.getInstance(xmlFile) : null;
    if (!isActive || facet == null || xmlFile.getVirtualFile() == null) {
      myPendingFile = null;
      setActiveModel(null);
    }
    else {
      if (myModel != null) {
        Disposer.dispose(myModel);
      }
      myModel = NlModel.create(null, facet, xmlFile.getVirtualFile());

      mySurface.setModel(myModel);
      myPendingFile = new Pending(xmlFile, myModel);

      // Set the default density to XXXHDPI for adaptive icon preview
      if (myModel.getType() == NlLayoutType.ADAPTIVE_ICON) {
        Device device = myModel.getConfiguration().getDevice();
        if (device != null && !NlModelHelperKt.CUSTOM_DENSITY_ID.equals(device.getId())) {
          NlModelHelperKt.overrideConfigurationDensity(myModel, Density.XXXHIGH);
        }
      }
    }
  }

  @SuppressWarnings("WeakerAccess") // This method needs to be public as it's used by the Anko DSL preview
  public void setActiveModel(@Nullable NlModel model) {
    myPendingFile = null;
    SceneView currentScreenView = mySurface.getCurrentSceneView();
    if (currentScreenView != null) {
      NlModel oldModel = currentScreenView.getModel();
      if (model != oldModel) {
        oldModel.deactivate(this);
        Disposer.dispose(oldModel);
      }
    }

    if (model == null) {
      setEditor(null);
      disposeActionsToolbar();

      myWorkBench.setToolContext(null);
    }
    else {
      myFile = model.getFile();
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

      myActionsToolbar = new ActionsToolbar(null, mySurface);
      myActionsToolbar.setModel(model);

      myContentPanel.add(myActionsToolbar.getToolbarComponent(), BorderLayout.NORTH);

      if (!model.getType().isSupportedByDesigner()) {
        mySceneMode = mySurface.getSceneMode();
        mySurface.setScreenMode(SceneMode.SCREEN_ONLY, false);
        myWorkBench.setMinimizePanelsVisible(false);
      }
      else if (mySceneMode != null && mySurface.getSceneMode() == SceneMode.SCREEN_ONLY) {
        mySurface.setScreenMode(mySceneMode, false);
        myWorkBench.setMinimizePanelsVisible(true);
      }
    }

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
  public void caretPositionChanged(CaretEvent e) {
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

    mySurface.deactivate();
    isActive = false;
    if (myContentPanel != null) {
      myPendingFile = null;
      setActiveModel(null);
    }
  }
}
