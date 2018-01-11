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
import com.android.tools.idea.gradle.parser.Dependency;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.gradle.structure.editors.ModuleDependenciesTableItem;
import com.android.tools.idea.gradle.structure.editors.ModuleDependenciesTableModel;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewModuleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.DependencyTabFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.ui.table.JBTable;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
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
  private static final String LIB_NAME_1 = "modulea";
  private static final String LIB_NAME_2 = "moduleb";
  private static final String JAVA_MODULE_1 = "lib"; // default name
  private static final String JAVA_MODULE_2 = "lib2"; // default name
  private static final String ANDROID_LIBRARY = "Android Library";
  private static final String JAVA_LIBRARY = "Java Library";
  private static final String CLASS_NAME_1 = "ModuleA";
  private static final String CLASS_NAME_2 = "ModuleB";

  @Ignore("b/70694098")
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
  @RunIn(TestGroup.QA)
  @Test
  public void transitiveDependenciesResolve() throws Exception {
    IdeFrameFixture ideFrame = createNewProject(APP_NAME, MIN_SDK);

    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType(ANDROID_LIBRARY)
      .clickNextToStep(ANDROID_LIBRARY)
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
      .clickPath(APP_NAME, "library-module", "src", "main", "java", "android.com.library_module");

    invokeNewFileDialog().setName("LibraryClass").clickOk();
    editor.open("/library-module/src/main/java/android/com/library_module/LibraryClass.java")
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
  private IdeFrameFixture createNewProject(@NotNull String appName, @NotNull String minSdk) throws Exception {
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

    return guiTest.ideFrame().waitForGradleProjectSyncToFinish(Wait.seconds(30));
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

  /**
   * Verifies that transitive dependencies with Android Libraries are resolved in a gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6179f958-82e1-4605-ac3a-eaaec2e01dbe
   * <p>
   *   <pre>
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Android Library, create new Android library module.
   *   3. Right click on the app module > Module settings under dependencies, add module dependency
   *      (default: implementation) to library create in step 2. Create a Java class in this
   *      new Android library module.
   *   4. Go to File Menu > New Module > Android Library, create another new Android library module.
   *   5. Right click on the module (created in step 2) > Module settings under dependencies, add
   *      module dependency (select API scope type) to library create in step 4.
   *      Create a Java class in module created in step 4.
   *   6. Try accessing Library2 classes in Library1 (verify 1).
   *   7. Try accessing both Library1 and Library2 classes in app module of your project(verify 2).
   *   Verification
   *   1. Library 2 classes should be accessible to Library 1 and should get resolved successfully.
   *   2. Library 2 and Library 1 classes should be accessible to app module and should get
   *      resolved successfully.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void transitiveDependenciesWithMultiAndroidLibraries() throws Exception {
    IdeFrameFixture ideFrame = createNewProject(APP_NAME, MIN_SDK);

    createAndroidLibrary(ideFrame, LIB_NAME_1);
    addModuleDependencyUnderAnother(ideFrame, LIB_NAME_1, "app", "IMPLEMENTATION");
    createJavaClassInModule(ideFrame, LIB_NAME_1, CLASS_NAME_1);

    createAndroidLibrary(ideFrame, LIB_NAME_2);
    addModuleDependencyUnderAnother(ideFrame, LIB_NAME_2, LIB_NAME_1, "API");
    createJavaClassInModule(ideFrame, LIB_NAME_2, CLASS_NAME_2);

    accessLibraryClassAndVerify(ideFrame, LIB_NAME_1, LIB_NAME_2);
  }

  /**
   * Verifies that transitive dependencies with Java Libraries are resolved in a gradle file.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5f5581fe-b02f-4775-aa76-f592e011080d
   * <p>
   *   <pre>
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Java Library, create new Java library module.
   *   3. Right click on the app module > Module settings under dependencies, add module dependency
   *      (default: implementation) to library create in step 2. Create a Java class in this
   *      new Android library module.
   *   4. Go to File Menu > New Module > Java Library, create another new Android library module.
   *   5. Right click on the module (created in step 2) > Module settings under dependencies, add
   *      module dependency (default: implementation) to library create in step 4.
   *      Create a Java class in module created in step 4.
   *   6. Try accessing Library2 classes in Library1 (verify 1).
   *   7. Try accessing both Library1 and Library2 classes in app module of your project(verify 2).
   *   Verification
   *   1. Library 2 classes should be accessible to Library 1 and should get resolved successfully.
   *   2. Library 2 and Library 1 classes should be accessible to app module and should get
   *      resolved successfully.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70633460
  @Test
  public void multiJavaLibraries() throws Exception {
    IdeFrameFixture ideFrame = createNewProject(APP_NAME, MIN_SDK);

    createJavaModule(ideFrame); // default name: lib
    addModuleDependencyUnderAnother(ideFrame, JAVA_MODULE_1, "app", "IMPLEMENTATION");
    createJavaClassInModule(ideFrame, JAVA_MODULE_1, CLASS_NAME_1);

    createJavaModule(ideFrame); // default name: lib2
    addModuleDependencyUnderAnother(ideFrame, JAVA_MODULE_2, JAVA_MODULE_1, "IMPLEMENTATION");
    createJavaClassInModule(ideFrame, JAVA_MODULE_2, CLASS_NAME_2);

    // Test will fail here because of bug: 69813406
    accessLibraryClassAndVerify(ideFrame, JAVA_MODULE_1, JAVA_MODULE_2);
  }

  private void createJavaModule(@NotNull IdeFrameFixture ideFrame) {
    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType(JAVA_LIBRARY)
      .clickNextToStep(JAVA_LIBRARY)
      .clickFinish() // Use default Java Module name.
      .waitForGradleProjectSyncToFinish();
  }

  private void accessLibraryClassAndVerify(@NotNull IdeFrameFixture ideFrame,
                                           @NotNull String module1,
                                           @NotNull String modeule2) {
    // Accessing Library2 classes in Library1, and verify.
    ideFrame.getEditor().open(module1 + "/src/main/java/android/com/" + module1 + "/" + CLASS_NAME_1 + ".java")
      .moveBetween("package android.com." + module1 + ";", "")
      .enterText("\n\nimport android.com." + modeule2 + "." + CLASS_NAME_2 + ";")
      .moveBetween("public class " + CLASS_NAME_1 + " {", "")
      .enterText("\n" + CLASS_NAME_2 + " className2 = new " + CLASS_NAME_2 + "();");
    GradleInvocationResult result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());

    // Accessing both Library1 and Library2 classes in app module, and verify.
    ideFrame.getEditor().open("app/src/main/java/android/com/app/MainActivity.java")
      .moveBetween("import android.os.Bundle;", "")
      .enterText("\nimport android.com." + module1 + "." + CLASS_NAME_1 + ";" +
                 "\nimport android.com." + modeule2 + "." + CLASS_NAME_2 + ";")
      .moveBetween("setContentView(R.layout.activity_main);", "")
      .enterText("\n" + CLASS_NAME_1 + " classNameA = new " + CLASS_NAME_1 + "();")
      .enterText("\n" + CLASS_NAME_2 + " classNameB = new " + CLASS_NAME_2 + "();");
    result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
  }

  private void createAndroidLibrary(@NotNull IdeFrameFixture ideFrame,
                                    @NotNull String moduleName) {
    ideFrame.openFromMenu(NewModuleDialogFixture::find, "File", "New", "New Module...")
      .chooseModuleType(ANDROID_LIBRARY)
      .clickNextToStep(ANDROID_LIBRARY)
      .setModuleName(moduleName)
      .clickFinish()
      .waitForGradleProjectSyncToFinish();
  }

  private void addModuleDependencyUnderAnother(@NotNull IdeFrameFixture ideFrame,
                                               @NotNull String moduleName,
                                               @NotNull String anotherModule,
                                               @NotNull String scope) {
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, APP_NAME, anotherModule);

    ideFrame.invokeMenuPath("Open Module Settings");

    String module = ":" + moduleName;
    DependencyTabFixture dependencyTabFixture = ProjectStructureDialogFixture.find(ideFrame)
      .selectDependenciesTab()
      .addModuleDependency(module);

    if (scope.equals(Dependency.Scope.API.getDisplayName())) {
      JBTable jbTable = GuiTests.waitUntilFound(guiTest.robot(),
                                                dependencyTabFixture.target(),
                                                Matchers.byType(JBTable.class).andIsShowing());

      ModuleDependenciesTableModel tableModel = (ModuleDependenciesTableModel) jbTable.getModel();

      for (ModuleDependenciesTableItem item : tableModel.getItems()) {
        Dependency dependencyEntry = (Dependency) item.getEntry();
        if (module.equals(dependencyEntry.getValueAsString())) {
          item.setScope(Dependency.Scope.API);
          break;
        }
      }
    }

    dependencyTabFixture.clickOk();
  }

  private void createJavaClassInModule(@NotNull IdeFrameFixture ideFrame,
                                       @NotNull String moduleName,
                                       @NotNull String className) {
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(APP_NAME, moduleName, "src", "main", "java", "android.com." + moduleName);

    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    CreateFileFromTemplateDialogFixture dialog = CreateFileFromTemplateDialogFixture.find(guiTest.robot());
    dialog.setName(className);
    dialog.clickOk();
  }
}