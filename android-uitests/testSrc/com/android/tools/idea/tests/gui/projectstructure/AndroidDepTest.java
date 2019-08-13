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

import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateFileFromTemplateDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.AddModuleDependencyDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.DependenciesPerspectiveConfigurableFixtureKt;
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.ProjectStructureDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AndroidDepTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  @Before
  public void setUp() {
    StudioFlags.NEW_PSD_ENABLED.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.NEW_PSD_ENABLED.clearOverride();
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
    IdeFrameFixture ideFrame = DependenciesTestUtil.createNewProject(guiTest, DependenciesTestUtil.APP_NAME, DependenciesTestUtil.MIN_SDK, DependenciesTestUtil.LANGUAGE_JAVA);

    ideFrame.openFromMenu(NewModuleWizardFixture::find, "File", "New", "New Module...")
      .clickNextToAndroidLibrary()
      .enterModuleName("library_module")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish();

    EditorFixture editor = ideFrame.getEditor()
      .open("/library_module/build.gradle")
      .select("dependencies \\{()")
      .enterText("\napi 'com.google.code.gson:gson:2.6.2'\n");

    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(RIGHT_BUTTON, DependenciesTestUtil.APP_NAME, "app");

    ideFrame.invokeMenuPath("Open Module Settings");

    ProjectStructureDialogFixture dialogFixture = ProjectStructureDialogFixture.Companion.find(ideFrame);
    DependenciesPerspectiveConfigurableFixture dependenciesFixture =
      DependenciesPerspectiveConfigurableFixtureKt.selectDependenciesConfigurable(dialogFixture);

    AddModuleDependencyDialogFixture addModuleDependencyFixture = dependenciesFixture.findDependenciesPanel().clickAddModuleDependency();
    addModuleDependencyFixture.toggleModule("library_module");
    addModuleDependencyFixture.clickOk();
    dialogFixture.clickOk();

    editor.open("/app/src/main/java/android/com/app/MainActivity.java")
      .moveBetween("setContentView(R.layout.activity_main);", "")
      .enterText("\nGson gson = new Gson();")
      .select("()public class MainActivity")
      .enterText("import com.google.gson.Gson;\n\n");

    // Create a class in the library and check the build.
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath(DependenciesTestUtil.APP_NAME, "library_module", "src", "main", "java", "android.com.library_module");

    invokeNewFileDialog().setName("LibraryClass").clickOk();
    editor.open("/library_module/src/main/java/android/com/library_module/LibraryClass.java")
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
}
