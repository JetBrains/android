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
package com.android.tools.idea.npw.deprecated;

import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.npw.FormFactorUtils;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.android.tools.idea.wizard.WizardConstants.*;

/**
 * This class configures Instant App specific data such as the name of the atom to be created and the path to assign to the default Activity
 */
@Deprecated
public final class ConfigureInstantModuleStep extends DynamicWizardStepWithDescription {
  @NotNull private final FormFactor myFormFactor;
  private JTextField myAtomNameField;
  private JTextField mySupportedRoutesField;
  private JPanel myPanel;

  public ConfigureInstantModuleStep(@Nullable Disposable parentDisposable, @NotNull FormFactor formFactor) {
    super(parentDisposable);
    setBodyComponent(myPanel);
    myFormFactor = formFactor;
  }

  @Override
  public void init() {
    super.init();
    // TODO: put descriptions in AndroidBundle once UI is finalised
    setControlDescription(mySupportedRoutesField, "Regular expression specifying paths that map to created atom");
    setControlDescription(myAtomNameField, "Name of the base atom");

    ScopedStateStore.Key<String> moduleNameKey = FormFactorUtils.getModuleNameKey(myFormFactor);
    register(moduleNameKey, myAtomNameField);
    myState.put(moduleNameKey, "atom");

    register(ATOM_ROUTE_KEY, mySupportedRoutesField);
    myState.put(ATOM_ROUTE_KEY, "/.*");

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
    return myAtomNameField;
  }

  @Override
  public boolean isStepVisible() {
    return myState.getNotNull(IS_INSTANT_APP_KEY, false) && myState.getNotNull(ALSO_CREATE_IAPK_KEY, false);
  }
}
