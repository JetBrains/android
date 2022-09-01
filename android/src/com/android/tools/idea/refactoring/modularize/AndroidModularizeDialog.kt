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
package com.android.tools.idea.refactoring.modularize;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.CollectionComboBoxModel;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class AndroidModularizeDialog extends RefactoringDialog {

  private final List<Module> myTargetModules;
  private final AndroidModularizeProcessor myProcessor;
  private ComboBox<Module> myModuleCombo;

  protected AndroidModularizeDialog(@NotNull Project project, @NotNull List<Module> targetModules, AndroidModularizeProcessor processor) {
    super(project, true);
    myTargetModules = targetModules;
    myProcessor = processor;
    setTitle("Modularize");
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

    panel.add(
      new JLabel(
        String.format(Locale.US, "Move %1$d classes and %2$d resources to:", myProcessor.getClassesCount(),
                      myProcessor.getResourcesCount())),
      BorderLayout.NORTH);

    ComboBoxModel<Module> model = new CollectionComboBoxModel<>(myTargetModules);
    myModuleCombo = new ComboBox<>(model);
    panel.add(myModuleCombo, BorderLayout.CENTER);
    return panel;
  }
}
