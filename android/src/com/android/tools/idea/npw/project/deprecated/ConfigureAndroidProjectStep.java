/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.npw.project.deprecated;

import com.android.tools.adtui.LabelWithEditButton;
import com.android.tools.adtui.util.FormScalingUtil;
import com.android.tools.adtui.validation.Validator;
import com.android.tools.adtui.validation.ValidatorPanel;
import com.android.tools.idea.npw.cpp.ConfigureCppSupportStep;
import com.android.tools.idea.npw.project.DomainToPackageExpression;
import com.android.tools.idea.npw.project.NewProjectModel;
import com.android.tools.idea.observable.BindingsManager;
import com.android.tools.idea.observable.ListenerManager;
import com.android.tools.idea.observable.core.*;
import com.android.tools.idea.observable.expressions.Expression;
import com.android.tools.idea.observable.ui.SelectedProperty;
import com.android.tools.idea.observable.ui.TextProperty;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.google.common.collect.Lists;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModel> {
  private final JBScrollPane myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JPanel myPanel;
  private JTextField myCompanyDomain;
  private LabelWithEditButton myPackageName;
  private JCheckBox myCppSupportCheck;
  private JCheckBox myKotlinSupportCheck;

  public ConfigureAndroidProjectStep(@NotNull NewProjectModel model) {
    super(model, "Create Android Project");

    TextProperty packageNameText = new TextProperty(myPackageName);

    Expression<String> computedPackageName = new DomainToPackageExpression(model.companyDomain(), model.applicationName());
    BoolProperty isPackageSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.receive(packageNameText, value -> isPackageSynced.set(value.equals(computedPackageName.get())));

    Expression<String> computedLocation = model.applicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getChildComponent());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(model.projectLocation(), locationText);
    myListeners.receive(locationText, value -> isLocationSynced.set(value.equals(computedLocation.get())));

    myBindings.bindTwoWay(new TextProperty(myAppName), model.applicationName());
    myBindings.bindTwoWay(new TextProperty(myCompanyDomain), model.companyDomain());
    myBindings.bindTwoWay(new SelectedProperty(myCppSupportCheck), model.enableCppSupport());

    myBindings.bindTwoWay(new SelectedProperty(myKotlinSupportCheck), model.enableKotlinSupport());

    myProjectLocation.addActionListener(event -> {
      String finalPath = browseForFile(locationText.get());
      if (finalPath != null) {
        locationText.set(finalPath);
      }
    });

    myValidatorPanel = new ValidatorPanel(this, myPanel);
    myValidatorPanel.registerValidator(model.applicationName(), value -> {
      if (value.isEmpty()) {
        return new Validator.Result(Validator.Severity.ERROR, "Please enter an application name");
      }
      else if (!Character.isUpperCase(value.charAt(0))) {
        return new Validator.Result(Validator.Severity.INFO, "The application name for most apps begins with an uppercase letter");
      }
      return Validator.Result.OK;
    });

    Expression<File> locationFile = model.projectLocation().transform(File::new);
    myValidatorPanel.registerValidator(locationFile, PathValidator.createDefault("project location"));

    myValidatorPanel.registerValidator(model.packageName(),
                                       value -> Validator.Result.fromNullableMessage(WizardUtils.validatePackageName(value)));

    myRootPanel = StudioWizardStepPanel.wrappedWithVScroll(myValidatorPanel);
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
  }

  @NotNull
  @Override
  protected Collection<? extends ModelWizardStep> createDependentSteps() {
    return Lists.newArrayList(new ConfigureFormFactorStep(getModel()), new ConfigureCppSupportStep(getModel()));
  }

  @Override
  public void dispose() {
    myBindings.releaseAll();
    myListeners.releaseAll();
  }

  @NotNull
  @Override
  protected JComponent getComponent() {
    return myRootPanel;
  }

  @Nullable
  @Override
  protected JComponent getPreferredFocusComponent() {
    return myAppName;
  }

  @NotNull
  @Override
  protected ObservableBool canGoForward() {
    return myValidatorPanel.hasErrors().not();
  }

  @NotNull
  private static String findProjectLocation(@NotNull String applicationName) {
    applicationName = NewProjectModel.sanitizeApplicationName(applicationName);
    File baseDirectory = WizardUtils.getProjectLocationParent();
    File projectDirectory = new File(baseDirectory, applicationName);

    // Try appName, appName2, appName3, ...
    int counter = 2;
    while (projectDirectory.exists()) {
      projectDirectory = new File(baseDirectory, String.format("%s%d", applicationName, counter++));
    }

    return projectDirectory.getPath();
  }

  // TODO: Do we really need this method? See SdkComponentsStep on how to use addBrowseFolderListener. If we can't do the same, at least
  // describe what this method does, and why is needed.
  // Returns null if no file was selected
  @Nullable
  private static String browseForFile(@NotNull String initialPath) {
    FileChooserDescriptor fileSaverDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    File currentPath = new File(initialPath);
    File parentPath = currentPath.getParentFile();
    if (parentPath == null) {
      String homePath = System.getProperty("user.home");
      parentPath = new File(homePath == null ? File.separator : homePath);
    }
    VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath);

    OptionalProperty<String> finalPath = new OptionalValueProperty<>();
    FileChooser.chooseFiles(fileSaverDescriptor, null, parent, virtualFiles -> {
      if (virtualFiles.size() == 1) {
        String result = virtualFiles.iterator().next().getCanonicalPath();
        if (result != null) {
          finalPath.setValue(result);
        }
      }
    });

    return finalPath.getValueOrNull();
  }
}
