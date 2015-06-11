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

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.tools.idea.AndroidPsiUtils;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.Lists;
import com.intellij.designer.DesignerEditorPanelFacade;
import com.intellij.designer.LightFillLayout;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ThreeComponentsSplitter;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends JPanel implements DesignerEditorPanelFacade, DataProvider {
  private final DesignSurface mySurface;
  private final ThreeComponentsSplitter myContentSplitter;

  public NlEditorPanel(@NonNull NlEditor editor, @NonNull AndroidFacet facet, @NonNull VirtualFile file) {
    super(new BorderLayout());
    setOpaque(true);

    final Project project = facet.getModule().getProject();
    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(project, file);
    assert xmlFile != null : file;

    mySurface = new DesignSurface(project);
    NlModel model = NlModel.create(mySurface, editor, facet, xmlFile);
    mySurface.setModel(model);

    myContentSplitter = new ThreeComponentsSplitter();
    Disposer.register(editor, myContentSplitter);

    // The {@link LightFillLayout} provides the UI for the minimized forms of the {@link LightToolWindow}
    // used for the palette and the structure/properties panes.
    JPanel contentPanel = new JPanel(new LightFillLayout());
    JLabel toolbar = new JLabel();
    toolbar.setVisible(false);
    contentPanel.add(toolbar);
    contentPanel.add(mySurface);

    myContentSplitter.setDividerWidth(0);
    myContentSplitter.setDividerMouseZoneSize(Registry.intValue("ide.splitter.mouseZone"));
    myContentSplitter.setInnerComponent(contentPanel);
    add(myContentSplitter, BorderLayout.CENTER);

    // When you're opening the layout editor we don't want to delay anything
    model.requestRenderAsap();
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return mySurface.getPreferredFocusedComponent();
  }

  public void dispose() {
  }

  public void activate() {
    mySurface.activate();
  }

  public void deactivate() {
    mySurface.deactivate();
  }

  public DesignSurface getSurface() {
    return mySurface;
  }

  @Override
  public Object getData(@NonNls String dataId) {
    if (PlatformDataKeys.DELETE_ELEMENT_PROVIDER.is(dataId) ||
        PlatformDataKeys.CUT_PROVIDER.is(dataId) ||
        PlatformDataKeys.COPY_PROVIDER.is(dataId) ||
        PlatformDataKeys.PASTE_PROVIDER.is(dataId)) {
      return new ActionHandler(this);
    }
    return null;
  }

  @Override
  public ThreeComponentsSplitter getContentSplitter() {
    return myContentSplitter;
  }

  private static class ActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
    private final NlEditorPanel myEditor;

    public ActionHandler(NlEditorPanel panel) {
      myEditor = panel;
    }

    @Override
    public void performCopy(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean isCopyEnabled(@NonNull DataContext dataContext) {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      return screenView != null && !screenView.getSelectionModel().isEmpty();
    }

    @Override
    public boolean isCopyVisible(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public void performCut(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean isCutEnabled(@NonNull DataContext dataContext) {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      return screenView != null && !screenView.getSelectionModel().isEmpty();
    }

    @Override
    public boolean isCutVisible(@NonNull DataContext dataContext) {
      return false;
    }

    @Override
    public void deleteElement(@NonNull DataContext dataContext) {
      DesignSurface surface = myEditor.getSurface();
      ScreenView screenView = surface.getCurrentScreenView();
      if (screenView == null) {
        return;
      }
      SelectionModel selectionModel = screenView.getSelectionModel();
      NlModel model = screenView.getModel();
      model.delete(selectionModel.getSelection());
      model.requestRender();
    }

    @Override
    public boolean canDeleteElement(@NonNull DataContext dataContext) {
      ScreenView screenView = myEditor.getSurface().getCurrentScreenView();
      return screenView != null && !screenView.getSelectionModel().isEmpty();
    }

    @Override
    public void performPaste(@NonNull DataContext dataContext) {
    }

    @Override
    public boolean isPastePossible(@NonNull DataContext dataContext) {
      // TODO: Look at clipboard
      return false;
    }

    @Override
    public boolean isPasteEnabled(@NonNull DataContext dataContext) {
      // TODO: Look at clipboard
      return false;
    }
  }
}
