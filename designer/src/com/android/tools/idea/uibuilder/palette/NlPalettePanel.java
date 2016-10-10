/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class NlPalettePanel extends JPanel implements Disposable, DataProvider {
  private final Project myProject;
  private final NlPreviewPanel myPreviewPane;
  private final CopyProvider myCopyProvider;
  private final NlPaletteTreeGrid myPalettePanel;
  private final DependencyManager myDependencyManager;
  private DesignSurface myDesignSurface;

  public NlPalettePanel(@NotNull Project project, @Nullable DesignSurface designSurface) {
    myProject = project;
    myDependencyManager = new DependencyManager(project, this, this);
    myPalettePanel = new NlPaletteTreeGrid(project, myDependencyManager);
    myPreviewPane = new NlPreviewPanel(new NlPreviewImagePanel(myDependencyManager));
    myCopyProvider = new CopyProviderImpl();
    myPalettePanel.setSelectionListener(myPreviewPane);

    Splitter splitter = new Splitter(true, 0.8f);
    splitter.setFirstComponent(myPalettePanel);
    splitter.setSecondComponent(myPreviewPane);

    setLayout(new BorderLayout());
    add(splitter, BorderLayout.CENTER);

    setDesignSurface(designSurface);
  }

  @Override
  public void requestFocus() {
    myPalettePanel.requestFocus();
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myPreviewPane.setDesignSurface(designSurface);
    DesignSurface oldDesignSurface = myDesignSurface;
    myDesignSurface = designSurface;
    Module module = getModule(designSurface);
    if (designSurface != null && module != null &&
        (oldDesignSurface == null || designSurface.getLayoutType() != oldDesignSurface.getLayoutType())) {
      NlPaletteModel model = NlPaletteModel.get(myProject);
      Palette palette = model.getPalette(myDesignSurface.getLayoutType());
      myPalettePanel.populateUiModel(palette, designSurface);
      myDependencyManager.setPalette(palette, module);
      repaint();
    }
  }

  @Nullable
  private static Module getModule(@Nullable DesignSurface designSurface) {
    Configuration configuration =
      designSurface != null && designSurface.getLayoutType().isSupportedByDesigner() ? designSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @NotNull
  public PaletteMode getMode() {
    return myPalettePanel.getMode();
  }

  public void setMode(@NotNull PaletteMode mode) {
    myPalettePanel.setMode(mode);
  }

  @Override
  public void dispose() {
    Disposer.dispose(myPreviewPane);
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    return PlatformDataKeys.COPY_PROVIDER.is(dataId) ? myCopyProvider : null;
  }

  private class CopyProviderImpl implements CopyProvider {

    @Override
    public void performCopy(@NotNull DataContext dataContext) {
      Palette.Item item = myPalettePanel.getSelectedItem();
      if (item != null && !myDependencyManager.needsLibraryLoad(item)) {
        DnDTransferComponent component = new DnDTransferComponent(item.getTagName(), item.getXml(), 0, 0);
        CopyPasteManager.getInstance().setContents(new ItemTransferable(new DnDTransferItem(component)));
      }
    }

    @Override
    public boolean isCopyEnabled(@NotNull DataContext dataContext) {
      Palette.Item item = myPalettePanel.getSelectedItem();
      return item != null && !myDependencyManager.needsLibraryLoad(item);
    }

    @Override
    public boolean isCopyVisible(@NotNull DataContext dataContext) {
      return true;
    }
  }
}
