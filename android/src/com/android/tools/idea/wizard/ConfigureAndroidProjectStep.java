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
import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileChooser.FileChooserFactory;
import com.intellij.openapi.fileChooser.FileSaverDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWrapper;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.text.Document;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.ScopedStateStore.Key;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.STEP;
import static com.android.tools.idea.wizard.ScopedStateStore.Scope.WIZARD;
import static com.android.tools.idea.wizard.ScopedStateStore.createKey;

/**
 * ConfigureAndroidModuleStep is the first page in the New Project wizard that sets project/module name, location, and other project-global
 * parameters.
 */
public class ConfigureAndroidProjectStep extends DynamicWizardStepWithHeaderAndDescription {
  public static final Key<String> APPLICATION_NAME_KEY = createKey(ATTR_APP_TITLE, WIZARD, String.class);
  public static final Key<String> COMPANY_DOMAIN_KEY = createKey("companyDomain", STEP, String.class);
  public static final Key<String> PACKAGE_NAME_KEY = createKey(ATTR_PACKAGE_NAME, WIZARD, String.class);
  public static final Key<String> PROJECT_LOCATION_KEY = createKey(ATTR_TOP_OUT, WIZARD, String.class);


  private static final String EXAMPLE_DOMAIN = "yourname.com";
  public static final String SAVED_COMPANY_DOMAIN = "SAVED_COMPANY_DOMAIN";
  public static final String INVALID_FILENAME_CHARS = "[/\\\\?%*:|\"<>]";
  private static final CharMatcher ILLEGAL_CHARACTER_MATCHER = CharMatcher.anyOf(INVALID_FILENAME_CHARS);

  @VisibleForTesting
  static final Set<String> INVALID_MSFT_FILENAMES = ImmutableSet
    .of("con", "prn", "aux", "clock$", "nul", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9", "lpt1", "lpt2",
        "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "$mft", "$mftmirr", "$logfile", "$volume", "$attrdef", "$bitmap", "$boot",
        "$badclus", "$secure", "$upcase", "$extend", "$quota", "$objid", "$reparse");


  private TextFieldWithBrowseButton myProjectLocation;
  private JTextField myAppName;
  private JPanel myPanel;
  private JTextField myCompanyDomain;
  private LabelWithEditLink myPackageName;

  public ConfigureAndroidProjectStep(@NotNull Disposable disposable) {
    super("Add information to your new project", null, null, disposable);
    setBodyComponent(myPanel);
  }

  @Override
  public void init() {
    register(APPLICATION_NAME_KEY, myAppName);
    register(COMPANY_DOMAIN_KEY, myCompanyDomain);
    register(PACKAGE_NAME_KEY, myPackageName, new ComponentBinding<String, LabelWithEditLink>() {
      @Override
      public void setValue(@Nullable String newValue, @NotNull LabelWithEditLink component) {
        newValue = newValue == null ? "" : newValue;
        component.setText(newValue);
      }

      @Nullable
      @Override
      public String getValue(@NotNull LabelWithEditLink component) {
        return component.getText();
      }

      @Nullable
      @Override
      public Document getDocument(@NotNull LabelWithEditLink component) {
        return component.getDocument();
      }
    });
    registerValueDeriver(PACKAGE_NAME_KEY, myPackageNameDeriver);
    register(PROJECT_LOCATION_KEY, myProjectLocation);
    registerValueDeriver(PROJECT_LOCATION_KEY, myProjectLocationDeriver);

    myProjectLocation.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        browseForFile();
      }
    });

    myState.put(APPLICATION_NAME_KEY, "My Application");
    String savedCompanyDomain = PropertiesComponent.getInstance().getValue(SAVED_COMPANY_DOMAIN);
    if (savedCompanyDomain == null) {
      savedCompanyDomain = System.getProperty("user.name");
    }
    if (savedCompanyDomain == null) {
      savedCompanyDomain = EXAMPLE_DOMAIN;
    }
    myState.put(COMPANY_DOMAIN_KEY, savedCompanyDomain);
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
    if (modified.contains(COMPANY_DOMAIN_KEY)) {
      String domain = myState.get(COMPANY_DOMAIN_KEY);
      if (domain != null && !domain.isEmpty() && myState.containsKey(PACKAGE_NAME_KEY)) {
        @SuppressWarnings("ConstantConditions")
        String message = AndroidUtils.validateAndroidPackageName(myState.get(PACKAGE_NAME_KEY));
        if (message == null) {
          PropertiesComponent.getInstance().setValue(SAVED_COMPANY_DOMAIN, domain);
        }
      }
    }
  }

  @Override
  public boolean validate() {
    setErrorHtml("");
    // App Name validation
    String appName = myState.get(APPLICATION_NAME_KEY);
    if (appName == null || appName.isEmpty()) {
      setErrorHtml("Please enter an application name (shown in launcher)");
      return false;
    } else if (Character.isLowerCase(appName.charAt(0))) {
      setErrorHtml("The application name for most apps begins with an uppercase letter");
    }

    // Company Domain validation
    String companyDomain = myState.get(COMPANY_DOMAIN_KEY);
    if (companyDomain == null || companyDomain.isEmpty()) {
      setErrorHtml("Please enter a company domain");
      return false;
    }

    // Package name validation
    String packageName = myState.get(PACKAGE_NAME_KEY);
    if (packageName == null) {
      setErrorHtml("Please enter a package name (This package uniquely identifies your application)");
      return false;
    } else {
      String message = AndroidUtils.validateAndroidPackageName(packageName);
      if (message != null) {
        setErrorHtml(message);
        return false;
      }
    }

    // Project location validation
    String projectLocation = myState.get(PROJECT_LOCATION_KEY);
    if (projectLocation == null || projectLocation.isEmpty()) {
      setErrorHtml("Please specify a project location");
      return false;
    }
    // Check the separators
    if ((File.separatorChar == '/' && projectLocation.contains("\\")) ||
        (File.separatorChar == '\\' && projectLocation.contains("/"))) {
      setErrorHtml("Your project location contains incorrect slashes ('\\' vs '/')");
      return false;
    }
    // Check the individual components for not allowed characters.
    File testFile = new File(projectLocation);
    while (testFile != null) {
      String filename = testFile.getName();
      if (ILLEGAL_CHARACTER_MATCHER.matchesAnyOf(filename)) {
        char illegalChar = filename.charAt(ILLEGAL_CHARACTER_MATCHER.indexIn(filename));
        setErrorHtml(String.format("Illegal character in project location path: '%c' in filename: %s", illegalChar, filename));
        return false;
      }
      if (INVALID_MSFT_FILENAMES.contains(filename.toLowerCase())) {
        setErrorHtml("Illegal filename in project location path: " + filename);
        return false;
      }
      if (CharMatcher.WHITESPACE.matchesAnyOf(filename)) {
        setErrorHtml("Your project location contains whitespace. This can cause " + "problems on some platforms and is not recommended.");
      }
      if (!CharMatcher.ASCII.matchesAllOf(filename)) {
        setErrorHtml("Your project location contains non-ASCII characters. " + "This can cause problems on Windows. Proceed with caution.");
      }
      // Check that we can write to that location: make sure we can write into the first extant directory in the path.
      if (!testFile.exists() && testFile.getParentFile() != null && testFile.getParentFile().exists()) {
        if (!testFile.getParentFile().canWrite()) {
          setErrorHtml(String.format("The path '%s' is not writeable. Please choose a new location.", testFile.getParentFile().getPath()));
          return false;
        }
      }
      testFile = testFile.getParentFile();
    }

    File file = new File(projectLocation);
    if (file.isFile()) {
      setErrorHtml("There must not already be a file at the project location");
      return false;
    } else if (file.isDirectory() && TemplateUtils.listFiles(file).length > 0) {
      setErrorHtml("A non-empty directory already exists at the specified project location. " +
                   "Existing files may be overwritten. Proceed with caution.");
    }
    if (file.getParent() == null) {
      setErrorHtml("The project location can not be at the filesystem root");
      return false;
    }
    if (file.getParentFile().exists() && !file.getParentFile().isDirectory()) {
      setErrorHtml("The project location's parent directory must be a directory, not a plain file");
      return false;
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

  @Nullable
  public String getHelpText(@NotNull Key<?> key) {
    if (key.equals(APPLICATION_NAME_KEY)) {
      return "The application name is shown in the Play store, as well as in the Manage Applications list in Settings.";
    } else if (key.equals(PACKAGE_NAME_KEY)) {
      return "The package name must be a unique identifier for your application.\n It is typically not shown to users, " +
             "but it <b>must</b> stay the same for the lifetime of your application; it is how multiple versions of the same application " +
             "are considered the \"same app\".\nThis is typically the reverse domain name of your organization plus one or more " +
             "application identifiers, and it must be a valid Java package name.";
    } else {
      return null;
    }
  }

  private static final ValueDeriver<String> myPackageNameDeriver = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(APPLICATION_NAME_KEY, COMPANY_DOMAIN_KEY);
    }

    String nameToPackage(String name) {
      name = name.replace('-', '_');
      name = name.replaceAll("[^a-zA-Z0-9_]", "");
      name = name.toLowerCase();
      return name;
    }

    @Nullable
    @Override
    public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
      String projectName = state.get(APPLICATION_NAME_KEY);
      if (projectName == null) {
        projectName = "app";
      }
      projectName = nameToPackage(projectName);
      String companyDomain = state.get(COMPANY_DOMAIN_KEY);
      if (companyDomain == null) {
        companyDomain = EXAMPLE_DOMAIN;
      }
      ArrayList domainParts = Lists.newArrayList(companyDomain.split("\\."));
      String reversedDomain = Joiner.on('.').join(Lists.reverse(domainParts));
      return reversedDomain + '.' + projectName;
    }
  };

  private static final ValueDeriver<String> myProjectLocationDeriver = new ValueDeriver<String>() {
    @Nullable
    @Override
    public Set<Key<?>> getTriggerKeys() {
      return makeSetOf(APPLICATION_NAME_KEY);
    }

    @Nullable
    @Override
    public String deriveValue(ScopedStateStore state, Key changedKey, @Nullable String currentValue) {
      String name = state.get(APPLICATION_NAME_KEY);
      name = name == null ? "" : name;
      name = name.replaceAll(INVALID_FILENAME_CHARS, "");
      name = name.replaceAll("\\s", "");
      File baseDirectory = new File(NewProjectWizardState.getProjectFileDirectory());
      File projectDir = new File(baseDirectory, name);
      int i = 2;
      while (projectDir.exists()) {
        projectDir = new File(baseDirectory, name + i);
        i++;
      }
      return projectDir.getPath();
    }
  };
}
