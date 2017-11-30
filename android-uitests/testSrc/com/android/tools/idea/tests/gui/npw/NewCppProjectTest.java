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
package com.android.tools.idea.tests.gui.npw;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture.PaneFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.LinkCppProjectFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class NewCppProjectTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(".*adb shell am start .*myapplication\\.MainActivity.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);


  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ede43cef-f4a1-484b-9b1b-58000c4ba17c
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project; check the box "Include C++ Support"
   *   2. Click next until you get to the window for "Customize C++ Support" don't check any boxes
   *   3. Click Finish (Verify 1)
   *   4. Run (Verify 2)
   *
   *   Repeat 1 to 4 with below modifications:
   *   On (2), check Exceptions support, then on (4) verify that  android.defaultConfig.cmake.cppFlags has "-fexceptions"
   *   On (2), check Runtime Type Information Support, then on (4) verify that android.defaultConfig.cmake.cppFlags has "-frtti"
   *   On (2), check both Exceptions & Runtime Type, then verify that android.defaultConfig.cmake.cppFlags has "-fexceptions -frtti"
   *   in any order."
   *   </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewProjectWithCpp1() throws Exception {
    createNewProjectWithCpp(false, false);
  }

  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewProjectWithCpp2() throws Exception {
    createNewProjectWithCpp(true, false);
  }

  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewProjectWithCpp3() throws Exception {
    createNewProjectWithCpp(false, true);
  }

  @RunIn(TestGroup.SANITY)
  @Test
  public void createNewProjectWithCpp4() throws Exception {
    createNewProjectWithCpp(true, true);
  }

  /**
   * To verify project deploys successfully after adding and removing dependency
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 45d4c00b-a6a8-4e48-b9af-d55094ae17a3
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project, checking the box for "Include C++ Support"
   *   2. Remove the externalNativeBuild section of the project level build.gradle
   *   3. Sync gradle; verify that the project's app/cpp files are gone but app/java remains
   *   4. Go to File -> Link C++ Project with Gradle
   *   5. Leave the build system dropdown on cmake and select ${projectDir}/app/CMakeLists.txt for project path (Verify 1, 2)
   *   6. Run (Verify 3)
   *
   *   Verification:
   *   1) Verify that the externalNativeBuild section of build.gradle reappears with cmake.path CMakeLists.txt
   *   2) Verify that app/cpp reappears and contains native-lib.cpp
   *   3) Verify that the project builds and runs on an emulator
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37918835
  @Test
  public void addRemoveCppDependency() throws Exception {
    createCppProject(false, false);

    assertAndroidPanePath(true, "app", "cpp", "native-lib.cpp");

    IdeFrameFixture ideFixture = guiTest.ideFrame();
    ideFixture
      .getEditor()
      .open("app/build.gradle")
      .select(getExternalNativeBuildRegExp())
      .enterText(" ") // Remove the externalNativeBuild section
      .getIdeFrame()
      .requestProjectSync()
      .waitForGradleProjectSyncToFinish();

    // verify that the project's app/cpp files are gone but app/java remains
    assertAndroidPanePath(false, "app", "cpp", "native-lib.cpp");
    assertAndroidPanePath(true, "app", "java");

    ideFixture
      .openFromMenu(LinkCppProjectFixture::find, "File", "Link C++ Project with Gradle")
      .selectCMakeBuildSystem()
      .enterCMakeListsPath(getCMakeListsPath())
      .clickOk()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .select(getExternalNativeBuildRegExp()); // externalNativeBuild section of build.gradle reappears with cmake.path CMakeLists.txt

    assertAndroidPanePath(true, "app", "cpp", "native-lib.cpp"); // app/cpp reappears and contains native-lib.cpp

    runAppOnEmulator();
  }

  /**
   * Creates a new project with c++, and checks that if we run Analyze > Inspect Code, there are no warnings.
   * This checks that our (default) project templates are warnings-clean.
   * The test then proceeds to make a couple of edits and checks that these do not generate additional warnings either.
   */
  @RunIn(TestGroup.PROJECT_WIZARD)
  @Test
  public void noWarningsInNewProjectWithCpp() {
    createCppProject(false, false);

    String inspectionResults = guiTest.ideFrame()
      .openFromMenu(InspectCodeDialogFixture::find, "Analyze", "Inspect Code...")
      .clickOk()
      .getResults();

    // TODO Disable spell checking before running inspection, then compare for equality like noWarningsInNewProjects does.
    // These two strings are categories that are very common when there will be some problem compiling.
    assertThat(inspectionResults).doesNotContain("Declaration order");
    assertThat(inspectionResults).doesNotContain("Unused code");
  }

  private void createNewProjectWithCpp(boolean hasExceptionSupport, boolean hasRuntimeInformation) throws Exception {
    createCppProject(hasExceptionSupport, hasRuntimeInformation);

    String gradleCppFlags = guiTest.ideFrame().getEditor()
      .open("app/build.gradle")
      .moveBetween("cppFlags \"", "")
      .getCurrentLine();

    String cppFlags = String.format("%s %s", hasRuntimeInformation ? "-frtti" : "",  hasExceptionSupport ? "-fexceptions" : "").trim();
    Assert.assertEquals(String.format("cppFlags \"%s\"", cppFlags), gradleCppFlags.trim());

    runAppOnEmulator();
  }

  private void createCppProject(boolean hasExceptionSupport, boolean hasRuntimeInformation) {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame()
      .createNewProject();

    newProjectWizard.getConfigureAndroidProjectStep()
      .setCppSupport(true); // Default "App name", "company domain" and "package name"
    newProjectWizard.clickNext();
    newProjectWizard.clickNext(); // Skip "Select minimum SDK Api" step
    newProjectWizard.clickNext(); // Skip "Add Activity" step
    newProjectWizard.clickNext(); // Use default activity names

    newProjectWizard.getConfigureCppStepFixture()
      .setExceptionsSupport(hasExceptionSupport)
      .setRuntimeInformationSupport(hasRuntimeInformation);

    newProjectWizard.clickFinish();

    guiTest.ideFrame().waitForGradleProjectSyncToFinish();
  }

  private void runAppOnEmulator() throws ClassNotFoundException {
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    guiTest.ideFrame()
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ExecutionToolWindowFixture.ContentFixture contentFixture = guiTest.ideFrame().getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }

  @NotNull
  private static String getExternalNativeBuildRegExp() {
    return "(externalNativeBuild.*\n" + // externalNativeBuild {
           ".*  cmake .*\n" +           //   cmake {
           ".*    path.*\n" +           //     path "CMakeLists.txt"
           ".*\n" +                     //   }
           ".*)";
  }

  @NotNull
  private String getCMakeListsPath() {
    File appPath = new File(guiTest.getProjectPath(), "app");
    return new File(appPath, "CMakeLists.txt").getAbsolutePath();
  }

  private void assertAndroidPanePath(boolean expectedToExist, @NotNull String... path) {
    PaneFixture androidPane = guiTest.ideFrame().getProjectView()
      .selectAndroidPane();

    try {
      androidPane.clickPath(path);
    }
    catch (Throwable ex) {
      if (expectedToExist) {
        throw ex;
      }
    }
  }
}
