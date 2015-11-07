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

import com.android.SdkConstants;
import com.android.tools.idea.templates.AndroidGradleTestCase;
import com.android.tools.idea.templates.Template;
import com.android.tools.idea.templates.TemplateUtils;
import com.android.tools.idea.templates.recipe.RenderingContext;
import com.android.tools.idea.wizard.template.TemplateWizardState;
import com.android.tools.idea.wizard.template.TemplateWizardStep;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.io.FileUtil;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.util.Map;

import static com.android.tools.idea.npw.NewProjectWizardState.ATTR_CREATE_ACTIVITY;
import static com.android.tools.idea.npw.NewProjectWizardState.ATTR_PROJECT_LOCATION;
import static com.android.tools.idea.templates.TemplateMetadata.*;
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
    try {
      myWizard.close(DialogWrapper.OK_EXIT_CODE);
    }
    finally {
      super.tearDown();
    }
  }

  public void testInit() throws Exception {
    // After construction
    assertNotNull(myWizardState);
    assertTrue(myWizardState.hasAttr(ATTR_GRADLE_VERSION));
    assertTrue(myWizardState.hasAttr(ATTR_GRADLE_PLUGIN_VERSION));

    assertTrue(myWizardState.hasAttr(ATTR_MIN_API));
    assertTrue(myWizardState.get(ATTR_MIN_API) instanceof String);

    assertTrue(myWizardState.hasAttr(ATTR_BUILD_API));
    assertTrue(myWizardState.get(ATTR_BUILD_API) instanceof Integer);
    assertTrue(myWizardState.hasAttr(ATTR_BUILD_API_STRING));
    assertTrue(myWizardState.get(ATTR_BUILD_API_STRING) instanceof String);

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
    myWizardState.put(FormFactorUtils.ATTR_MODULE_NAME, MODULE_NAME);
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
    myWizard.createProject(myFixture.getProject());

    // Make sure we created the project-level files
    assertFilesExist(baseDir,
                     "build.gradle",
                     ".gitignore",
                     "settings.gradle",
                     "gradle.properties",
                     "local.properties");

    // Make sure we created the module files
    File moduleBase = new File(baseDir, myWizardState.getString(FormFactorUtils.ATTR_MODULE_NAME));
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

    String gradleContents = TemplateUtils.readTextFromDisk(gradleFile);
    assertNotNull(gradleContents);
    assertTrue(gradleContents.contains("apply plugin: 'com.android.library'"));

    File manifestFile = new File(moduleDir, FileUtil.join("src", "main", SdkConstants.ANDROID_MANIFEST_XML));
    String manifestContents = TemplateUtils.readTextFromDisk(manifestFile);
    assertNotNull(manifestContents);
    assertFalse(manifestContents.contains("android:theme"));

    assertFilesExist(moduleDir,
                     // Libraries no longer have launcher icons in them
                     "src/main/java/com/test/package");
  }

  public void testCreateProjectNoActivityNoIconsApplication() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);

    setUpStandardProjectCreation();
    File moduleDir = runCommonCreateProjectTest();
    File gradleFile = new File(moduleDir, "build.gradle");

    String gradleContents = TemplateUtils.readTextFromDisk(gradleFile);
    assertNotNull(gradleContents);
    assertTrue(gradleContents.contains("apply plugin: 'com.android.application'"));

    File manifestFile = new File(moduleDir, FileUtil.join("src", "main", SdkConstants.ANDROID_MANIFEST_XML));
    String manifestContents = TemplateUtils.readTextFromDisk(manifestFile);
    assertNotNull(manifestContents);
    assertTrue(manifestContents.contains("android:theme"));

    assertFilesExist(moduleDir,
                     "src/main/res/values/styles.xml",
                     "src/main/java/com/test/package",
                     "src/main/res/mipmap-hdpi/ic_launcher.png",
                     "src/main/res/mipmap-mdpi/ic_launcher.png",
                     "src/main/res/mipmap-xhdpi/ic_launcher.png",
                     "src/main/res/mipmap-xxhdpi/ic_launcher.png");
  }

  public void testCreateProjectWithActivityWithIcons() throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, true);
    myWizardState.put(ATTR_CREATE_ICONS, true);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);

    AssetStudioAssetGenerator launcherIconStateMock = mock(AssetStudioAssetGenerator.class);
    NewProjectWizardState spyState = spy(myWizardState);
    myWizard.myWizardState = spyState;
    myWizard.myAssetGenerator = launcherIconStateMock;

    TemplateWizardState activityStateMock = spy(myWizardState.getActivityTemplateState());
    File templateRootMock = mock(File.class);
    Template activityTemplateMock = mock(Template.class);
    when(templateRootMock.getName()).thenReturn("MockTemplate");
    when(activityTemplateMock.getRootPath()).thenReturn(templateRootMock);
    when(activityStateMock.getTemplate()).thenReturn(activityTemplateMock);
    when(spyState.getActivityTemplateState()).thenReturn(activityStateMock);

    setUpStandardProjectCreation();
    File moduleRoot = runCommonCreateProjectTest();

    verify(launcherIconStateMock).outputImagesIntoDefaultVariant(eq(moduleRoot));
    ArgumentCaptor<RenderingContext> context = ArgumentCaptor.forClass(RenderingContext.class);
    verify(activityTemplateMock).render(context.capture());
    assertEquals(myFixture.getProject(), context.getValue().getProject());
    assertEquals(moduleRoot, context.getValue().getOutputRoot());
    assertEquals(moduleRoot, context.getValue().getModuleRoot());
    assertEquals(moduleRoot, context.getValue().getOutputRoot());
    assertEquals(moduleRoot, context.getValue().getOutputRoot());
    assertIsSubMap(context.getValue().getParamMap(), myWizardState.myActivityTemplateState.myParameters);
  }

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

  // TODO: The following tests check the support of strange path names, even though our project
  // wizard flow does not allow us to create them. It seems worth it, instead, to test that our
  // wizard logic prevents inserting these weird characters rather than asserting we can handle
  // them. Otherwise, we're wasting time testing (and maintaining) impossible test cases.

  public void testCreateProjectWithSingleQuoteInPathName() throws Exception {
    createProjectWithWeirdTextInPathName("'");
  }

  // This test doesn't work (the " breaks xml parsing) but our project creation flow doesn't allow users to enter quotes anyway
  // It's left here to document that it was once tested.
  @SuppressWarnings("unused")
  public void DISABLED_testCreateProjectWithDoubleQuoteInPathName() throws Exception {
    // " is reserved on a Windows system
    if (System.getProperty("os.name").startsWith("Windows")) {
      return;
    }

    createProjectWithWeirdTextInPathName("\"");
  }

  public void testCreateProjectWithUnicodeInPathName() throws Exception {
    // Unicode is reserved on a Windows system
    if (System.getProperty("os.name").startsWith("Windows")) {
      return;
    }

    createProjectWithWeirdTextInPathName("\uD83D\uDCA9");
  }

  // This test doesn't work (the < breaks xml parsing) but our project creation flow doesn't allow users to enter this character anyway.
  // It's left here to document that it was once tested.
  @SuppressWarnings("unused")
  public void DISABLED_testCreateProjectWithLessThanInPathName() throws Exception {
    // < is reserved on a Windows system
    if (System.getProperty("os.name").startsWith("Windows")) {
      return;
    }

    createProjectWithWeirdTextInPathName("<");
  }

  public void testCreateProjectWithGreaterThanInPathName() throws Exception {
    // > is reserved on a Windows system
    if (System.getProperty("os.name").startsWith("Windows")) {
      return;
    }

    createProjectWithWeirdTextInPathName(">");
  }

  private void createProjectWithWeirdTextInPathName(String problemText) throws Exception {
    myWizardState.put(ATTR_CREATE_ACTIVITY, false);
    myWizardState.put(ATTR_CREATE_ICONS, false);
    myWizardState.put(ATTR_IS_LIBRARY_MODULE, false);
    String projectName = "ThisIsAProjectName";
    myWizardState.put(ATTR_APP_TITLE, projectName);
    myWizardState.put(ATTR_PACKAGE_NAME, "com.test.package");
    myWizardState.put(FormFactorUtils.ATTR_MODULE_NAME, MODULE_NAME);

    File baseDir = new File(getProject().getBasePath(), "test" + problemText + "orama");
    myWizardState.put(ATTR_PROJECT_LOCATION, baseDir.getPath());
    runCommonCreateProjectTest();
  }

  private void assertIsSubMap(Map<?,?> map, Map<?,?> subMap) {
    for (Object key : subMap.keySet()) {
      assertEquals("Value is different for the key: " + key, subMap.get(key), map.get(key));
    }
  }
}
