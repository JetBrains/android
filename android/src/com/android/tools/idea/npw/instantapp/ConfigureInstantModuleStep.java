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
package com.android.tools.idea.npw.instantapp;

import com.android.tools.adtui.LabelWithEditLink;
import com.android.tools.idea.npw.module.NewModuleModel;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.BoolProperty;
import com.android.tools.idea.ui.properties.core.BoolValueProperty;
import com.android.tools.idea.ui.properties.core.ObservableString;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.wizard.model.ModelWizard;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.jetbrains.android.util.AndroidBundle.message;


/**
 * This class configures Instant App specific data such as the name of the atom to be created and the path to assign to the default Activity
 */
public final class ConfigureInstantModuleStep extends ModelWizardStep<NewModuleModel> {
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private JTextField mySplitNameField;
  private JPanel myPanel;
  private LabelWithEditLink myPackageName;

  public ConfigureInstantModuleStep(@NotNull NewModuleModel moduleModel) {
    super(moduleModel, message("android.wizard.module.new.instant.app"));
  }

  @Override
  protected void onWizardStarting(@NotNull ModelWizard.Facade wizard) {
    NewModuleModel model = getModel();
    TextProperty splitFieldText = new TextProperty(mySplitNameField);
    TextProperty packageNameText = new TextProperty(myPackageName);

    BoolProperty isPackageNameSynced = new BoolValueProperty(true);
    ObservableString computedFeatureModulePackageName = model.computedFeatureModulePackageName();
    myBindings.bind(model.packageName(), packageNameText, model.instantApp());
    myBindings.bind(packageNameText, computedFeatureModulePackageName, isPackageNameSynced);

    myBindings.bind(model.moduleName(), splitFieldText, model.instantApp());
    myBindings.bind(model.splitName(), splitFieldText);
    myListeners.receive(packageNameText, value -> isPackageNameSynced.set(value.equals(computedFeatureModulePackageName.get())));

    splitFieldText.set("feature");
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return mySplitNameField;
  }

  @Override
  protected boolean shouldShow() {
    return getModel().instantApp().get();
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }
}
