/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.gradle;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.EditorAction.LINE_END;
import static com.android.tools.idea.wizard.template.Language.Java;
import static com.android.tools.idea.wizard.template.Language.Kotlin;
import static com.android.tools.idea.wizard.template.impl.activities.blankWearActivity.BlankWearActivityTemplateKt.getBlankWearActivityTemplate;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

import com.android.flags.junit.RestoreFlagRule;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BytecodeLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests that newly generated modules work, even with older gradle plugin versions.
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Rule public final RestoreFlagRule<Boolean> restoreNpwFlagRule = new RestoreFlagRule<>(StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR);

  @Before
  public void setup() {
    StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.override(true);
  }

  @Test
  public void createNewModuleFromJar() throws IOException {
    String jarFile = GuiTests.getTestDataDir() + "/LocalJarsAsModules/localJarAsModule/local.jar";

    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextToModuleFromJar()
      .setFileName(jarFile)
      .setSubprojectName("localJarLib")
      .wizard()
      .clickFinishAndWaitForSyncToFinish()
      .getEditor()
      .open("app/build.gradle")
      .moveBetween("dependencies {", "")
      .invokeAction(LINE_END)
      .enterText("\ncompile project(':localJarLib')")
      .getIdeFrame()
      .requestProjectSyncAndWaitForSyncToFinish()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .moveBetween("setContentView(R.layout.activity_my);", "")
      .invokeAction(LINE_END)
      .enterText("\nnew com.example.android.multiproject.person.Person(\"Me\");\n")
      .waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  @Test
  public void createNewJavaLibraryWithDefaults() throws IOException {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextToPureLibrary()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .setSourceLanguage(Java)
      .wizard()
      .clickFinishAndWaitForSyncToFinish();
    assertAbout(file()).that(guiTest.getProjectPath("mylib/src/main/java/my/test/MyClass.java")).isFile();
    assertAbout(file()).that(guiTest.getProjectPath("mylib/.gitignore")).isFile();
  }

  @Test
  public void createNewKotlinLibraryWithDefaults() throws IOException {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextToPureLibrary()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .setSourceLanguage(Kotlin)
      .wizard()
      .clickFinishAndWaitForSyncToFinish();
    String gradleFileContents = guiTest.getProjectFileText("mylib/build.gradle");
    assertThat(gradleFileContents).contains("id 'kotlin'");
    assertAbout(file()).that(guiTest.getProjectPath("mylib/src/main/java/my/test/MyClass.kt")).isFile();
  }

  @Test
  public void createNewAndroidLibraryWithDefaults() throws IOException {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextToAndroidLibrary()
      .selectBytecodeLevel(BytecodeLevel.L8.toString())
      .enterModuleName("somelibrary")
      .wizard()
      .clickFinishAndWaitForSyncToFinish();

    String gradleFileContents = guiTest.getProjectFileText("somelibrary/build.gradle");
    assertThat(gradleFileContents).contains("id 'com.android.library'");
    assertThat(gradleFileContents).contains("consumerProguardFiles");
    assertAbout(file()).that(guiTest.getProjectPath("somelibrary/.gitignore")).isFile();
  }

  @Test
  public void addNewModuleToAndroidxProject() {
    WizardUtils.createNewProject(guiTest); // Default projects are created with androidx dependencies
    guiTest.ideFrame()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextPhoneAndTabletModule()
      .enterModuleName("otherModule")
      .wizard()
      .clickNext()
      .clickNext() // Default "Empty Activity"
      .clickFinishAndWaitForSyncToFinish();

    assertThat(guiTest.getProjectFileText("gradle.properties"))
      .contains("android.useAndroidX=true");

    assertThat(guiTest.getProjectFileText("app/build.gradle"))
      .contains("androidx.appcompat:appcompat");

    assertThat(guiTest.getProjectFileText("otherModule/build.gradle"))
      .contains("androidx.appcompat:appcompat");
  }

  @Test
  public void addNewBasicActivityModuleToNewProject() {
    WizardUtils.createNewProject(guiTest); // Default projects are created with androidx dependencies
    guiTest.ideFrame()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextPhoneAndTabletModule()
      .setSourceLanguage(Kotlin)
      .enterModuleName("otherModule")
      .wizard()
      .clickNext()
      .chooseActivity("Basic Activity")
      .clickNext() // Default "Empty Activity"
      .clickFinishAndWaitForSyncToFinish();

    String otherModuleBuildGradleText = guiTest.getProjectFileText("otherModule/build.gradle");
    assertThat(otherModuleBuildGradleText).contains("implementation 'androidx.navigation:navigation-fragment-ktx:");
    assertThat(otherModuleBuildGradleText).contains("JavaVersion.VERSION_1_8");

    String navGraphText = guiTest.getProjectFileText("otherModule/src/main/res/navigation/nav_graph.xml");
    assertThat(navGraphText).contains("navigation xmlns:android=");
    assertThat(navGraphText).contains("app:startDestination=\"@id/FirstFragment\"");
  }

  @Test
  public void addNewWearModule() {
    WizardUtils.createNewProject(guiTest); // Use androidx
    final String moduleName = "wearModule";
      guiTest.ideFrame()
        .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
        .clickNextWearModule()
        .enterModuleName(moduleName)
        .wizard()
        .clickNext()
        .chooseActivity(getBlankWearActivityTemplate().getName())
        .clickNext()
        .clickFinishAndWaitForSyncToFinish();

    String gradleFileContents = guiTest.getProjectFileText(moduleName + "/build.gradle");
    assertThat(gradleFileContents).contains("id 'com.android.application'");
    assertThat(gradleFileContents).doesNotContain("consumerProguardFiles");
    assertThat(gradleFileContents).contains("androidx.wear:wear");
    assertAbout(file()).that(guiTest.getProjectPath(moduleName + "/.gitignore")).isFile();
  }
}
