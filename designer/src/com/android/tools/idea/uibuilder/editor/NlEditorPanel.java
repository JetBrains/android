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
import com.intellij.ide.CopyProvider;
import com.intellij.ide.CutProvider;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Assembles a designer editor from various components
 */
public class NlEditorPanel extends JPanel implements DataProvider {
  private final DesignSurface mySurface;

  public NlEditorPanel(@NonNull NlEditor editor, @NonNull AndroidFacet facet, @NonNull VirtualFile file) {
    super(new BorderLayout());
    setOpaque(true);

    XmlFile xmlFile = (XmlFile)AndroidPsiUtils.getPsiFileSafely(facet.getModule().getProject(), file);
    assert xmlFile != null : file;
    NlModel model = NlModel.create(editor, facet, xmlFile);

    mySurface = new DesignSurface(model);
    add(mySurface, BorderLayout.CENTER);
    model.requestRender();
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

  private static class ActionHandler implements DeleteProvider, CutProvider, CopyProvider, PasteProvider {
    private final NlEditorPanel myEditor;

    public ActionHandler(NlEditorPanel panel) {
      myEditor = panel;
    }

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      return !myEditor.getSurface().getCurrentScreenView().getSelectionModel().isEmpty();
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public void performCut(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean isCutEnabled(@NotNull DataContext dataContext) {
      return !myEditor.getSurface().getCurrentScreenView().getSelectionModel().isEmpty();
    }

    @Override
    public boolean isCutVisible(@NotNull DataContext dataContext) {
      return false;
    }

    @Override
    public void deleteElement(@NotNull DataContext dataContext) {
      DesignSurface surface = myEditor.getSurface();
      ScreenView screenView = surface.getCurrentScreenView();
      SelectionModel selectionModel = screenView.getSelectionModel();
      NlModel model = screenView.getModel();
      model.delete(Lists.newArrayList(selectionModel.getSelection()));
      model.requestRender();
    }

    @Override
    public boolean canDeleteElement(@NotNull DataContext dataContext) {
      return !myEditor.getSurface().getCurrentScreenView().getSelectionModel().isEmpty();
    }

    @Override
    public void performPaste(@NotNull DataContext dataContext) {
    }

    @Override
    public boolean isPastePossible(@NotNull DataContext dataContext) {
      // TODO: Look at clipboard
      return false;
    }

    @Override
    public boolean isPasteEnabled(@NotNull DataContext dataContext) {
      // TODO: Look at clipboard
      return false;
    }
  }
}
