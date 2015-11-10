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
package com.android.tools.idea.gradle.structure.configurables;

import com.android.tools.idea.gradle.structure.configurables.model.ModuleMergedModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.BaseStructureConfigurable;
import com.intellij.ui.navigation.Place;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultTreeModel;

public class ModuleStructureConfigurable extends BaseStructureConfigurable implements Place.Navigator {
  public ModuleStructureConfigurable(@NotNull Project project) {
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
        ModuleConfigurable configurable = new ModuleConfigurable(model);
        MyNode moduleNode = new MyNode(configurable);
        myRoot.add(moduleNode);
      }
    }
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
    ModuleStructureConfigurable.super.disposeUIResources();
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
    return "android.gradle.project.structure.modules";
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }

  @Override
  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("project.roots.display.name");
  }
}
