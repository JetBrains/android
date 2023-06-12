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
package com.android.tools.idea.gradle.adtimport;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;

import java.util.ArrayList;
import javax.swing.*;
import java.util.List;

class AdtImportWarningsStep extends ProjectImportWizardStep {
  private JTextArea myWarnings;
  private JPanel myPanel;

  AdtImportWarningsStep(WizardContext context) {
    super(context);
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public void updateStep() {
    super.updateStep();
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    if (importer != null) {
      StringBuilder sb = new StringBuilder();
      List<String> problems = new ArrayList<>();
      problems.addAll(importer.getErrors());
      problems.addAll(importer.getWarnings());
      if (!problems.isEmpty()) {
        sb.append("\n");
        for (String problem : problems) {
          sb.append(" * ");
          sb.append(problem);
          sb.append("\n");
        }
      }

      myWarnings.setText(sb.toString());
    }
  }

  @Override
  public boolean isStepVisible() {
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    return importer == null || !importer.getErrors().isEmpty()
           || !importer.getWarnings().isEmpty();
  }

  @Override
  public boolean validate() throws ConfigurationException {
    GradleImport importer = AdtImportProvider.getImporter(getWizardContext());
    if (importer != null && !importer.getErrors().isEmpty()) {
      throw new ConfigurationException("There are unrecoverable errors which must be corrected first");
    }
    return super.validate();
  }

  @Override
  public String getName() {
    return "ADT Import Issues";
  }
}
