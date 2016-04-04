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
package com.android.tools.idea.npw;

import com.android.annotations.VisibleForTesting;
import com.android.tools.idea.ui.LabelWithEditLink;
import com.android.tools.idea.wizard.WizardConstants;
import com.android.tools.idea.wizard.dynamic.DynamicWizardStepWithHeaderAndDescription;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore;
import com.android.tools.idea.wizard.dynamic.ScopedStateStore.Key;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends DynamicWizardStepWithHeaderAndDescription {
  private static final String EXAMPLE_DOMAIN = "example.com";
  public static final String SAVED_COMPANY_DOMAIN = "SAVED_COMPANY_DOMAIN";

  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JPanel myPanel;
  private JTextField myCompanyDomain;
  private LabelWithEditLink myPackageName;

  public ConfigureAndroidProjectStep(@NotNull Disposable disposable) {
    this("Configure your new project", disposable);
  }

  public ConfigureAndroidProjectStep(String title, Disposable parentDisposable) {
    super(title, null, parentDisposable);
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    register(WizardConstants.APPLICATION_NAME_KEY, myAppName);
    register(WizardConstants.COMPANY_DOMAIN_KEY, myCompanyDomain);
    register(WizardConstants.PACKAGE_NAME_KEY, myPackageName);
    registerValueDeriver(WizardConstants.PACKAGE_NAME_KEY, PACKAGE_NAME_DERIVER);
    register(WizardConstants.PROJECT_LOCATION_KEY, myProjectLocation);
    registerValueDeriver(WizardConstants.PROJECT_LOCATION_KEY, myProjectLocationDeriver);

    myProjectLocation.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browseForFile();
      }
    });

    myState.put(WizardConstants.APPLICATION_NAME_KEY, "My Application");
    String savedCompanyDomain = PropertiesComponent.getInstance().getValue(SAVED_COMPANY_DOMAIN);
    if (savedCompanyDomain == null) {
      savedCompanyDomain = nameToPackage(System.getProperty("user.name"));
      if (savedCompanyDomain != null) {
        savedCompanyDomain = savedCompanyDomain + '.' + EXAMPLE_DOMAIN;
      }
    }
    if (savedCompanyDomain == null) {
      savedCompanyDomain = EXAMPLE_DOMAIN;
    }
    myState.put(WizardConstants.COMPANY_DOMAIN_KEY, savedCompanyDomain);
    super.init();
  }

  private void browseForFile() {
    FileSaverDescriptor fileSaverDescriptor = new FileSaverDescriptor("Project location", "Please choose a location for your project");
    File currentPath = new File(myProjectLocation.getText());
    File parentPath = currentPath.getParentFile();
    if (parentPath == null) {
      String homePath = System.getProperty("user.home");
      parentPath = homePath == null ? new File("/") : new File(homePath);
    }
    VirtualFile parent = LocalFileSystem.getInstance().findFileByIoFile(parentPath);
    String filename = currentPath.getName();
    VirtualFileWrapper fileWrapper =
      FileChooserFactory.getInstance().createSaveFileDialog(fileSaverDescriptor, (Project)null).save(parent, filename);
    if (fileWrapper != null) {
      myProjectLocation.setText(fileWrapper.getFile().getAbsolutePath());
    }
  }

  @Override
  public void deriveValues(Set<Key> modified) {
    super.deriveValues(modified);
    // Save the user edited value of the company domain
    if (modified.contains(WizardConstants.COMPANY_DOMAIN_KEY)) {
      String domain = myState.get(WizardConstants.COMPANY_DOMAIN_KEY);
      if (domain != null && !domain.isEmpty() && myState.containsKey(WizardConstants.PACKAGE_NAME_KEY)) {
        @SuppressWarnings("ConstantConditions") String message =
          AndroidUtils.validateAndroidPackageName(myState.get(WizardConstants.PACKAGE_NAME_KEY));
        if (message == null) {
          PropertiesComponent.getInstance().setValue(SAVED_COMPANY_DOMAIN, domain);
        }
      }
    }
  }

  @Override
  public boolean validate() {
    if (!myPath.validate()) return false;
    WizardUtils.ValidationResult locationValidationResult = WizardUtils.validateLocation(myState.get(WizardConstants.PROJECT_LOCATION_KEY));
    setErrorHtml(locationValidationResult.isOk() ? "" : locationValidationResult.getFormattedMessage());
    return validateAppName() && validatePackageName() && !locationValidationResult.isError();
  }

  private boolean validateAppName() {
    String appName = myState.get(WizardConstants.APPLICATION_NAME_KEY);
    if (appName == null || appName.isEmpty()) {
      setErrorHtml("Please enter an application name (shown in launcher)");
      return false;
    }
    else if (Character.isLowerCase(appName.charAt(0))) {
      setErrorHtml("The application name for most apps begins with an uppercase letter");
    }
    return true;
  }

  private boolean validatePackageName() {
    String packageName = myState.get(WizardConstants.PACKAGE_NAME_KEY);
    if (packageName == null) {
      setErrorHtml("Please enter a package name (This package uniquely identifies your application)");
      return false;
    }
    else {
      String message = AndroidUtils.validateAndroidPackageName(packageName);
      if (message != null) {
        setErrorHtml("Invalid package name: " + message);
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public String getStepName() {
    return "Create Android Project";
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myAppName;
  }

  @VisibleForTesting
  static String nameToPackage(String name) {
    name = name.replace('-', '_');
    name = name.replaceAll("[^a-zA-Z0-9_]", "");

    if (!name.isEmpty()) {
      name = name.toLowerCase(Locale.US);

      if (AndroidUtils.isReservedKeyword(name) != null) {
        // Not using StringUtil.fixVariableNameDerivedFromPropertyName() because we want it to stay lower cased.
        name = (StringUtil.isVowel(name.charAt(0)) ? "an" : "a") + name;
      }
    }

    return name;
  }

  public static final ValueDeriver<String> PACKAGE_NAME_DERIVER = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(WizardConstants.APPLICATION_NAME_KEY, WizardConstants.COMPANY_DOMAIN_KEY);
    }

    @Nullable
    @Override
    public String deriveValue(@NotNull ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
      String projectName = state.get(WizardConstants.APPLICATION_NAME_KEY);
      if (projectName == null) {
        projectName = "app";
      }
      projectName = nameToPackage(projectName);
      String companyDomain = state.get(WizardConstants.COMPANY_DOMAIN_KEY);
      if (companyDomain == null) {
        companyDomain = EXAMPLE_DOMAIN;
      }
      ArrayList domainParts = Lists.newArrayList(companyDomain.split("\\."));
      String reversedDomain = Joiner.on('.').skipNulls().join(Lists.reverse(Lists.transform(domainParts, new Function<String, String>() {
        @Override
        public String apply(String input) {
          String name = nameToPackage(input);
          return name.isEmpty() ? null : name;
        }
      })));
      return reversedDomain + '.' + projectName;
    }
  };

  private static final ValueDeriver<String> myProjectLocationDeriver = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(WizardConstants.APPLICATION_NAME_KEY);
    }

    @Nullable
    @Override
    public String deriveValue(@NotNull ScopedStateStore state, @Nullable Key changedKey, @Nullable String currentValue) {
      String name = state.get(WizardConstants.APPLICATION_NAME_KEY);
      name = name == null ? "" : name;
      name = name.replaceAll(WizardConstants.INVALID_FILENAME_CHARS, "");
      name = name.replaceAll("\\s", "");
      File baseDirectory = WizardUtils.getProjectLocationParent();
      File projectDir = new File(baseDirectory, name);
      int i = 2;
      while (projectDir.exists()) {
        projectDir = new File(baseDirectory, name + i);
        i++;
      }
      return projectDir.getPath();
    }
  };

  @NotNull
  @Override
  protected WizardStepHeaderSettings getStepHeader() {
    return ConfigureAndroidProjectPath.buildConfigurationHeader();
  }
}
