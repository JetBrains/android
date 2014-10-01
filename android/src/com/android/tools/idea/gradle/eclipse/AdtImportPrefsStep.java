/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.eclipse;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.projectImport.ProjectImportWizardStep;

import javax.swing.*;

/** Panel where you configure ADT import preferences */
class AdtImportPrefsStep extends ProjectImportWizardStep {
  private JCheckBox myReplaceJars;
  private JCheckBox myReplaceLibs;
  private JCheckBox myLowerCase;
  private JPanel myPanel;

  AdtImportPrefsStep(WizardContext context) {
    super(context);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public String getName() {
    return "ADT Import Preferences";
  }

  @Override
  public void updateDataModel() {
    AdtImportBuilder builder = AdtImportBuilder.getBuilder(getWizardContext());
    if (builder != null) {
      GradleImport importer = builder.getImporter();
      if (importer != null) {
        importer.setReplaceJars(myReplaceJars.isSelected());
        importer.setReplaceLibs(myReplaceLibs.isSelected());
        importer.setGradleNameStyle(myLowerCase.isSelected());
        // Refresh read state in case any of the options affect the set of warnings, etc
        builder.readProjects();
      }
    }
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myReplaceJars;
  }
}
