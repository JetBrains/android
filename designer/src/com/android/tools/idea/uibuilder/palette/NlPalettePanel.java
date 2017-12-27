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

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.workbench.StartFilteringListener;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class NlPalettePanel extends JPanel implements Disposable, DataProvider, ToolContent<DesignSurface> {
  static final String PALETTE_MODE = "palette.mode";

  private final CopyProvider myCopyProvider;
  private final NlPaletteTreeGrid myPalettePanel;
  private final DependencyManager myDependencyManager;
  private final CopyPasteManager myCopyPasteManager;
  private NlLayoutType myLayoutType;
  private Runnable myCloseAutoHideCallback;

  public NlPalettePanel(@NotNull Project project, @Nullable NlDesignSurface designSurface) {
    this(project, designSurface, CopyPasteManager.getInstance());
  }

  @VisibleForTesting
  NlPalettePanel(@NotNull Project project, @Nullable NlDesignSurface designSurface, @NotNull CopyPasteManager copyPasteManager) {
    myCopyPasteManager = copyPasteManager;
    IconPreviewFactory iconPreviewFactory = new IconPreviewFactory();
    Disposer.register(this, iconPreviewFactory);
    myDependencyManager = new DependencyManager(project, this, this);
    myPalettePanel = new NlPaletteTreeGrid(
      project, getInitialMode(), myDependencyManager, this::closeAutoHideToolWindow, designSurface, iconPreviewFactory);
    myCopyProvider = new CopyProviderImpl();
    myLayoutType = NlLayoutType.UNKNOWN;

    Disposer.register(this, myPalettePanel);

    setLayout(new BorderLayout());
    add(myPalettePanel, BorderLayout.CENTER);

    setToolContext(designSurface);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myPalettePanel;
  }

  @Override
  public void requestFocus() {
    myPalettePanel.requestFocus();
  }

  @NotNull
  @TestOnly
  public NlPaletteTreeGrid getTreeGrid() {
    return myPalettePanel;
  }

  @NotNull
  @Override
  public List<AnAction> getGearActions() {
    List<AnAction> actions = new ArrayList<>(3);
    actions.add(new TogglePaletteModeAction(this, PaletteMode.ICON_AND_NAME));
    actions.add(new TogglePaletteModeAction(this, PaletteMode.LARGE_ICONS));
    actions.add(new TogglePaletteModeAction(this, PaletteMode.SMALL_ICONS));
    return actions;
  }

  @Override
  public void setCloseAutoHideWindow(@NotNull Runnable runnable) {
    myCloseAutoHideCallback = runnable;
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myPalettePanel.setFilter(filter);
  }

  @Override
  public void setStartFiltering(@NotNull StartFilteringListener listener) {
    myPalettePanel.setStartFiltering(listener);
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    assert designSurface == null || designSurface instanceof NlDesignSurface;
    Module module = getModule(designSurface);
    if (designSurface != null && module != null && myLayoutType != designSurface.getLayoutType()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      myLayoutType = designSurface.getLayoutType();
      NlPaletteModel model = NlPaletteModel.get(facet);
      model.setUpdateListener(() -> reloadPalette(model, module, designSurface));
      reloadPalette(model, module, designSurface);
    }
  }

  private void reloadPalette(NlPaletteModel model, Module module, DesignSurface designSurface) {
    Palette palette = model.getPalette(myLayoutType);
    myPalettePanel.populateUiModel(palette, (NlDesignSurface)designSurface);
    myDependencyManager.setPalette(palette, module);
    repaint();
  }

  private void closeAutoHideToolWindow() {
    if (myCloseAutoHideCallback != null) {
      myCloseAutoHideCallback.run();
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
    PropertiesComponent.getInstance().setValue(PALETTE_MODE, mode.toString(), PaletteMode.ICON_AND_NAME.toString());
  }

  private static PaletteMode getInitialMode() {
    try {
      return PaletteMode.valueOf(PropertiesComponent.getInstance().getValue(PALETTE_MODE, PaletteMode.ICON_AND_NAME.toString()));
    }
    catch (IllegalArgumentException unused) {
      return PaletteMode.ICON_AND_NAME;
    }
  }

  @Override
  public void dispose() {
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
        myCopyPasteManager.setContents(new ItemTransferable(new DnDTransferItem(component)));
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
