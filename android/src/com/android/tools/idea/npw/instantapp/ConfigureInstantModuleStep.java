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
package com.android.tools.idea.npw.instantapp;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.FormFactorUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import com.intellij.ui.components.JBLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.wizard.WizardConstants.*;

/**
 * This class configures Instant App specific data such as the name of the atom to be created and the path to assign to the default Activity
 */
public final class ConfigureInstantModuleStep extends DynamicWizardStepWithDescription {
  @NotNull private final FormFactor myFormFactor;
  private JTextField mySupportedDomainsField;
  private JTextField myAtomNameField;
  private JTextField mySupportedRoutesField;
  private JPanel myPanel;
  private JBLabel mySupportedRoutesLabel;
  private JBLabel myAtomNameLabel;
  private JBLabel myConfigureAtomLabel;

  public ConfigureInstantModuleStep(@Nullable Disposable parentDisposable, @NotNull FormFactor formFactor) {
    super(parentDisposable);
    setBodyComponent(myPanel);
    myFormFactor = formFactor;
  }

  @Override
  public void init() {
    super.init();
    register(APP_DOMAIN_KEY, mySupportedDomainsField);

    myState.put(APP_DOMAIN_KEY, myState.get(COMPANY_DOMAIN_KEY));

    // TODO: put descriptions in AndroidBundle once UI is finalised
    setControlDescription(mySupportedDomainsField, "Domain that maps to this instant app");
    setControlDescription(mySupportedRoutesField, "Regular expression specifying paths that map to created atom");
    setControlDescription(myAtomNameField, "Name of the atom");

    boolean atomAndIapk = myState.getNotNull(ALSO_CREATE_IAPK_KEY, false);
    myConfigureAtomLabel.setVisible(atomAndIapk);
    myAtomNameLabel.setVisible(atomAndIapk);
    myAtomNameField.setVisible(atomAndIapk);
    mySupportedRoutesLabel.setVisible(atomAndIapk);
    mySupportedRoutesField.setVisible(atomAndIapk);

    if (atomAndIapk) {
      ScopedStateStore.Key<String> moduleNameKey = FormFactorUtils.getModuleNameKey(myFormFactor);
      register(moduleNameKey, myAtomNameField);
      myState.put(moduleNameKey, "atom");

      register(ATOM_ROUTE_KEY, mySupportedRoutesField);
      myState.put(ATOM_ROUTE_KEY, ".*");
    }

  }

  @NotNull
  @Override
  public String getStepName() {
    return "Configure Instant Module";
  }

  @NotNull
  @Override
  protected String getStepTitle() {
    return "Configure Instant Module";
  }

  @Nullable
  @Override
  protected String getStepDescription() {
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return mySupportedDomainsField;
  }

  @Override
  public boolean isStepVisible() {
    return myState.getNotNull(IS_INSTANT_APP_KEY, false);
  }
}
