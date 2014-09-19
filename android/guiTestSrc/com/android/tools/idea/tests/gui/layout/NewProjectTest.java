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
package com.android.tools.idea.tests.gui.layout;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.ApiVersion;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.annotation.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.FileFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.ConfigureAndroidProjectStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.LanguageLevelModuleExtension;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;

import static com.android.tools.idea.wizard.FormFactorUtils.FormFactor.MOBILE;
import static junit.framework.Assert.assertNotNull;
import static org.fest.assertions.Assertions.assertThat;

public class NewProjectTest extends GuiTestCase {
  @Test
  @IdeGuiTest
  public void testCreateNewMobileProject() {
    IdeFrameFixture projectFrame = newProject("Test Application").create();
    FileFixture layoutFile = projectFrame.findExistingFileByRelativePath("app/src/main/res/layout/activity_main.xml");
    layoutFile.requireOpenAndSelected();

    // Verify state of project
    projectFrame.requireModuleCount(2);
    IdeaAndroidProject appAndroidProject = projectFrame.getAndroidProjectForModule("app");
    assertThat(appAndroidProject.getVariantNames()).as("variants").containsOnly("debug", "release");
    assertThat(appAndroidProject.getSelectedVariant().getName()).as("selected variant").isEqualTo("debug");

    AndroidProject model = appAndroidProject.getDelegate();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo("19");

    // Creating a project with minSdkVersion 19 should leave the Java language level as Java 6
    // For L and higher we use Java 7 language level; that is tested separately in testLanguageLevelForApi21
    assertThat(appAndroidProject.getJavaLanguageLevel()).as("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_6);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(projectFrame.getProject());
    assertThat(projectExt.getLanguageLevel()).as("Project Java language level").isSameAs(LanguageLevel.JDK_1_6);
    for (Module module : ModuleManager.getInstance(projectFrame.getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtension.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).as("Gradle Java language level in module " + module.getName()).isNull();
    }
  }

  @Test
  @IdeGuiTest
  public void testLanguageLevelForApi21() {
    // Verifies that creating a project with L will set the language level correctly
    // both in the generated Gradle model as well as in the synced project and modules

    // "20+" here should change to 21 as soon as L goes out of preview state
    IdeFrameFixture projectFrame = newProject("Test Application").withMinSdk("20+")
      // just to speed up the test: type as little as possible
      .withActivity("A").withCompanyDomain("C").withName("P").withPackageName("a.b").create();

    IdeaAndroidProject appAndroidProject = projectFrame.getAndroidProjectForModule("app");
    AndroidProject model = appAndroidProject.getDelegate();
    ApiVersion minSdkVersion = model.getDefaultConfig().getProductFlavor().getMinSdkVersion();
    assertNotNull("minSdkVersion", minSdkVersion);

    // If this test fails, verify that
    //   (1) you have the L preview installed in the SDK on the test machine
    //   (2) the associated JDK is JDK 7 or higher
    assertThat(minSdkVersion.getApiString()).as("minSdkVersion API").isEqualTo("L");
    assertThat(appAndroidProject.getJavaLanguageLevel()).as("Gradle Java language level").isSameAs(LanguageLevel.JDK_1_7);
    LanguageLevelProjectExtension projectExt = LanguageLevelProjectExtension.getInstance(projectFrame.getProject());
    assertThat(projectExt.getLanguageLevel()).as("Project Java language level").isSameAs(LanguageLevel.JDK_1_7);
    for (Module module : ModuleManager.getInstance(projectFrame.getProject()).getModules()) {
      LanguageLevelModuleExtension moduleExt = LanguageLevelModuleExtension.getInstance(module);
      assertThat(moduleExt.getLanguageLevel()).as("Gradle Java language level in module " + module.getName()).isNull();
    }
  }

  @NotNull
  private NewProjectDescriptor newProject(@NotNull String name) {
    return new NewProjectDescriptor(name);
  }

  /**
   * Describes a new test project to be created.
   */
  private class NewProjectDescriptor {
    private String myActivity = "MainActivity";
    private String myPkg = "com.android.test.app";
    private String myMinSdk = "19";
    private String myName = "TestProject";
    private String myDomain = "com.android";

    private NewProjectDescriptor(@NotNull String name) {
      withName(name);
    }

    /**
     * Set a custom package to use in the new project
     */
    NewProjectDescriptor withPackageName(@NotNull String pkg) {
      myPkg = pkg;
      return this;
    }

    /**
     * Set a new project name to use for the new project
     */
    NewProjectDescriptor withName(@NotNull String name) {
      myName = name;
      return this;
    }

    /**
     * Set a custom activity name to use in the new project
     */
    NewProjectDescriptor withActivity(@NotNull String activity) {
      myActivity = activity;
      return this;
    }

    /**
     * Set a custom minimum SDK version to use in the new project
     */
    NewProjectDescriptor withMinSdk(@NotNull String minSdk) {
      myMinSdk = minSdk;
      return this;
    }

    /**
     * Set a custom company domain to enter in the new project wizard
     */
    NewProjectDescriptor withCompanyDomain(@NotNull String domain) {
      myDomain = domain;
      return this;
    }

    /**
     * Creates a project fixture for this description
     */
    @NotNull
    IdeFrameFixture create() {
      findWelcomeFrame().clickNewProjectButton();

      NewProjectWizardFixture newProjectWizard = findNewProjectWizard();

      ConfigureAndroidProjectStepFixture configureAndroidProjectStep = newProjectWizard.getConfigureAndroidProjectStep();
      configureAndroidProjectStep.enterApplicationName(myName).enterCompanyDomain(myDomain).enterPackageName(myPkg);
      File projectPath = configureAndroidProjectStep.getLocationInFileSystem();
      newProjectWizard.clickNext();

      newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, myMinSdk);
      newProjectWizard.clickNext();

      // Skip "Add Activity" step
      newProjectWizard.clickNext();

      newProjectWizard.getChooseOptionsForNewFileStep().enterActivityName(myActivity);
      newProjectWizard.clickFinish();

      IdeFrameFixture projectFrame = findIdeFrame(myName, projectPath);
      projectFrame.waitForGradleProjectSyncToFinish();

      return projectFrame;
    }
  }
}