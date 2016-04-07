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
package com.android.tools.idea.npw;

import com.android.sdklib.IAndroidTarget;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.google.common.collect.Sets;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.sdk.AndroidSdkUtils;

import javax.swing.*;
import java.io.File;
import java.util.Locale;
import java.util.Set;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;

/**
 * Test cases for the Android Module Configuration wizard step.
 */
public class ConfigureAndroidModuleStepTest extends AndroidGradleTestCase {

  ConfigureAndroidModuleStep myStep;
  NewModuleWizardState myState;
  @Override
  public void setUp() throws Exception {
    super.setUp();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ModuleManager manager = ModuleManager.getInstance(getProject());
        File moduleRoot = new File(getProject().getBasePath(), "app");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);

        moduleRoot = new File(getProject().getBasePath(), "Lib");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);

        moduleRoot = new File(getProject().getBasePath(), "lib2");
        manager.newModule(moduleRoot.getPath(), ModuleTypeId.JAVA_MODULE);
      }
    });

    myState = new NewModuleWizardState();
    myState.put(ATTR_APP_TITLE, "My Project");

    myStep = new ConfigureAndroidModuleStep(myState, null, null,
                                        TemplateWizardStep.NONE);
    myStep.updateStep();
    myState.myHidden.clear();
  }

  public void testAPILevelPopulation() throws Exception {
    Set<String> targetApiLevels = Sets.newHashSet();
    ComboBoxModel cbm = myStep.myTargetSdk.getModel();
    for (int i = 0; i < cbm.getSize(); ++i) {
      targetApiLevels.add(cbm.getElementAt(i).toString());
    }

    Set<String> minApiLevels = Sets.newHashSet();
    cbm = myStep.myMinSdk.getModel();
    for (int i = 0; i < cbm.getSize(); ++i) {
      minApiLevels.add(cbm.getElementAt(i).toString());
    }

    assertContainsElements(targetApiLevels, TemplateUtils.getKnownVersions());
    assertContainsElements(minApiLevels, TemplateUtils.getKnownVersions());

    Set<String> compileTargets = Sets.newHashSet();
    cbm = myStep.myCompileWith.getModel();
    for (int i = 0; i < cbm.getSize(); ++i) {
      compileTargets.add(cbm.getElementAt(i).toString());
    }

    Set<String> expectedCompileTargets = Sets.newHashSet();
    for (IAndroidTarget target : myStep.getCompilationTargets()) {
      expectedCompileTargets.add(AndroidSdkUtils.getTargetLabel(target));
    }

    assertContainsElements(compileTargets, expectedCompileTargets);
  }

  public void testDeriveValues() throws Exception {
    resetValues();

    myState.put(ATTR_IS_LIBRARY_MODULE, true);
    myStep.deriveValues();
    assertEquals(NewModuleWizardState.LIB_NAME, myState.getString(ATTR_MODULE_NAME));

    myState.put(ATTR_IS_LIBRARY_MODULE, false);
    myStep.deriveValues();
    assertEquals(NewModuleWizardState.APP_NAME, myState.getString(ATTR_MODULE_NAME));

    resetValues();

    myState.put(ATTR_MODULE_NAME, "foo");
    myState.myModified.add(ATTR_MODULE_NAME);
    myStep.deriveValues();
    assertEquals("com.example.apptitle.foo", myState.getString(ATTR_PACKAGE_NAME));

    resetValues();

    myState.put(ATTR_APP_TITLE, "This Is An App");
    myStep.deriveValues();
    assertTrue(myState.getString(ATTR_PROJECT_LOCATION).endsWith("ThisIsAnApp"));
  }

  private void assertValidationError(String error) throws Exception {
    assertFalse(myStep.validate());
    assertEquals(error, myStep.getError().getText().replaceAll("<.*?>", ""));
  }

  private void assertValidationWarning(String warning) throws Exception {
    assertTrue(myStep.validate());
    assertEquals(warning, myStep.getError().getText().replaceAll("<.*?>", ""));
  }

  private void resetValues() {
    myState.put(ATTR_APP_TITLE, "App Title");
    myState.put(ATTR_PACKAGE_NAME, "com.foo.bar");
    myState.put(ATTR_MODULE_NAME, "app");
    myState.put(ATTR_MIN_API, "8");
    myState.put(ATTR_MIN_API_LEVEL, 8);
    myState.put(ATTR_BUILD_API, 19);
    myState.put(ATTR_BUILD_API_STRING, "19");
    myState.put(ATTR_TARGET_API, 19);
    myState.put(ATTR_TARGET_API_STRING, "19");
    myState.put(ATTR_MIN_API_LEVEL, 8);
    myState.put(ATTR_JAVA_VERSION, "1.6");
    myState.put(ATTR_PROJECT_LOCATION, FileUtil.join(FileUtil.toSystemDependentName(getProject().getBasePath()), "SafeTestLocation"));

    myState.myHidden.clear();
    myState.myModified.clear();
  }

  public void testValidate() throws Exception {
    resetValues();
    assertTrue(myStep.validate());

    // Check the error messages shown to the user

    myState.put(ATTR_APP_TITLE, "");
    assertValidationError("Enter an application name (shown in launcher)");

    myState.put(ATTR_APP_TITLE, "app");
    assertValidationWarning("The application name for most apps begins with an uppercase letter");

    resetValues();

    myState.put(ATTR_PACKAGE_NAME, "com.example.blah");
    assertValidationWarning("The prefix 'com.example.' is meant as a placeholder and should not be used");

    resetValues();

    myState.put(ATTR_MODULE_NAME, "");
    assertValidationError("Please specify a module name.");

    myState.put(ATTR_MODULE_NAME, "/?%");
    assertValidationError("Invalid module name.");

    resetValues();

    myState.put(ATTR_MIN_API_LEVEL, null);
    assertValidationError("Select a minimum SDK version");

    resetValues();

    myState.put(ATTR_TARGET_API, null);
    myState.put(ATTR_TARGET_API_STRING, null);
    assertValidationError("Select a target SDK");

    resetValues();

    myState.put(ATTR_BUILD_API, null);
    assertValidationError("Select a compile target");

    resetValues();

    myState.myParameters.remove(ATTR_BUILD_API);
    assertValidationError("Select a compile target");

    resetValues();

    myState.put(ATTR_MIN_API_LEVEL, 15);
    myState.put(ATTR_TARGET_API, 10);
    myState.put(ATTR_TARGET_API, "10");
    assertValidationError("The target SDK version should be at least as high as the minimum SDK version");

    resetValues();

    myState.put(ATTR_MIN_API_LEVEL, 15);
    myState.put(ATTR_BUILD_API, 10);
    assertValidationError("The build target version should be at least as high as the minimum SDK version");

    resetValues();

    myState.put(ATTR_JAVA_VERSION, "1.7");
    myState.put(ATTR_BUILD_API, 18);
    assertValidationError("Using Java language level 7 requires compiling with API 19: Android 4.4 (KitKat)");

    myState.put(ATTR_BUILD_API, 19);
    myState.put(ATTR_MIN_API_LEVEL, 8);
    assertValidationWarning("Note: With minSdkVersion less than 19, you cannot use try-with-resources, but other Java 7 language " +
                            "features are fine");

    resetValues();

    myState.put(ATTR_PROJECT_LOCATION, "");
    assertValidationError("Please specify a project location");

    assertTrue(new File(getProject().getBasePath()).exists());
    myState.put(ATTR_PROJECT_LOCATION, System.getProperty("java.home"));
    assertValidationWarning("A non-empty directory already exists at the specified project location. " +
                            "Existing files may be overwritten. Proceed with caution.");

    File relative = new File(FileUtil.toSystemDependentName("foo"));
    assertNull(relative.getParent());
    myState.put(ATTR_PROJECT_LOCATION, relative.getPath());
    assertValidationError("The project location can not be at the filesystem root");

    // Set the file separator so tests are repeatable on backwards filesystems
    File plainFile = new File(getProject().getBasePath(), "file.txt");
    assertTrue(plainFile.createNewFile());
    File plainFileRooted = new File(plainFile, "fooDir");
    assertFalse(plainFileRooted.exists());

    myState.put(ATTR_PROJECT_LOCATION, plainFileRooted.getPath());
    assertValidationError("The project location's parent directory must be a directory, not a plain file");

    String basePath = FileUtil.toSystemDependentName(getProject().getBasePath()) + File.separator;

    if (File.separatorChar == '/') {
      myState.put(ATTR_PROJECT_LOCATION, basePath + "My\\ApplicationProject");
      assertValidationError("Your project location contains incorrect slashes ('\\' vs '/')");
    } else {
      myState.put(ATTR_PROJECT_LOCATION, basePath + "My/ApplicationProject");
      assertValidationError("Your project location contains incorrect slashes ('\\' vs '/')");
    }

    myState.put(ATTR_PROJECT_LOCATION, basePath + "My>ApplicationProject");
    assertValidationError("Illegal character in project location path: '>' in filename: My>ApplicationProject");

    myState.put(ATTR_PROJECT_LOCATION, basePath + "My?ApplicationProject");
    assertValidationError("Illegal character in project location path: '?' in filename: My?ApplicationProject");

    myState.put(ATTR_PROJECT_LOCATION, basePath + "MyApplication*Project");
    assertValidationError("Illegal character in project location path: '*' in filename: MyApplication*Project");

    myState.put(ATTR_PROJECT_LOCATION, basePath + "MyApplicationPro|ject");
    assertValidationError("Illegal character in project location path: '|' in filename: MyApplicationPro|ject");

    myState.put(ATTR_PROJECT_LOCATION, basePath + "My Application Project");
    assertValidationWarning("Your project location contains whitespace. This can cause problems on some platforms and is not recommended.");

    myState.put(ATTR_PROJECT_LOCATION, basePath + "My \u2603 Project");
    assertValidationWarning("Your project location contains non-ASCII characters. This can cause problems on Windows. Proceed with caution.");

    // Java's File.setReadOnly() returns false on Windows if the file is a directory.
    // Probably that's because Windows' read-only attribute for folders isn't clearly defined and isn't treated in the same way as in Linux
    // - typically one can still write there, and therefore the canWrite() check which this test relies on will still return true.
    // So this testcase assertion only makes sense on non-Windows platforms.
    // Simulating non-writable directory on Windows would probably involve some tricks with the security and ownership, but
    // that is likely to involve a significant effort for little or no value
    if (!SystemInfo.isWindows) {
      File nonEditableFile = new File(FileUtil.toSystemDependentName(getProject().getBasePath()), "NotEditable");
      nonEditableFile.mkdir();
      nonEditableFile.setReadOnly();
      myState.put(ATTR_PROJECT_LOCATION, new File(nonEditableFile, "myapp").getPath());
      assertValidationError(String.format("The path '%s' is not writeable. Please choose a new location.", nonEditableFile.getPath()));
    }

    resetValues();

    ModuleManager manager = ModuleManager.getInstance(getProject());
    assertTrue("No modules found", manager.getModules().length > 0);
    myStep = new ConfigureAndroidModuleStep(myState, getProject(), null, TemplateWizardStep.NONE);
    Module module = manager.getModules()[0];
    assertNotNull(module.getName());
    assertFalse(module.getName().isEmpty());

    myState.myHidden.add(ATTR_PROJECT_LOCATION);
    myState.put(ATTR_MODULE_NAME, module.getName());
    assertValidationError(String.format(Locale.getDefault(), "Module %1$s already exists", module.getName()));
  }

  public void testComputeUniqueProjectLocation() throws Exception {
    File oldLocation = new File(getProject().getBasePath(), "TestProject");
    assertTrue(oldLocation.createNewFile());
    myState.put(ATTR_PROJECT_LOCATION, oldLocation.getAbsolutePath());
    myState.put(ATTR_APP_TITLE, "Test Project");

    myStep.computeUniqueProjectLocation();
    String newName = myState.getString(ATTR_APP_TITLE);
    File newLocation = new File(myState.getString(ATTR_PROJECT_LOCATION));
    assertEquals("Test Project 2", newName);
    assertEquals("TestProject2", newLocation.getName());
  }

  public void testIsValidModuleName() throws Exception {
    assertTrue(ConfigureAndroidModuleStep.isValidModuleName("app"));
    assertTrue(ConfigureAndroidModuleStep.isValidModuleName("lib"));
    assertFalse(ConfigureAndroidModuleStep.isValidModuleName("123:456"));
    assertFalse(ConfigureAndroidModuleStep.isValidModuleName("$boot"));

    for (String s : ConfigureAndroidModuleStep.INVALID_MSFT_FILENAMES) {
      assertFalse(ConfigureAndroidModuleStep.isValidModuleName(s));
    }
  }

  public void testVisibility() throws Exception {
    myState.myHidden.add(ATTR_CREATE_ICONS);
    myStep.updateStep();
    assertFalse(myStep.myCreateCustomLauncherIconCheckBox.isVisible());

    myState.myHidden.remove(ATTR_CREATE_ICONS);
    myStep.updateStep();
    assertTrue(myStep.myCreateCustomLauncherIconCheckBox.isVisible());
  }

  public void testComputeModuleName() throws Exception {
    ModuleManager manager = ModuleManager.getInstance(getProject());
    myStep = new ConfigureAndroidModuleStep(myState, getProject(), null, TemplateWizardStep.NONE);
    Module module = manager.getModules()[0];
    assertEquals("app", module.getName());

    myState.myHidden.add(ATTR_PROJECT_LOCATION);
    myState.put(ATTR_IS_LIBRARY_MODULE, true);
    // "Lib" and "lib2" already exist
    assertEquals("lib3", myStep.computeModuleName());

    myState.put(ATTR_IS_LIBRARY_MODULE, false);
    // "app" already exists
    assertEquals("app2", myStep.computeModuleName());
  }

  public void testComputePackageName() throws Exception {
    myState.put(ATTR_APP_TITLE, "My Awesome App");

    myState.put(ATTR_MODULE_NAME, "App-Foo");
    assertEquals("com.example.myawesomeapp.app_foo", myStep.computePackageName());

    myState.put(ATTR_MODULE_NAME, "$asdf$");
    assertEquals("com.example.myawesomeapp.asdf", myStep.computePackageName());

    myState.put(ATTR_MODULE_NAME, "App_Foo");
    assertEquals("com.example.myawesomeapp.app_foo", myStep.computePackageName());

    myState.put(ATTR_APP_TITLE, "");
    assertEquals("com.example.app_foo", myStep.computePackageName());
  }

  public void testComputePackagePrefix() {
    myState.put(ATTR_MODULE_NAME, "");
    myState.put(ATTR_APP_TITLE, "");

    assertEquals("com.foo", myStep.computePackagePrefix("com.foo"));

    myState.put(ATTR_MODULE_NAME, "app");
    assertEquals("com.foo.", myStep.computePackagePrefix("com.foo.app"));

    myState.put(ATTR_APP_TITLE, "My Cool App");
    assertEquals("com.foo.", myStep.computePackagePrefix("com.foo.mycoolapp.app"));

    myState.put(ATTR_MODULE_NAME, "");
    assertEquals("com.foo.", myStep.computePackagePrefix("com.foo.mycoolapp.app"));
    assertEquals("com.foo.", myStep.computePackagePrefix("com.foo.mycoolapp"));
  }
}
