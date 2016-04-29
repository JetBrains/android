/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.android.newProject;

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class AndroidModuleWizardStep extends ModuleWizardStep {
  private final AndroidAppPropertiesEditor myAppPropertiesEditor;

  private final AndroidModuleBuilder myModuleBuilder;
  private final AndroidProjectType myProjectType;
  private final Alarm myAvdsUpdatingAlarm = new Alarm();
  private JPanel myPanel;
  private boolean myInitialized;

  public AndroidModuleWizardStep(@NotNull AndroidModuleBuilder moduleBuilder,
                                 ModulesProvider modulesProvider,
                                 AndroidProjectType projectType) {
    super();
    myProjectType = projectType;
    myModuleBuilder = moduleBuilder;
    myAppPropertiesEditor = new AndroidAppPropertiesEditor(moduleBuilder.getName(), modulesProvider);
    assert projectType == AndroidProjectType.APPLICATION;
    myPanel = new JPanel();
    myPanel.setLayout(new OverlayLayout(myPanel));
    myPanel.add(myAppPropertiesEditor.getContentPanel());
  }

  @Override
  public void disposeUIResources() {
    Disposer.dispose(myAvdsUpdatingAlarm);
  }

  @Override
  public void updateStep() {
    if (!myInitialized) {
      myInitialized = true;
      initialize();
    }
  }

  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAppPropertiesEditor.getApplicationNameField();
  }

  private void initialize() {
    final String moduleName = myModuleBuilder.getName();

    if (moduleName != null) {
      myAppPropertiesEditor.getApplicationNameField().setText(moduleName);
      myAppPropertiesEditor.getPackageNameField().setText(AndroidAppPropertiesEditor.getDefaultPackageNameByModuleName(moduleName));
    }
  }

  @Override
  public boolean validate() throws ConfigurationException {
    myAppPropertiesEditor.validate(myProjectType == AndroidProjectType.LIBRARY);
    return true;
  }

  @Override
  public void updateDataModel() {
    myModuleBuilder.setActivityName(myAppPropertiesEditor.getActivityName());
    myModuleBuilder.setPackageName(myAppPropertiesEditor.getPackageName());
    final String applicationName = myAppPropertiesEditor.getApplicationName();
    myModuleBuilder.setApplicationName(applicationName);
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.new.project.fromScratch.android";
  }
}
