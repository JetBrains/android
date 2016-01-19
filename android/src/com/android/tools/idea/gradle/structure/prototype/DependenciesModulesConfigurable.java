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
package com.android.tools.idea.gradle.structure.prototype;

import com.android.tools.idea.gradle.structure.prototype.model.ModuleMergedModel;
import com.google.common.collect.Lists;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;
import java.util.ArrayList;

public class DependenciesModulesConfigurable extends BaseStructureConfigurable implements Place.Navigator {
  protected DependenciesModulesConfigurable(@NotNull Project project) {
    super(project);
  }

  @Override
  protected void initTree() {
    super.initTree();
    myTree.setRootVisible(false);
  }

  @Override
  protected void loadTree() {
    createModuleNodes();
    ((DefaultTreeModel)myTree.getModel()).reload();
    myUiDisposed = false;
  }

  private void createModuleNodes() {
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      ModuleMergedModel model = ModuleMergedModel.get(module);
      if (model != null) {
        DependenciesConfigurable configurable = new DependenciesConfigurable(model);
        MyNode moduleNode = new MyNode(configurable);
        myRoot.add(moduleNode);
      }
    }
  }

  @Override
  @NotNull
  protected ArrayList<AnAction> createActions(boolean fromPopup) {
    return Lists.newArrayList();
  }

  @Override
  @Nullable
  protected AbstractAddGroup createAddAction() {
    return null;
  }

  @Override
  public void dispose() {

  }

  @Override
  public void disposeUIResources() {
    super.disposeUIResources();
    myContext.myModulesConfigurator.disposeUIResources();
    DependenciesModulesConfigurable.super.disposeUIResources();
  }

  @Override
  protected void processRemovedItems() {

  }

  @Override
  protected boolean wasObjectStored(Object editableObject) {
    return false;
  }

  @Override
  @NotNull
  public String getId() {
    return "android.gradle.project.structure.dependencies";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return "Dependencies";
  }
}
