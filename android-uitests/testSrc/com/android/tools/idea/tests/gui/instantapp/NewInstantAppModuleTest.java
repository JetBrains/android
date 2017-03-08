/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidModuleStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewModuleWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;

import static com.android.tools.idea.gradle.util.BuildMode.SOURCE_GEN;
import static com.android.tools.idea.instantapp.InstantApps.setAiaSdkLocation;
import static com.android.tools.idea.npw.deprecated.ConfigureAndroidProjectStep.SAVED_COMPANY_DOMAIN;
import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.util.io.FileUtil.join;
import static java.lang.System.getenv;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Test that newly created Instant App modules do not have errors in them
 */
@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class NewInstantAppModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Nullable private String myOldSavedCompanyDomain;

  @Before
  public void before() {
    PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
    myOldSavedCompanyDomain = propertiesComponent.getValue(SAVED_COMPANY_DOMAIN);
    propertiesComponent.setValue(SAVED_COMPANY_DOMAIN, "aia.example.com");
    setAiaSdkLocation("TestValue");
  }

  @After
  public void after() {
    PropertiesComponent.getInstance().setValue(SAVED_COMPANY_DOMAIN, myOldSavedCompanyDomain);
    setAiaSdkLocation(getenv("WH_SDK"));
  }

  // TODO: add tests for warnings in code - requires way to separate warnings from SimpleApplication out from warnings in new module

  @Test
  public void testCanBuildDefaultNewInstantAppApplicationModule() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(true, false, null);
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildDefaultNewInstantAppLibraryModule() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(false, false, null);
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testCanBuildDefaultNewInstantAppBaseLibraryModule() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(false, true, null);
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testDefaultNewInstantAppApplicationModuleHasNoResources() throws IOException {
    guiTest.importSimpleApplication();

    addNewInstantAppModule(true, false, "instantapp");
    addNewInstantAppModule(false, false, "atom");
    addNewInstantAppModule(false, true, "baseatom");

    File projectRoot = guiTest.ideFrame().getProjectPath();
    assertAbout(file()).that(new File(projectRoot, join("instantapp", "src", "main", "res"))).doesNotExist();
    assertAbout(file()).that(new File(projectRoot, join("atom", "src", "main", "res"))).doesNotExist();
    assertAbout(file()).that(new File(projectRoot, join("baseatom", "src", "main", "res"))).isDirectory();
  }

  @Test
  public void testCanBuildFullInstantApp() throws IOException {
    guiTest.importSimpleApplication();

    addNewInstantAppModule(true, false, "instantapp");
    addNewInstantAppModule(false, false, "atom");
    addNewInstantAppModule(false, true, "baseatom");

    guiTest.ideFrame()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("instantapp")
      .selectDependenciesTab()
      .addModuleDependency(":atom")
      .selectConfigurable("atom")
      .selectDependenciesTab()
      .addModuleDependency(":baseatom")
      .clickOk();

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .waitForBuildToFinish(SOURCE_GEN);

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }

  @Test
  public void testApplicationPackageGeneratedCorrectly() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(true, false, "instantapp");
    assertNull(getManifest("instantapp"));
  }

  @Test
  public void testAtomPackageGeneratedCorrectly() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(false, false, "atom");
    assertCorrectPackageAndSplit("atom", "atom");
  }

  @Test
  public void testBaseAtomPackageGeneratedCorrectly() throws IOException {
    guiTest.importSimpleApplication();
    addNewInstantAppModule(false, true, "baseatom");
    assertCorrectPackageAndSplit("baseatom", null);
  }

  private void addNewInstantAppModule(boolean isApplication, boolean isBaseAtom, @Nullable String moduleName) {

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    NewModuleWizardFixture newModuleWizardFixture = ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...");

    ConfigureAndroidModuleStepFixture configureAndroidModuleStep = newModuleWizardFixture
      .chooseModuleType(isApplication ? "Phone & Tablet Module" : "Android Library")
      .clickNext() // Selected App
      .getConfigureAndroidModuleStep()
      .selectMinimumSdkApi("16")
      .selectInstantAppSupport();

    if (moduleName != null) {
      configureAndroidModuleStep.enterModuleName(moduleName);
    }

    if (isBaseAtom) {
      configureAndroidModuleStep.selectBaseAtom();
    }

    if (isApplication) {
      newModuleWizardFixture
        .clickNext() // Default options
        .chooseActivity("Add No Activity"); // No Activity (see http://b/34216139)
    }

    newModuleWizardFixture
      .clickFinish();

    ideFrame
      .waitForGradleProjectSyncToFinish()
      .waitForBuildToFinish(SOURCE_GEN);
  }

  @Nullable
  private Manifest getManifest(@NotNull String moduleName) {
    Module module = guiTest.ideFrame().getModule(moduleName);
    AndroidFacet facet = AndroidFacet.getInstance(module);
    assertNotNull(facet);
    return facet.getManifest();
  }

  private void assertCorrectPackageAndSplit(@NotNull String moduleName, @Nullable String splitName) {
    Manifest manifest = getManifest(moduleName);
    assertNotNull(manifest);

    ApplicationManager.getApplication().runReadAction(() -> {
      GenericAttributeValue<String> packageAttribute = manifest.getPackage();
      assertNotNull(packageAttribute);
      assertThat(packageAttribute.isValid()).isTrue();
      assertThat(packageAttribute.getStringValue()).isEqualTo("com.example.aia");

      if (splitName != null) {
        boolean splitNameFound = false;
        for (PsiElement child : manifest.getXmlElement().getChildren()) {
          splitNameFound = splitNameFound || child.getText().equals("split=\"" + splitName + "\"");
        }
        assertThat(splitNameFound).isTrue();
      }
    });
  }
}
