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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.model.PsdModuleEditor;
import com.android.tools.idea.gradle.structure.model.PsdProjectEditor;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.NamedConfigurable;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.navigation.Place;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;

public abstract class BasePerspectiveConfigurable extends MasterDetailsComponent
  implements SearchableConfigurable, Disposable, Place.Navigator {

  @NotNull private final PsdProjectEditor myProjectEditor;

  protected boolean myUiDisposed = true;

  private boolean myWasTreeInitialized;

  protected BasePerspectiveConfigurable(@NotNull PsdProjectEditor projectEditor) {
    myProjectEditor = projectEditor;
  }

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    // By default, do not create actions like "+" or "-" for the modules tree view
    return Lists.newArrayList();
  }

  @Override
  public void reset() {
    myUiDisposed = false;

    if (!myWasTreeInitialized) {
      initTree();
      myTree.setShowsRootHandles(false);
      loadTree();
    }
    else {
      super.disposeUIResources();
      myTree.setShowsRootHandles(false);
      loadTree();
    }

    super.reset();
  }

  @Override
  protected void initTree() {
    if (myWasTreeInitialized) {
      return;
    }
    myWasTreeInitialized = true;
    super.initTree();
    myTree.setRootVisible(false);

    new TreeSpeedSearch(myTree, new Convertor<TreePath, String>() {
      @Override
      public String convert(final TreePath treePath) {
        return ((MyNode)treePath.getLastPathComponent()).getDisplayName();
      }
    }, true);
    ToolTipManager.sharedInstance().registerComponent(myTree);
    // myTree.setCellRenderer(new ProjectStructureElementRenderer(myContext));
  }

  protected void loadTree() {
    createModuleNodes();
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  private void createModuleNodes() {
    for (PsdModuleEditor moduleEditor : myProjectEditor.getModuleEditors()) {
      NamedConfigurable<? extends PsdModuleEditor> configurable = getConfigurable(moduleEditor);
      if (configurable != null) {
        MyNode moduleNode = new MyNode(configurable);
        myRoot.add(moduleNode);
      }
    }
  }

  @Nullable
  protected abstract NamedConfigurable<? extends PsdModuleEditor> getConfigurable(@NotNull PsdModuleEditor moduleEditor);

  @NotNull
  protected PsdProjectEditor getProjectEditor() {
    return myProjectEditor;
  }

  @Override
  protected void processRemovedItems() {
  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
