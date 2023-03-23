/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static com.android.tools.idea.wizard.template.Language.Java;
import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.NewJavaClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.AddModuleDependencyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixtureKt;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.BuildConfigurationLanguage;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AndroidDepTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  /***
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: eb05dfdd-f751-47f1-820c-e9f71896dab4
   * <pre>
   *   Verifies transitive dependencies with Android Library and third party library are resolved in a gradle file.
   *   Test Steps
   *   1. Create a new Android Studio project.
   *   2. Go to File Menu > New Module > Android Library.
   *   3. Create new Android library module.
   *   4. Add "api 'com.google.code.gson.gson:2.6.2" to library module's build.gradle file in dependencies section.
   *   5. Right click on the app module > Module settings under dependencies, add module dependency to library create in step 2.
   *   6. Add in the line "Gson gson = new Gson();" in class files of app module and library module.
   *   Verification
   *   The line "Gson gsn = new Gson();" should get resolved in both the app and library modules without any errors.
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void transitiveDependenciesResolve() {
    WizardUtils.createNewProject(guiTest, "Empty Views Activity", Java, BuildConfigurationLanguage.KTS);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module\u2026")
      .clickNextToAndroidLibrary()
      .enterModuleName("library_module")
      .wizard()
      .clickFinishAndWaitForSyncToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    editor.open("/library_module/build.gradle.kts")
      .select("dependencies \\{()")
      .enterText("\n   api(\"com.google.code.gson:gson:2.6.2\")");
    ideFrame.takeScreenshot();
    guiTest.robot().waitForIdle();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath(RIGHT_BUTTON, "app")
      .invokeContextualMenuPath("Open Module Settings");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ProjectStructureDialogFixture dialogFixture = ProjectStructureDialogFixture.Companion.find(ideFrame);
    dialogFixture.waitTillProjectStructureIsLoaded();

    DependenciesPerspectiveConfigurableFixture dependenciesFixture =
      DependenciesPerspectiveConfigurableFixtureKt.selectDependenciesConfigurable(dialogFixture);

    dependenciesFixture.findModuleSelector()
      .selectModule("app");

    AddModuleDependencyDialogFixture addModuleDependencyFixture =
      dependenciesFixture.findDependenciesPanel()
        .clickAddModuleDependency();

    addModuleDependencyFixture.toggleModule("library_module");
    addModuleDependencyFixture.clickOk();
    guiTest.robot().waitForIdle();
    dialogFixture.clickOk();

    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    editor.open("/app/src/main/java/com/google/myapplication/MainActivity.java")
      .moveBetween("setContentView(R.layout.activity_main);", "")
      .enterText("\nGson gson = new Gson();");
    guiTest.robot().waitForIdle();

    editor.select("()public class MainActivity")
      .enterText("import com.google.gson.Gson;\n\n");

    // Create a class in the library and check the build.
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath("MyApplication", "library_module", "src", "main", "java", "com.google.library_module");

    invokeJavaClass(ideFrame)
      .enterName("LibraryClass")
      .clickOk();
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    editor.open("/library_module/src/main/java/com/google/library_module/LibraryClass.java")
      .select("()public class LibraryClass")
      .enterText("import com.google.gson.Gson;\n\n");
    guiTest.waitForBackgroundTasks();
    guiTest.robot().waitForIdle();

    editor.open("/library_module/src/main/java/com/google/library_module/LibraryClass.java")
      .moveBetween("public class LibraryClass {", "")
      .enterText("\nGson gson = new Gson();\n");

    BuildStatus result = guiTest.ideFrame().invokeProjectMake();
    assertTrue(result.isBuildSuccessful());
  }

  @NotNull
  private NewJavaClassDialogFixture invokeJavaClass(@NotNull IdeFrameFixture ideFrame) {
    guiTest.ideFrame().invokeMenuPath("File", "New", "Java Class");
    return NewJavaClassDialogFixture.find(ideFrame);
  }
}
