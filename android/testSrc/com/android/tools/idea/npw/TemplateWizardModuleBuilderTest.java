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

import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.TemplateManager;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;
import java.util.ArrayList;

import static com.android.tools.idea.npw.AssetStudioAssetGenerator.ATTR_ASSET_TYPE;
import static com.android.tools.idea.npw.FormFactorUtils.ATTR_MODULE_NAME;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.npw.NewModuleWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.Template.CATEGORY_PROJECTS;
import static com.android.tools.idea.templates.TemplateMetadata.*;

/**
 * Tests for creating modules
 */
public class TemplateWizardModuleBuilderTest extends AndroidGradleTestCase {

  File myProjectRoot;

  public void testConstructor() throws Exception {
    // Null project means creating a new project
    TemplateWizardModuleBuilder moduleBuilder1 =
      new TemplateWizardModuleBuilder(null, null, null, null, new ArrayList<>(), getTestRootDisposable(), false);
    assertTrue(moduleBuilder1.myWizardState.getBoolean(ATTR_IS_LAUNCHER));
    assertEquals(WizardUtils.getProjectLocationParent().getPath(), moduleBuilder1.myWizardState.get(ATTR_PROJECT_LOCATION));
    assertDoesntContain(moduleBuilder1.myWizardState.myHidden, ATTR_MODULE_NAME);

    assertNotNull(moduleBuilder1.myWizardState.get(ATTR_GRADLE_VERSION));
    assertNotNull(moduleBuilder1.myWizardState.get(ATTR_GRADLE_PLUGIN_VERSION));

    assertEquals(AssetStudioAssetGenerator.AssetType.LAUNCHER.name(), moduleBuilder1.myWizardState.getString(ATTR_ASSET_TYPE));
    assertTrue(moduleBuilder1.myInitializationComplete);

    // Non-null project means we're adding a module to an existing project
    TemplateWizardModuleBuilder moduleBuilder2 =
      new TemplateWizardModuleBuilder(null, null, getProject(), null, new ArrayList<>(), getTestRootDisposable(), true);
    assertFalse(moduleBuilder2.myWizardState.getBoolean(ATTR_IS_LAUNCHER));
    assertContainsElements(moduleBuilder2.myWizardState.myHidden, ATTR_MODULE_NAME);

    assertNotNull(moduleBuilder2.myWizardState.get(ATTR_PROJECT_LOCATION));
    String basePath = getProject().getBasePath();
    assert basePath != null;
    assertEquals(FileUtil.toSystemIndependentName(basePath), moduleBuilder2.myWizardState.getString(ATTR_PROJECT_LOCATION));
  }

  private TemplateWizardModuleBuilder setUpModuleCreator(String templateName) {
    String basePath = getProject().getBasePath();
    assert basePath != null;
    myProjectRoot = new File(basePath);
    File templateFile = new File(TemplateManager.getTemplateRootFolder(), FileUtil.join(CATEGORY_PROJECTS, templateName));
    assertTrue(templateFile.exists());

    final TemplateWizardModuleBuilder moduleBuilder = new TemplateWizardModuleBuilder(templateFile,
                                                                                      null,
                                                                                      getProject(),
                                                                                      null,
                                                                                      new ArrayList<>(),
                                                                                      getTestRootDisposable(),
                                                                                      false);

    moduleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);
    moduleBuilder.myWizardState.put(ATTR_PACKAGE_NAME, "com.test.foo");
    moduleBuilder.myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    moduleBuilder.myWizardState.put(ATTR_MODULE_NAME, "app");
    moduleBuilder.myWizardState.put(ATTR_CREATE_ICONS, false);

    return moduleBuilder;
  }

  public void testCreateAndroidApplicationModule() throws Exception {
    final TemplateWizardModuleBuilder moduleBuilder = setUpModuleCreator(WizardConstants.MODULE_TEMPLATE_NAME);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        moduleBuilder.getDefaultPath().createModule();
        assertFilesExist(myProjectRoot,
                         "settings.gradle",
                         "app",
                         "app/src/main/java/com/test/foo",
                         "app/src/main/AndroidManifest.xml",
                         "app/src/main/res",
                         "app/libs",
                         "app/.gitignore",
                         "app/build.gradle",
                         "app/proguard-rules.pro");
        File buildGradleFile = new File(myProjectRoot, FileUtil.join("app", "build.gradle"));
        String contents = TemplateUtils.readTextFromDisk(buildGradleFile);
        assertNotNull(contents);
        assertFalse(contents.contains("android-library"));
        assertTrue(contents.contains("apply plugin: 'com.android.application'"));

        File settingsGradleFile = new File(myProjectRoot, "settings.gradle");
        contents = TemplateUtils.readTextFromDisk(settingsGradleFile);
        assertNotNull(contents);
        assertTrue(contents.contains("app"));
      }
    });
  }

  public void testCreateAndroidLibraryModule() throws Exception {
    final TemplateWizardModuleBuilder moduleBuilder = setUpModuleCreator(WizardConstants.MODULE_TEMPLATE_NAME);

    moduleBuilder.myWizardState.put(ATTR_IS_LIBRARY_MODULE, true);
    moduleBuilder.myWizardState.put(ATTR_MODULE_NAME, "lib");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        moduleBuilder.getDefaultPath().createModule();
        assertFilesExist(myProjectRoot,
                         "settings.gradle",
                         "lib",
                         "lib/src/main/java/com/test/foo",
                         "lib/src/main/AndroidManifest.xml",
                         "lib/src/main/res",
                         "lib/libs",
                         "lib/.gitignore",
                         "lib/build.gradle",
                         "lib/proguard-rules.pro");
        File buildGradleFile = new File(myProjectRoot, FileUtil.join("lib", "build.gradle"));
        String contents = TemplateUtils.readTextFromDisk(buildGradleFile);
        assertNotNull(contents);
        assertTrue(contents.contains("apply plugin: 'com.android.library'"));

        File settingsGradleFile = new File(myProjectRoot, "settings.gradle");
        contents = TemplateUtils.readTextFromDisk(settingsGradleFile);
        assertNotNull(contents);
        assertTrue(contents.contains("lib"));
      }
    });
  }

  public void testCreateJavaLibraryModule() throws Exception {
    final TemplateWizardModuleBuilder moduleBuilder = setUpModuleCreator("NewJavaLibrary");

    moduleBuilder.myWizardState.put(ATTR_MODULE_NAME, "lib");
    moduleBuilder.myWizardState.put("className", "FooClass");

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        moduleBuilder.getDefaultPath().createModule();
        assertFilesExist(myProjectRoot,
                         "lib",
                         "lib/src/main/java/com/test/foo/FooClass.java",
                         "lib/libs",
                         "lib/.gitignore",
                         "lib/build.gradle");
        File buildGradleFile = new File(myProjectRoot, FileUtil.join("lib", "build.gradle"));
        String contents = TemplateUtils.readTextFromDisk(buildGradleFile);
        assertNotNull(contents);
        assertFalse(contents.contains("android"));
        assertTrue(contents.contains("apply plugin: 'java'"));

        File settingsGradleFile = new File(myProjectRoot, "settings.gradle");
        contents = TemplateUtils.readTextFromDisk(settingsGradleFile);
        assertNotNull(contents);
        assertTrue(contents.contains("lib"));
      }
    });
  }
}
