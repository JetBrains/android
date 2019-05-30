/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@RunWith(GuiTestRemoteRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testNewModuleOldGradle() throws Exception {
    String gradleFileContents = guiTest.importSimpleApplication()
      // the oldest combination we support:
      .updateAndroidGradlePluginVersion("1.0.0")
      .updateGradleWrapperVersion("2.2.1")
      .getEditor()
      .open("app/build.gradle")
      // delete lines using DSL features added after Android Gradle 1.0.0
      .moveBetween("use", "Library")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .moveBetween("test", "Implementation")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFail(Wait.seconds(30))
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("somelibrary")
      .clickFinish()
      .waitForGradleProjectSyncToFail()
      .getEditor()
      .open("somelibrary/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents).doesNotContain("testCompile");
    assertThat(gradleFileContents).doesNotContain("testImplementation");
    assertAbout(file()).that(guiTest.getProjectPath("somelibrary/src/main")).isDirectory();
    assertAbout(file()).that(guiTest.getProjectPath("somelibrary/src/test")).doesNotExist();
  }

  @Test
  public void createNewModuleFromJar() throws Exception {
    String jarFile = GuiTests.getTestDataDir() + "/LocalJarsAsModules/localJarAsModule/local.jar";

    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Import .JAR/.AAR Package")
      .clickNextToStep("Import Module from Library")
      .setFileName(jarFile)
      .setSubprojectName("localJarLib")
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/build.gradle")
      .moveBetween("dependencies {", "")
      .enterText("\ncompile project(':localJarLib')")
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .moveBetween("setContentView(R.layout.activity_my);", "")
      .enterText("\nnew com.example.android.multiproject.person.Person(\"Me\");\n")
      .waitForCodeAnalysisHighlightCount(HighlightSeverity.ERROR, 0);
  }

  @Test
  public void createNewJavaLibraryWithDefaults() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Java Library")
      .clickNextToStep("Library name:")
      .getConfigureJavaLibaryStepFixture()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(guiTest.getProjectPath("mylib/src/main/java/my/test/MyClass.java")).isFile();
  }

  @Test
  public void createNewAndroidLibraryWithDefaults() throws Exception {
    String gradleFileContents = guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("somelibrary")
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("somelibrary/build.gradle")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains("apply plugin: 'com.android.library'");
    assertThat(gradleFileContents).contains("consumerProguardFiles");
  }

  @Test
  public void createNewJavaLibraryWithNoGitIgnore() throws Exception {
    guiTest.importSimpleApplication()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Java Library")
      .clickNextToStep("Library name:")
      .getConfigureJavaLibaryStepFixture()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .enterClassName("MyJavaClass")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(guiTest.getProjectPath("mylib/src/main/java/my/test/MyJavaClass.java")).isFile();
  }

  @Test
  public void addNewModuleToAndroidxProject() {
    WizardUtils.createNewProject(guiTest); // Default projects are created with androidx dependencies
    guiTest.ideFrame()
      .openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNext() // Default Phone & Tablet Module
      .setModuleName("otherModule")
      .clickNext()
      .clickNext() // Default "Empty Activity"
      .clickFinish();

    String gradleProperties = guiTest.ideFrame().getEditor()
      .open("gradle.properties")
      .getCurrentFileContents();
    assertThat(gradleProperties).contains("android.useAndroidX=true");

    String appBuildGradle = guiTest.ideFrame().getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();
    assertThat(appBuildGradle).contains("androidx.appcompat:appcompat");

    String otherModuleBuildGradle = guiTest.ideFrame().getEditor()
      .open("othermodule/build.gradle")
      .getCurrentFileContents();
    assertThat(otherModuleBuildGradle).contains("androidx.appcompat:appcompat");
  }
}
