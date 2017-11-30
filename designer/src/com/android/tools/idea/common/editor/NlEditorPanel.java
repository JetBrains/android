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
package com.android.tools.idea.common.editor;

import com.android.tools.adtui.common.AdtPrimaryPanel;
import com.android.tools.adtui.workbench.*;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.naveditor.property.NavPropertyPanelDefinition;
import com.android.tools.idea.naveditor.structure.DestinationList;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.uibuilder.error.IssuePanelSplitter;
import com.android.tools.idea.startup.ClearResourceCacheAfterFirstBuild;
import com.android.tools.idea.uibuilder.mockup.editor.MockupToolDefinition;
import com.android.tools.idea.uibuilder.palette.NlPaletteDefinition;
import com.android.tools.idea.uibuilder.palette2.PaletteDefinition;
import com.android.tools.idea.uibuilder.property.NlPropertyPanelDefinition;
import com.android.tools.idea.uibuilder.structure.NlComponentTreeDefinition;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.ui.JBSplitter;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends JPanel implements Disposable {
  private final NlEditor myEditor;
  private final Project myProject;
  private final VirtualFile myFile;
  private final DesignSurface mySurface;
  private final JPanel myContentPanel;
  private final WorkBench<DesignSurface> myWorkBench;
  private boolean myIsActive;
  private JBSplitter mySplitter;

  public NlEditorPanel(@NotNull NlEditor editor, @NotNull Project project, @NotNull VirtualFile file) {
    super(new BorderLayout());
    myWorkBench = new WorkBench<>(project, "NELE_EDITOR", editor);
    myWorkBench.setOpaque(true);

    myEditor = editor;
    myProject = project;
    myFile = file;
    myContentPanel = new AdtPrimaryPanel(new BorderLayout());

    if (NlLayoutType.typeOf(getFile()) == NlLayoutType.NAV) {
      mySurface = new NavDesignSurface(project, editor);
    }
    else {
      mySurface = new NlDesignSurface(project, false, editor);
      ((NlDesignSurface)mySurface).setCentered(true);
    }

    myWorkBench.setLoadingText("Waiting for build to finish...");
    ClearResourceCacheAfterFirstBuild.getInstance(project).runWhenResourceCacheClean(this::initNeleModel, this::buildError);

    mySplitter = new IssuePanelSplitter(mySurface, myWorkBench);
    add(mySplitter);
    Disposer.register(editor, myWorkBench);
  }

  // Build was either cancelled or there was an error
  private void buildError() {
    myWorkBench.loadingStopped("Design editor is unavailable until a successful build");
  }

  private void initNeleModel() {
    DumbService.getInstance(myProject).smartInvokeLater(this::initNeleModelOnEventDispatchThread);
  }

  private void initNeleModelOnEventDispatchThread() {
    if (Disposer.isDisposed(myEditor) || myContentPanel.getComponentCount() > 0) {
      return;
    }
    XmlFile file = getFile();

    AndroidFacet facet = AndroidFacet.getInstance(file);
    assert facet != null;
    NlModel model = NlModel.create(myEditor, facet, myFile);
    mySurface.setModel(model);
    Disposer.register(myEditor, mySurface);

    JComponent toolbarComponent = mySurface.getActionManager().createToolbar(model);
    myContentPanel.add(toolbarComponent, BorderLayout.NORTH);
    myContentPanel.add(mySurface);

    List<ToolWindowDefinition<DesignSurface>> tools = new ArrayList<>(4);
    // TODO: factor out tool creation
    if (NlLayoutType.typeOf(file) == NlLayoutType.NAV) {
      tools.add(new NavPropertyPanelDefinition(facet, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
      tools.add(new DestinationList.DestinationListDefinition());
    }
    else {
      if (StudioFlags.NELE_NEW_PALETTE.get()) {
        tools.add(new PaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.DOCKED));
      }
      else {
        tools.add(new NlPaletteDefinition(myProject, Side.LEFT, Split.TOP, AutoHide.DOCKED));
      }
      tools.add(new NlPropertyPanelDefinition(facet, Side.RIGHT, Split.TOP, AutoHide.DOCKED));
      tools.add(new NlComponentTreeDefinition(myProject, Side.LEFT, Split.BOTTOM, AutoHide.DOCKED));
      if (StudioFlags.NELE_MOCKUP_EDITOR.get()) {
        tools.add(new MockupToolDefinition(Side.RIGHT, Split.TOP, AutoHide.AUTO_HIDE));
      }
    }

    myWorkBench.init(myContentPanel, mySurface, tools);
    if (myIsActive) {
      model.activate(myWorkBench);
    }
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void activate() {
    mySurface.activate();
    myIsActive = true;
  }

  public void deactivate() {
    mySurface.deactivate();
    myIsActive = false;
  }

  @NotNull
  public DesignSurface getSurface() {
    return mySurface;
  }

  @NotNull
  public XmlFile getFile() {
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
}
