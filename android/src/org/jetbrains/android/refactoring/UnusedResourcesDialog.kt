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
package org.jetbrains.android.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.ui.StateRestoringCheckBox;

import javax.swing.*;
import java.awt.*;

class UnusedResourcesDialog extends RefactoringDialog {
  private final UnusedResourcesProcessor myProcessor;
  private StateRestoringCheckBox myCbIncludeIds;

  public UnusedResourcesDialog(Project project, UnusedResourcesProcessor processor) {
    super(project, true);
    myProcessor = processor;
    setTitle("Remove Unused Resources");
    init();
  }

  @Override
  protected JComponent createNorthPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    myCbIncludeIds = new StateRestoringCheckBox();
    myCbIncludeIds.setText("Delete unused @id declarations too");
    panel.add(myCbIncludeIds, BorderLayout.CENTER);
    return panel;
  }

  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected void doAction() {
    myProcessor.setIncludeIds(myCbIncludeIds.isSelected());
    myProcessor.setPreviewUsages(isPreviewUsages());
    close(DialogWrapper.OK_EXIT_CODE);
    myProcessor.run();
  }
}
