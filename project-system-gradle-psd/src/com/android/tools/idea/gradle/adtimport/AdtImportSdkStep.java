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

import com.android.tools.idea.gradle.structure.IdeSdksConfigurable;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.projectImport.ProjectImportWizardStep;
import java.awt.Dimension;
import javax.swing.JComponent;

/** Panel where the user configures an SDK, if needed */
class AdtImportSdkStep extends ProjectImportWizardStep {
  private JComponent myComponent;
  private IdeSdksConfigurable myConfigurable;

  AdtImportSdkStep(WizardContext context) {
    super(context);
    // TODO: Pass in a context here which allows the configurable to request
    // validation when the text field is edited
    myConfigurable = new IdeSdksConfigurable(null);
  }

  @Override
  public JComponent getComponent() {
    if (myComponent == null) {
      myComponent = myConfigurable.getContentPanel();
      myComponent.setPreferredSize(new Dimension(500, 400));
      myConfigurable.reset();
    }
    return myComponent;
  }

  @Override
  public String getName() {
    return "ADT Import SDK Selection";
  }

  @Override
  public void updateDataModel() {
  }

  @Override
  public boolean validate() throws ConfigurationException {
    boolean valid = myConfigurable.validate();
    if (valid && myConfigurable.isModified()) {
      myConfigurable.apply();
    }

    return valid;
  }

  @Override
  public void updateStep() {
    super.updateStep();
  }

  @Override
  public boolean isStepVisible() {
    return IdeSdksConfigurable.isNeeded();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myConfigurable.getPreferredFocusedComponent();
  }
}
