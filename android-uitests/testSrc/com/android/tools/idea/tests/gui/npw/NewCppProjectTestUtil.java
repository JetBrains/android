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
package com.android.tools.idea.tests.gui.npw;

import static com.android.tools.idea.wizard.template.Language.Java;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.CppStandardType;
import com.intellij.openapi.util.SystemInfo;
import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NewCppProjectTestUtil {

  private static final String APP_NAME = "app";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(".*adb shell am start .*myapplication\\.MainActivity.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);

  protected static void createNewProjectWithCpp(CppStandardType toolChain, GuiTestRule guiTest) throws Exception {
    createCppProject(toolChain, guiTest);

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    assertEquals(toolChain.getCompilerFlag(), getCppFlags(ideFrame));

    guiTest.waitForBackgroundTasks();
    runAppOnEmulator(ideFrame);
    ideFrame.stopApp();
  }

  protected static void createCppProject(CppStandardType toolChain, GuiTestRule guiTest) {
    guiTest.welcomeFrame()
      .createNewProject()
      .getChooseAndroidProjectStep()
      .chooseActivity("Native C++")
      .wizard()
      .clickNext()
      .getConfigureNewAndroidProjectStep()
      .setSourceLanguage(Java)
      .enterPackageName("com.example.myapplication")
      .wizard()
      .clickNext()
      .getConfigureCppStepFixture()
      .selectToolchain(toolChain)
      .wizard()
      // the QA tests don't care that much about timeouts occurring. Be generous with the timeouts
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(240));

    guiTest.waitForBackgroundTasks();

    // Sanity check we have create the right files
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/cpp/CMakeLists.txt")).isNotNull();
    assertThat(guiTest.ideFrame().findFileByRelativePath("app/src/main/cpp/native-lib.cpp")).isNotNull();

    String mainActivityText = guiTest.getProjectFileText("app/src/main/java/com/example/myapplication/MainActivity.java");
    assertThat(mainActivityText).contains("System.loadLibrary(\"myapplication\")");
    assertThat(mainActivityText).contains("public native String stringFromJNI()");
  }

  private static @Nullable String getCppFlags(@NotNull IdeFrameFixture frame) {
    String contents = frame.getEditor()
      .open("app/build.gradle")
      .getCurrentFileContents();

    Pattern cppFlags = Pattern.compile(" *cppFlags '(.*)'");

    Optional<String> flags = Arrays.stream(contents.split("\\R"))
      .map(cppFlags::matcher)
      .filter(Matcher::matches)
      .map(matcher -> matcher.group(1))
      .findFirst();

    return flags.orElse(null);
  }

  protected static void runAppOnEmulator(@NotNull IdeFrameFixture ideFrame) {
    ideFrame.runApp(APP_NAME, "Google Nexus 5X");

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrame.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
  }

  @NotNull
  protected static String getExternalNativeBuildRegExp() {
    return "(externalNativeBuild.*\n" + // externalNativeBuild {
           ".*  cmake .*\n" +           //   cmake {
           ".*    path.*\n" +           //     path "CMakeLists.txt"
           ".*\n" +                     //   }
           ".*)";
  }

  protected static void assertAndroidPanePath(boolean expectedToExist, GuiTestRule guiTest, @NotNull String... path) {
    ProjectViewFixture.PaneFixture androidPane = guiTest.ideFrame().getProjectView()
                                                        .selectAndroidPane();

    try {
      if (SystemInfo.isMac) {
        androidPane.expand();
      }
        androidPane.clickPath(path);
    }
    catch (Throwable ex) {
      if (expectedToExist) {
        throw ex;
      }
    }
  }
}
