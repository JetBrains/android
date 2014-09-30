/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard;

import com.android.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

import static com.android.tools.idea.wizard.WizardConstants.APPLICATION_NAME_KEY;
import static com.android.tools.idea.wizard.WizardConstants.PROJECT_LOCATION_KEY;
import static com.android.tools.idea.wizard.WizardConstants.SELECTED_MODULE_TYPE_KEY;

/**
 * Configuration for a new Android module
 */
public class ConfigureAndroidModuleStepDynamic extends ConfigureAndroidProjectStep {
  private static final Logger LOG = Logger.getInstance(ConfigureAndroidModuleStepDynamic.class);

  private CreateModuleTemplate myModuleType;
  private FormFactorApiComboBox mySdkControls;
  private Project myProject;

  public ConfigureAndroidModuleStepDynamic(@Nullable Project project, @Nullable Disposable parentDisposable) {
    super("Configure your new module", parentDisposable);
    myProject = project;
  }

  @Override
  public void init() {
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    super.init();
    myProjectLocation.setVisible(false);
    myProjectLocationLabel.setVisible(false);
    deregister(myProjectLocation);
    unregisterValueDeriver(PROJECT_LOCATION_KEY);
    myProjectLocation.setText(projectLocation);
    myState.put(PROJECT_LOCATION_KEY, projectLocation);
  }

  @Override
  public void onEnterStep() {
    super.onEnterStep();
    CreateModuleTemplate moduleType = getModuleType();
    if (moduleType != null && moduleType.formFactor != null && moduleType.templateMetadata != null) {
      myModuleType = moduleType;
      registerValueDeriver(FormFactorUtils.getModuleNameKey(moduleType.formFactor), ourModuleNameDeriver);

      if (mySdkControls != null) {
        // Remove existing SDK combo if we have one
        deregister(mySdkControls);
        myPanel.remove(mySdkControls);
      }
      mySdkControls = new FormFactorApiComboBox(moduleType.formFactor, moduleType.templateMetadata.getMinSdk());
      GridConstraints constraints = new GridConstraints();
      constraints.setColumn(0);
      constraints.setRow(3);
      myPanel.add(new JBLabel(ConfigureFormFactorStep.MIN_SDK_STRING), constraints);
      constraints.setColumn(1);
      constraints.setFill(GridConstraints.FILL_HORIZONTAL);
      myPanel.add(mySdkControls, constraints);
      mySdkControls.register(this);
    } else {
      LOG.error("init() Called on ConfigureAndroidModuleStepDynamic with an incorrect selected ModuleType");
    }
    if (mySdkControls != null) {
      mySdkControls.loadSavedApi();
    }
    invokeUpdate(null);
  }

  @Nullable
  private CreateModuleTemplate getModuleType() {
    ModuleTemplate moduleTemplate = myState.get(SELECTED_MODULE_TYPE_KEY);
    if (moduleTemplate instanceof CreateModuleTemplate) {
      CreateModuleTemplate type = (CreateModuleTemplate)moduleTemplate;
      if (type.formFactor != null && type.templateMetadata != null) {
        return type;
      }
    }
    return null;
  }

  @Override
  public void deriveValues(Set<ScopedStateStore.Key> modified) {
    super.deriveValues(modified);
    if (mySdkControls != null) {
      mySdkControls.deriveValues(myState, modified);
    }
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    return validateAppName() && validatePackageName() && validateApiLevel();
  }

  private boolean validateApiLevel() {
    if (mySdkControls == null || mySdkControls.getItemCount() < 1) {
      setErrorHtml("No supported platforms found. Please install the proper platform or add-on through the SDK manager.");
      return false;
    }
    return true;
  }

  private final ValueDeriver<String> ourModuleNameDeriver = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<ScopedStateStore.Key<?>> getTriggerKeys() {
      return makeSetOf(APPLICATION_NAME_KEY);
    }

    @Nullable
    @Override
    public String deriveValue(@NotNull ScopedStateStore state, @Nullable ScopedStateStore.Key changedKey, @Nullable String currentValue) {
      String appName = state.get(APPLICATION_NAME_KEY);
      if (appName == null) {
        appName = myModuleType.formFactor.toString();
      }
      return WizardUtils.computeModuleName(appName, getProject());
    }
  };

  @Override
  public boolean isStepVisible() {
    return getModuleType() != null;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "New Android Module Configuration";
  }

  @Nullable
  @Override
  protected JComponent getHeader() {
    return NewModuleWizardDynamic.buildHeader();
  }
}
