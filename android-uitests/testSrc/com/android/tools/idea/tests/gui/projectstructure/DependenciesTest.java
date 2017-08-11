/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.projectstructure;

import com.android.repository.io.FileOpUtils;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Paths;

import static com.android.tools.idea.npw.FormFactor.MOBILE;
import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.junit.Assert.assertTrue;

@RunIn(TestGroup.PROJECT_SUPPORT)
@RunWith(GuiTestRunner.class)
public class DependenciesTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "App";
  private static final String MIN_SDK = "18";

  @RunIn(TestGroup.UNRELIABLE)  // b/64414268
  @Test
  public void createNewFlavors() throws Exception {
    String projPath = guiTest.importSimpleApplication()
      .getProjectPath()
      .getPath();

    File jarFile = Paths.get(projPath, "app", "libs", "local.jar").toFile();
    assertTrue(FileOpUtils.create().mkdirs(jarFile.getParentFile()));

    FileOpUtils.create().copyFile(new File(GuiTests.getTestDataDir() + "/LocalJarsAsModules/localJarAsModule/local.jar"), jarFile);

    String gradleFileContents = guiTest.ideFrame()
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectDependenciesTab()
      .addJarDependency(jarFile)
      .clickOk()
      .getEditor()
      .open("/app/build.gradle")
      .getCurrentFileContents();

    assertThat(gradleFileContents).contains("implementation files('libs/local.jar')");
  }

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: eb05dfdd-f751-47f1-820c-e9f71896dab4
   * <pre>
   *   Verifies transitive dependencies with Android Library and third party library are resolved in a gradle file.
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Android Library.
   *   3. Create new Android library module.
   *   4. Add "compile 'com.google.code.gson.gson:2.6.2" to library module's build.gradle file in dependencies section.
   *   5. Right click on the app module > Module settings under dependencies, add module dependency to library create in step 2.
   *   6. Add in the line "Gson gson = new Gson();" in class files of app module and library module.
   *   Verification
   *   The line "Gson gsn = new Gson();" should get resolved in both the app and library modules without any errors.
   * </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void transitiveDependenciesResolve() throws Exception {
    IdeFrameFixture ideFrame = createNewProject(APP_NAME, MIN_SDK);

    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Android Library")
      .clickNextToStep("Android Library")
      .setModuleName("library-module")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor()
      .open("/library-module/build.gradle")
      .select("dependencies \\{()")
      .enterText("\ncompile 'com.google.code.gson:gson:2.6.2'\n");

    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, APP_NAME, "app");

    ideFrame.invokeMenuPath("Open Module Settings");

    ProjectStructureDialogFixture.find(ideFrame)
      .selectDependenciesTab()
      .addModuleDependency(":library-module")
      .clickOk();

    editor.open("/app/src/main/java/android/com/app/MainActivity.java")
      .select("()public class MainActivity")
      .enterText("import com.google.gson.Gson;\n\n")
      .select("public class MainActivity extends AppCompatActivity \\{()")
      .enterText("\nGson gson = new Gson();\n");

    // Create a class in the library and check the build.
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(APP_NAME, "library-module", "src", "main", "java", "android.com.mylibrary");

    invokeNewFileDialog().setName("LibraryClass").clickOk();
    editor.open("/library-module/src/main/java/android/com/mylibrary/LibraryClass.java")
      .select("()public class LibraryClass")
      .enterText("import com.google.gson.Gson;\n\n")
      .select("public class LibraryClass \\{()")
      .enterText("\nGson gson = new Gson();\n");

    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
  }

  @NotNull
  private CreateFileFromTemplateDialogFixture invokeNewFileDialog() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return CreateFileFromTemplateDialogFixture.find(guiTest.robot());
  }

  @NotNull
  private IdeFrameFixture createNewProject(@NotNull String appName, @NotNull String minSdk) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame().createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
      .enterApplicationName(appName)
      .enterCompanyDomain("com.android");

    newProjectWizard.clickNext();
    newProjectWizard.getConfigureFormFactorStep().selectMinimumSdkApi(MOBILE, minSdk);
    newProjectWizard.clickNext();
    newProjectWizard.chooseActivity("Empty Activity");
    newProjectWizard.clickNext();
    newProjectWizard.clickFinish();

    return guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 92ad6e70-ad2a-432a-a447-630ccb9a0327
   * <pre>
   *   Verifies transitive dependencies with Android Library and third party library are resolved in a gradle file.
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Java Library.
   *   3. Create new Java library module.
   *   4. Add "compile 'com.google.code.gson.gson:2.6.2" to library module's build.gradle file in dependencies section.
   *   5. Right click on the app module > Module settings under dependencies, add module dependency to library create in step 2.
   *   6. Add in the line "Gson gson = new Gson();" in class files of app module and library module.
   *   Verification
   *   The line "Gson gsn = new Gson();" should get resolved in both the app and library modules without any errors.
   * </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void transitiveJavaDependenciesResolve() throws Exception {
    IdeFrameFixture ideFrame = createNewProject(APP_NAME, MIN_SDK);

    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType("Java Library")
      .clickNextToStep("Java Library")
      .clickFinish()
      .waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor()
      .open("/lib/build.gradle")
      .select("dependencies \\{()")
      .enterText("\ncompile 'com.google.code.gson:gson:2.6.2'\n");


    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, APP_NAME, "app");

    ideFrame.invokeMenuPath("Open Module Settings");

    ProjectStructureDialogFixture.find(ideFrame)
      .selectDependenciesTab()
      .addModuleDependency(":lib")
      .clickOk();

    editor.open("/app/src/main/java/android/com/app/MainActivity.java")
      .select("()public class MainActivity")
      .enterText("import com.google.gson.Gson;\n\n")
      .select("public class MainActivity extends AppCompatActivity \\{()")
      .enterText("\nGson gson = new Gson();\n");

    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
  }
}