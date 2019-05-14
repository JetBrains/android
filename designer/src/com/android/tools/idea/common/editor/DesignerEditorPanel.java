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
import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.workbench.ToolWindowDefinition;
import com.android.tools.adtui.workbench.WorkBench;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.error.IssuePanelSplitter;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.util.SyncUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBSplitter;
import com.intellij.ui.OnePixelSplitter;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.EdtExecutorService;
import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

/**
 * Assembles a designer editor from various components. Subclasses should implement the {@link #getToolWindowDefinitions(AndroidFacet)}
 * method to specify which {@link ToolWindowDefinition}s should be added to the {@link WorkBench}.
 */
public class DesignerEditorPanel extends JPanel implements Disposable {

  private static final String DESIGN_UNAVAILABLE_MESSAGE = "Design editor is unavailable until after a successful project sync";


  @NotNull private final DesignerEditor myEditor;
  @NotNull private final Project myProject;
  @NotNull private final VirtualFile myFile;
  @NotNull private final DesignSurface mySurface;
  @NotNull private final JPanel myContentPanel;
  @NotNull private final WorkBench<DesignSurface> myWorkBench;
  private JBSplitter mySplitter;
  @Nullable private final JPanel myAccessoryPanel;
  /**
   * Which {@link ToolWindowDefinition} should be added to {@link #myWorkBench}.
   */
  @NotNull private final Function<AndroidFacet, List<ToolWindowDefinition<DesignSurface>>> myToolWindowDefinitions;

  public DesignerEditorPanel(@NotNull DesignerEditor editor, @NotNull Project project, @NotNull VirtualFile file,
                             @NotNull WorkBench<DesignSurface> workBench, @NotNull Function<DesignerEditorPanel, DesignSurface> surface,
                             @NotNull Function<AndroidFacet, List<ToolWindowDefinition<DesignSurface>>> toolWindowDefinitions) {
    super(new BorderLayout());
    myEditor = editor;
    myProject = project;
    myFile = file;
    myWorkBench = workBench;
    myWorkBench.setOpaque(true);

    myContentPanel = new AdtPrimaryPanel(new BorderLayout());
    mySurface = surface.apply(this);
    Disposer.register(this, mySurface);
    myAccessoryPanel = mySurface.getAccessoryPanel();
    myContentPanel.add(createSurfaceToolbar(mySurface), BorderLayout.NORTH);

    myWorkBench.setLoadingText("Loading...");

    mySplitter = new IssuePanelSplitter(mySurface, myWorkBench);
    add(mySplitter);
    Disposer.register(editor, myWorkBench);

    myToolWindowDefinitions = toolWindowDefinitions;
    ClearResourceCacheAfterFirstBuild.getInstance(project).runWhenResourceCacheClean(this::initNeleModel, this::buildError);
  }

  @NotNull
  private static JComponent createSurfaceToolbar(@NotNull DesignSurface surface) {
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

    CompletableFuture.supplyAsync(() -> {
      XmlFile file = ReadAction.compute(() -> getFile());
      AndroidFacet facet = AndroidFacet.getInstance(file);
      assert facet != null;
      return NlModel.create(myEditor, facet, myFile, mySurface.getConfigurationManager(facet), mySurface.getComponentRegistrar());
    }, AppExecutorUtil.getAppExecutorService())
      .whenComplete((model, exception) -> {
        // We are running on the AppExecutorService so wait for goingToSetModel async operation to complete
        mySurface.goingToSetModel(model).join();

        if (exception == null) {
          myWorkBench.setLoadingText("Waiting for build to finish...");
          SyncUtil.runWhenSmartAndSyncedOnEdt(myProject, this, result -> {
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
          myWorkBench.loadingStopped("Failed to initialize editor");
          Logger.getInstance(DesignerEditorPanel.class).warn("Failed to initialize DesignerEditorPanel", exception);
        }
      });
  }

  @UiThread
  private void initNeleModelOnEventDispatchThread(@NotNull NlModel model) {
    if (Disposer.isDisposed(model)) {
      return;
    }

    CompletableFuture<Void> modelSetFuture = mySurface.setModel(model);

    if (myAccessoryPanel != null) {
      boolean verticalSplitter = StudioFlags.NELE_MOTION_HORIZONTAL.get();
      OnePixelSplitter splitter = new OnePixelSplitter(verticalSplitter, 1f, 0.5f, 1f);
      splitter.setHonorComponentsMinimumSize(true);
      splitter.setFirstComponent(mySurface);
      splitter.setSecondComponent(myAccessoryPanel);
      myContentPanel.add(splitter, BorderLayout.CENTER);
    }
    else {
      myContentPanel.add(mySurface, BorderLayout.CENTER);
    }

    modelSetFuture.whenCompleteAsync(
      (result, ex) -> myWorkBench.init(myContentPanel, mySurface, myToolWindowDefinitions.apply(model.getFacet())),
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
  public DesignSurface getSurface() {
    return mySurface;
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
  }

  @NotNull
  public WorkBench<DesignSurface> getWorkBench() {
    return myWorkBench;
  }
}
