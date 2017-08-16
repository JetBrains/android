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
package com.android.tools.idea.uibuilder.palette2;

import com.android.tools.adtui.splitter.ComponentsSplitter;
import com.android.tools.adtui.workbench.ToolContent;
import com.android.tools.idea.common.model.NlLayoutType;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.palette.Palette;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

/**
 * Top level Palette UI.
 */
public class PalettePanel extends JPanel implements Disposable, ToolContent<DesignSurface> {
  private static final String PALETTE_CATEGORY_WIDTH = "palette.category.width";
  private static final int DEFAULT_CATEGORY_WIDTH = 100;
  private static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 50;
  private static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 25;

  private final CategoryList myCategoryList;
  private final ItemList myItemList;
  private final DataModel myDataModel;

  private NlLayoutType myLayoutType;

  public PalettePanel(@NotNull Project project) {
    super(new BorderLayout());
    DependencyManager dependencyManager = new DependencyManager(project);
    dependencyManager.registerDependencyUpdates(this, this);
    myDataModel = new DataModel(dependencyManager);

    myCategoryList = new CategoryList();
    myItemList = new ItemList(dependencyManager);

    myCategoryList.setBackground(UIUtil.getPanelBackground());
    myCategoryList.setForeground(UIManager.getColor("Panel.foreground"));

    // Use a ComponentSplitter instead of a Splitter here to avoid a fat splitter size.
    ComponentsSplitter splitter = new ComponentsSplitter(false, true);
    splitter.setFirstComponent(createScrollPane(myCategoryList));
    splitter.setInnerComponent(createScrollPane(myItemList));
    splitter.setHonorComponentsMinimumSize(true);
    splitter.setFirstSize(JBUI.scale(getInitialCategoryWidth()));
    splitter.setFocusCycleRoot(false);

    add(splitter, BorderLayout.CENTER);

    myCategoryList.addListSelectionListener(event -> categorySelectionChanged());
    myCategoryList.setModel(myDataModel.getCategoryListModel());
    myItemList.setModel(myDataModel.getItemListModel());

    myLayoutType = NlLayoutType.UNKNOWN;
  }

  @NotNull
  private static JScrollPane createScrollPane(@NotNull JComponent component) {
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(component, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER);
    scrollPane.getVerticalScrollBar().setUnitIncrement(VERTICAL_SCROLLING_UNIT_INCREMENT);
    scrollPane.getVerticalScrollBar().setBlockIncrement(VERTICAL_SCROLLING_BLOCK_INCREMENT);
    scrollPane.setBorder(BorderFactory.createEmptyBorder());
    return scrollPane;
  }

  private static int getInitialCategoryWidth() {
    try {
      return Integer.parseInt(PropertiesComponent.getInstance().getValue(PALETTE_CATEGORY_WIDTH, String.valueOf(DEFAULT_CATEGORY_WIDTH)));
    }
    catch (NumberFormatException unused) {
      return DEFAULT_CATEGORY_WIDTH;
    }
  }

  private void categorySelectionChanged() {
    Palette.Group group = myCategoryList.getSelectedValue();
    if (group == null) {
      myCategoryList.setSelectedIndex(0);
      return;
    }
    myDataModel.categorySelectionChanged(group);
    myItemList.setSelectedIndex(0);
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return this;
  }

  @NotNull
  @Override
  public JComponent getFocusedComponent() {
    return myCategoryList;
  }

  @Override
  public void requestFocus() {
    myCategoryList.requestFocus();
  }

  @Override
  public boolean supportsFiltering() {
    return true;
  }

  @Override
  public void setFilter(@NotNull String filter) {
    myDataModel.setFilterPattern(filter);
    categorySelectionChanged();
  }

  @Override
  public void setToolContext(@Nullable DesignSurface designSurface) {
    assert designSurface == null || designSurface instanceof NlDesignSurface;
    Module module = getModule(designSurface);
    if (designSurface != null && module != null && myLayoutType != designSurface.getLayoutType()) {
      AndroidFacet facet = AndroidFacet.getInstance(module);
      assert facet != null;
      myLayoutType = designSurface.getLayoutType();
      myDataModel.setLayoutType(facet, myLayoutType);
      myCategoryList.setSelectedIndex(0);
    }
  }

  @Nullable
  private static Module getModule(@Nullable DesignSurface designSurface) {
    Configuration configuration =
      designSurface != null && designSurface.getLayoutType().isSupportedByDesigner() ? designSurface.getConfiguration() : null;
    return configuration != null ? configuration.getModule() : null;
  }

  @Override
  public void dispose() {
  }
}
