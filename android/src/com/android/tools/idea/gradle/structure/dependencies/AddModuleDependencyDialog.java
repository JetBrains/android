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
package com.android.tools.idea.gradle.structure.dependencies;

import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.ui.ValidationInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class AddModuleDependencyDialog extends AbstractAddDependenciesDialog {
  @NotNull public static final String TITLE = "Add Module Dependency";

  private ModuleDependenciesForm myModuleDependenciesForm;

  public AddModuleDependencyDialog(@NotNull PsModule module) {
    super(module);
    setTitle(TITLE);
  }

  @Override
  public void addNewDependencies() {

  }

  @Override
  @NotNull
  protected String getSplitterProportionKey() {
    return "psd.add.module.dependency.main.horizontal.splitter.proportion";
  }

  @Override
  @NotNull
  protected JComponent getDependencySelectionView() {
    if (myModuleDependenciesForm == null) {
      myModuleDependenciesForm = new ModuleDependenciesForm(getModule());
    }
    return myModuleDependenciesForm.getPanel();
  }

  @Override
  @NotNull
  protected String getInstructions() {
    return "Please select the modules to add as dependencies.";
  }

  @Override
  @NotNull
  protected String getDimensionServiceKey() {
    return "psd.add.module.dependency.panel.dimension";
  }

  @Override
  @Nullable
  public JComponent getPreferredFocusedComponent() {
    if (myModuleDependenciesForm != null) {
      return myModuleDependenciesForm.getPreferredFocusedComponent();
    }
    return null;
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    List<PsModule> modules = myModuleDependenciesForm.getSelectedModules();
    if (modules.isEmpty()) {
      return new ValidationInfo("Select at least one module", myModuleDependenciesForm.getPreferredFocusedComponent());
    }
    return getScopesPanel().validateInput();
  }
}
