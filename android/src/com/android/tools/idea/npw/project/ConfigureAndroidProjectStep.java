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
package com.android.tools.idea.npw.project;

import com.android.tools.idea.ui.LabelWithEditLink;
import com.android.tools.idea.ui.properties.BindingsManager;
import com.android.tools.idea.ui.properties.ListenerManager;
import com.android.tools.idea.ui.properties.core.*;
import com.android.tools.idea.ui.properties.expressions.Expression;
import com.android.tools.idea.ui.properties.swing.SelectedProperty;
import com.android.tools.idea.ui.properties.swing.TextProperty;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.ui.validation.ValidatorPanel;
import com.android.tools.idea.ui.validation.validators.PathValidator;
import com.android.tools.idea.ui.wizard.StudioWizardStepPanel;
import com.android.tools.idea.ui.wizard.WizardUtils;
import com.android.tools.idea.wizard.model.ModelWizardStep;
import com.android.tools.swing.util.FormScalingUtil;
import com.google.common.base.Splitter;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * First page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends ModelWizardStep<NewProjectModel> {
  private final StudioWizardStepPanel myRootPanel;
  private final ValidatorPanel myValidatorPanel;
  private final BindingsManager myBindings = new BindingsManager();
  private final ListenerManager myListeners = new ListenerManager();

  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JPanel myPanel;
  private JTextField myCompanyDomain;
  private LabelWithEditLink myPackageName;
  private JCheckBox myCppSupportCheck;

  public ConfigureAndroidProjectStep(NewProjectModel model) {
    super(model, "Create Android Project");

    TextProperty packageNameText = new TextProperty(myPackageName);

    Expression<String> computedPackageName = new DomainToPackageExpression(model.companyDomain(), model.applicationName());
    BoolProperty isPackageSynced = new BoolValueProperty(true);
    myBindings.bind(packageNameText, computedPackageName, isPackageSynced);
    myBindings.bind(model.packageName(), packageNameText);
    myListeners.listen(packageNameText,
                       (Consumer<String>)sender -> isPackageSynced.set(packageNameText.get().equals(computedPackageName.get())));

    Expression<String> computedLocation = model.applicationName().transform(ConfigureAndroidProjectStep::findProjectLocation);
    TextProperty locationText = new TextProperty(myProjectLocation.getChildComponent());
    BoolProperty isLocationSynced = new BoolValueProperty(true);
    myBindings.bind(locationText, computedLocation, isLocationSynced);
    myBindings.bind(model.projectLocation(), locationText);
    myListeners.listen(locationText,
                       (Consumer<String>)sender -> isLocationSynced.set(locationText.get().equals(computedLocation.get())));

    myBindings.bindTwoWay(new TextProperty(myAppName), model.applicationName());
    myBindings.bindTwoWay(new TextProperty(myCompanyDomain), model.companyDomain());
    myBindings.bindTwoWay(new SelectedProperty(myCppSupportCheck), model.enableCppSupport());

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

    myValidatorPanel.registerValidator(model.packageName(), value -> {
      return Validator.Result.fromNullableMessage(AndroidUtils.validateAndroidPackageName(value));
    });

    myRootPanel = new StudioWizardStepPanel(myValidatorPanel, "Configure your new project");
    FormScalingUtil.scaleComponentTree(this.getClass(), myRootPanel);
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
