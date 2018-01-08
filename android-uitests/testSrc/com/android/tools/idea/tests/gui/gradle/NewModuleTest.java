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

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import com.intellij.lang.annotation.HighlightSeverity;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static com.android.tools.idea.testing.FileSubject.file;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests, that newly generated modules work, even with older gradle plugin versions.
 */
@RunWith(GuiTestRunner.class)
public class NewModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testNewModuleOldGradle() throws Exception {
    String gradleFileContents = guiTest.importSimpleLocalApplication()
      // the oldest combination we support:
      .updateAndroidGradlePluginVersion("1.0.0")
      .updateGradleWrapperVersion("2.2.1")
      .getEditor()
      .open("app/build.gradle")
      // delete lines using DSL features added after Android Gradle 1.0.0
      .moveBetween("use", "Library")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .moveBetween("test", "Compile")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFail(Wait.seconds(30))
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("somelibrary")
      .clickFinish()
      .waitForGradleProjectSyncToFail()
      .getEditor()
      .open("somelibrary/build.gradle")
      .getCurrentFileContents();
    assertThat(gradleFileContents).doesNotContain("testCompile");
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "somelibrary/src/main")).isDirectory();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "somelibrary/src/test")).doesNotExist();
  }

  @Test
  public void createNewModuleFromJar() throws Exception {
    String jarFile = GuiTests.getTestDataDir() + "/LocalJarsAsModules/localJarAsModule/local.jar";

    guiTest.importSimpleLocalApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
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

  /**
   * Verifies addition of new application module to application.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: fd583b0a-bedd-4ec8-9207-70e4994ed761
   * <pre>
   *   Test Steps
   *   1. File -> new module
   *   2. Select Phone & Tablet module
   *   3. Choose no activity
   *   3. Wait for build to complete
   *   Verification
   *   a new folder matching the module name should have been created.
   * </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewAppModuleWithDefaults() throws Exception {
    guiTest.importSimpleLocalApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Phone & Tablet Module")
      .clickNextToStep("Phone & Tablet Module")
      .setModuleName("application-module")
      .clickNextToStep("Add an Activity to Mobile")
      .chooseActivity("Add No Activity")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "application-module")).isDirectory();
  }

  /**
   * Verifies addition of new library module to application.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: c35b2089-a5e5-408a-a448-5d94f71fda94
   * <pre>
   *   Test Steps
   *   Create a new project
   *   1. File > New Module
   *   2. Choose Android Library
   *   3. Click Finish
   *   Verification
   *   a new folder matching the module name should have been created
   * </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewLibraryModuleWithDefaults() throws Exception {
    guiTest.importSimpleLocalApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("library-module")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "library-module")).isDirectory();
  }

  @Test
  public void createNewJavaLibraryWithDefaults() throws Exception {
    guiTest.importSimpleLocalApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Java Library")
      .clickNextToStep("Library name:")
      .getConfigureJavaLibaryStepFixture()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .setCreateGitIgnore(true)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "mylib/src/main/java/my/test/MyClass.java")).isFile();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "mylib/.gitignore")).isFile();
  }

  @Test
  public void createNewJavaLibraryWithNoGitIgnore() throws Exception {
    guiTest.importSimpleLocalApplication()
      .openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Java Library")
      .clickNextToStep("Library name:")
      .getConfigureJavaLibaryStepFixture()
      .enterLibraryName("mylib")
      .enterPackageName("my.test")
      .enterClassName("MyJavaClass")
      .setCreateGitIgnore(false)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "mylib/src/main/java/my/test/MyJavaClass.java")).isFile();
    assertAbout(file()).that(new File(guiTest.getProjectPath(), "mylib/.gitignore")).doesNotExist();
  }
}
