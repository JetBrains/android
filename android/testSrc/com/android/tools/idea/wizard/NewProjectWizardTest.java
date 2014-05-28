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
package com.android.tools.idea.wizard;

import com.android.SdkConstants;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;

import java.io.File;

import static com.android.tools.idea.templates.TemplateMetadata.*;
import static com.android.tools.idea.wizard.NewProjectWizardState.*;
import static org.mockito.Mockito.*;

/**
 * Tests for the New Project Wizard.
 */
public class NewProjectWizardTest extends AndroidGradleTestCase {

  NewProjectWizard myWizard;
  NewProjectWizardState myWizardState;

  private static final String MODULE_NAME = "thisisamodulename";

  @Override
  protected boolean requireRecentSdk() {
    // Need valid SDK templates
    return true;
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myWizard = new NewProjectWizard();
    myWizardState = myWizard.myWizardState;
  }

  @Override
  public void tearDown() throws Exception {
    Disposer.dispose(myWizard.getDisposable());
    super.tearDown();
  }

  public void testInit() throws Exception {
    // After construction
    assertNotNull(myWizardState);
    assertTrue(myWizardState.hasAttr(ATTR_GRADLE_VERSION));
    assertTrue(myWizardState.hasAttr(ATTR_GRADLE_PLUGIN_VERSION));

    assertTrue(myWizardState.hasAttr(ATTR_MIN_API));
    assertTrue(myWizardState.get(ATTR_MIN_API) instanceof Integer);

    assertTrue(myWizardState.hasAttr(ATTR_BUILD_API));
    assertTrue(myWizardState.get(ATTR_BUILD_API) instanceof Integer);

    assertTrue(myWizardState.hasAttr(ATTR_MIN_API_LEVEL));
    assertTrue(myWizardState.get(ATTR_MIN_API_LEVEL) instanceof Integer);

    assertTrue(myWizardState.hasAttr(ATTR_TARGET_API));
    assertTrue(myWizardState.get(ATTR_TARGET_API) instanceof Integer);
    assertTrue(myWizardState.hasAttr(ATTR_TARGET_API_STRING));
    assertTrue(myWizardState.get(ATTR_TARGET_API_STRING) instanceof String);

    assertEquals(4, myWizard.getStepCount());
    assertNotNull(myWizard.myAssetSetStep);
    assertNotNull(myWizard.myChooseActivityStep);
    assertNotNull(myWizard.myActivityParameterStep);

    assertTrue(myWizard.myInitializationComplete);
  }

  public void testUpdate() throws Exception {
    myWizardState.put(ATTR_CREATE_ICONS, true);
    myWizardState.put(ATTR_CREATE_ACTIVITY, true);

    myWizard.update();

    assertVisibilityMatchesParameter(myWizard.myAssetSetStep, ATTR_CREATE_ICONS);
    assertVisibilityMatchesParameter(myWizard.myChooseActivityStep, ATTR_CREATE_ACTIVITY);
    assertVisibilityMatchesParameter(myWizard.myActivityParameterStep, ATTR_CREATE_ACTIVITY);

    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);

    myWizard.update();

    assertVisibilityMatchesParameter(myWizard.myAssetSetStep, ATTR_CREATE_ICONS);
    assertVisibilityMatchesParameter(myWizard.myChooseActivityStep, ATTR_CREATE_ACTIVITY);
    assertVisibilityMatchesParameter(myWizard.myActivityParameterStep, ATTR_CREATE_ACTIVITY);
  }

  private void assertVisibilityMatchesParameter(TemplateWizardStep step, String paramName) {
    assertEquals(myWizardState.getBoolean(paramName), step.isStepVisible());
  }


  /**
   * Sets up standard project/module creation
   */
  private void setUpStandardProjectCreation() throws Exception {
    File baseDir = new File(getProject().getBasePath());
    myWizardState.put(ATTR_MODULE_NAME, MODULE_NAME);
    myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
    String projectName = "ThisIsAProjectName";
    myWizardState.put(ATTR_APP_TITLE, projectName);
    myWizardState.put(ATTR_PACKAGE_NAME, "com.test.package");
  }

  /**
   * Returns the module directory.
   */
  private File runCommonCreateProjectTest() throws Exception {
    File baseDir = new File(myWizardState.getString(ATTR_PROJECT_LOCATION));

    // Do the project creation
    myWizard.createProject();

    // Make sure we created the project-level files
    assertFilesExist(baseDir,
                     "build.gradle",
                     ".gitignore",
                     "settings.gradle",
                     "gradle.properties",
                     "local.properties");

    // Make sure we created the module files
    File moduleBase = new File(baseDir, myWizardState.getString(ATTR_MODULE_NAME));
    assertFilesExist(moduleBase,
                     ".gitignore",
                     "build.gradle",
                     "proguard-rules.pro",
                     "src/main/res/values/strings.xml",
                     "src/main/AndroidManifest.xml");

    return moduleBase;
  }

  public void testCreateProjectNoActivityNoIconsLibrary() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, true);

    setUpStandardProjectCreation();
    File moduleDir = runCommonCreateProjectTest();
    File gradleFile = new File(moduleDir, SdkConstants.FN_BUILD_GRADLE);

    String gradleContents = TemplateUtils.readTextFile(gradleFile);
    assertNotNull(gradleContents);
    assertTrue(gradleContents.contains("apply plugin: 'android-library'"));

    File manifestFile = new File(moduleDir, FileUtil.join("src", "main", SdkConstants.ANDROID_MANIFEST_XML));
    String manifestContents = TemplateUtils.readTextFile(manifestFile);
    assertNotNull(manifestContents);
    assertFalse(manifestContents.contains("android:theme"));

    assertFilesExist(moduleDir,
                     "src/main/java/com/test/package",
                     "src/main/res/drawable-hdpi/ic_launcher.png",
                     "src/main/res/drawable-mdpi/ic_launcher.png",
                     "src/main/res/drawable-xhdpi/ic_launcher.png",
                     "src/main/res/drawable-xxhdpi/ic_launcher.png");
  }

  public void testCreateProjectNoActivityNoIconsApplication() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);

    setUpStandardProjectCreation();
    File moduleDir = runCommonCreateProjectTest();
    File gradleFile = new File(moduleDir, "build.gradle");

    String gradleContents = TemplateUtils.readTextFile(gradleFile);
    assertNotNull(gradleContents);
    assertTrue(gradleContents.contains("apply plugin: 'android'"));

    File manifestFile = new File(moduleDir, FileUtil.join("src", "main", SdkConstants.ANDROID_MANIFEST_XML));
    String manifestContents = TemplateUtils.readTextFile(manifestFile);
    assertNotNull(manifestContents);
    assertTrue(manifestContents.contains("android:theme"));

    assertFilesExist(moduleDir,
                     "src/main/res/values/styles.xml",
                     "src/main/java/com/test/package",
                     "src/main/res/drawable-hdpi/ic_launcher.png",
                     "src/main/res/drawable-mdpi/ic_launcher.png",
                     "src/main/res/drawable-xhdpi/ic_launcher.png",
                     "src/main/res/drawable-xxhdpi/ic_launcher.png");
  }

  @SuppressWarnings("unchecked")
  public void testCreateProjectWithActivityWithIcons() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, true);
    myWizardState.put(ATTR_CREATE_ICONS, true);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);

    AssetStudioAssetGenerator launcherIconStateMock = mock(AssetStudioAssetGenerator.class);
    NewProjectWizardState spyState = spy(myWizardState);
    myWizard.myWizardState = spyState;
    myWizard.myAssetGenerator = launcherIconStateMock;

    TemplateWizardState activityStateMock = spy(myWizardState.getActivityTemplateState());
    Template activityTemplateMock = mock(Template.class);
    when(activityStateMock.getTemplate()).thenReturn(activityTemplateMock);
    when(spyState.getActivityTemplateState()).thenReturn(activityStateMock);

    setUpStandardProjectCreation();
    File moduleRoot = runCommonCreateProjectTest();

    verify(launcherIconStateMock).outputImagesIntoDefaultVariant(eq(moduleRoot));
    verify(activityTemplateMock).render(eq(moduleRoot), eq(moduleRoot),
                                        eq(myWizardState.myActivityTemplateState.myParameters));
  }

  @SuppressWarnings("unchecked")
  public void testCreateProjectNoActivityNoIcons() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);

    AssetStudioAssetGenerator launcherIconStateMock = mock(AssetStudioAssetGenerator.class);
    NewProjectWizardState spyState = spy(myWizardState);
    myWizard.myWizardState = spyState;
    myWizard.myAssetGenerator = launcherIconStateMock;

    TemplateWizardState activityStateMock = spy(myWizardState.getActivityTemplateState());
    Template activityTemplateMock = mock(Template.class);
    when(activityStateMock.getTemplate()).thenReturn(activityTemplateMock);
    when(spyState.getActivityTemplateState()).thenReturn(activityStateMock);

    setUpStandardProjectCreation();
    runCommonCreateProjectTest();

    verifyZeroInteractions(activityStateMock, activityTemplateMock, launcherIconStateMock);
  }

  @SuppressWarnings("unchecked")
  public void testCreateProjectWithWeirdFileNames() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);
    String projectName = "ThisIsAProjectName";
    myWizardState.put(ATTR_APP_TITLE, projectName);
    myWizardState.put(ATTR_PACKAGE_NAME, "com.test.package");
    myWizardState.put(ATTR_MODULE_NAME, MODULE_NAME);
    File baseDir;

    // Check apostrophe in file path
    baseDir = new File(getProject().getBasePath(), "test'orama");
    myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
    runCommonCreateProjectTest();
    resetDirectoryParams();

    // The following characters are reserved on a Windows system
    if (!System.getProperty("os.name").startsWith("Windows")) {
      // Check quote in file path
      baseDir = new File(getProject().getBasePath(), "test\"orama");
      myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
      runCommonCreateProjectTest();
      resetDirectoryParams();

      // Check unicode characters
      baseDir = new File(getProject().getBasePath(), "test\uD83D\uDCA9orama");
      myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
      runCommonCreateProjectTest();
      resetDirectoryParams();

      // Check < and > characters
      baseDir = new File(getProject().getBasePath(), "test<orama");
      myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
      runCommonCreateProjectTest();
      resetDirectoryParams();

      baseDir = new File(getProject().getBasePath(), "test>orama");
      myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
      runCommonCreateProjectTest();
      resetDirectoryParams();
    }
  }

  private void resetDirectoryParams() {
    myWizardState.myParameters.remove(ATTR_RES_OUT);
    myWizardState.myParameters.remove(ATTR_SRC_OUT);
    myWizardState.myParameters.remove(ATTR_MANIFEST_OUT);
  }
}
