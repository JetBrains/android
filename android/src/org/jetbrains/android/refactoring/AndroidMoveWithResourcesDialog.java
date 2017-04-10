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
package org.jetbrains.android.refactoring;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.CollectionComboBoxModel;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AndroidMoveWithResourcesDialog extends RefactoringDialog {

  private final AndroidMoveWithResourcesProcessor myProcessor;
  private ComboBox<Module> myModuleCombo;

  protected AndroidMoveWithResourcesDialog(@NotNull Project project, AndroidMoveWithResourcesProcessor processor) {
    super(project, true);
    myProcessor = processor;
    setTitle("Move With Resources");
    init();
  }

  @Override
  protected void doAction() {
    myProcessor.setTargetModule((Module)myModuleCombo.getSelectedItem());
    myProcessor.setPreviewUsages(isPreviewUsages());
    close(DialogWrapper.OK_EXIT_CODE);
    myProcessor.run();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());

    // Only offer modules that have an Android facet, otherwise we don't know where to move resources.
    List<Module> suitableModules = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (AndroidFacet.getInstance(module) != null) {
        suitableModules.add(module);
      }
    }

    ComboBoxModel<Module> model = new CollectionComboBoxModel<>(suitableModules);
    myModuleCombo = new ComboBox<>(model);
    panel.add(myModuleCombo);
    return panel;
  }
}
