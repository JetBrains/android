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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.tools.idea.tests.gui.debugger.DebuggerTestBase;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.google.common.base.Strings;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class InstantRunTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.simpleapplication.*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern CMAKE_RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.basiccmake.*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);

  /**
   * Verifies that instant run hot swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581583
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a java file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run hot swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37480946
  @Test
  public void hotSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String pid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .enterText(Strings.repeat("\n", 10));

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    contentFixture.waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);
    // (Hot swap) Verify the equality of PIDs before and after IR.
    assertThat(pid).isEqualTo(newPid);
  }

  /**
   * Verifies that instant run cold swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581584
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a resource xml file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run cold swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37959653
  @Test
  public void coldSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();
    String pid = extractPidFromOutput(output, RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Text", "TextView")
      .waitForRenderToFinish();

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(10).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  /**
   * Verifies that Instant run user notification on activity running on separate process.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14606152
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project
   *   2. Run on emulator
   *   3. Open AndroidManifest
   *   4. For launcher activity set android:process=":foo"
   *   </pre>
   *   Verify:
   *   Verify that instant run cold swap is applied in Run tool window.
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37512428
  @Test
  public void ActivityRunningOnSeparateProcess() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();
    String pid = extractPidFromOutput(output, RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("<application", "")
      .enterText("\nandroid:process=\":foo\"");

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(10).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  /**
   * Verifies that instant run works as expected when AndroidManifest is changed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581585
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit AndroidManifest.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run is applied in EventLog tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37506663
  @Test
  public void changeManifest() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();
    String pid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("", "<application")
      .enterText("<uses-permission android:name=\"android.permission.INTERNET\" /\n");

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(10).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(), RUN_OUTPUT);
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  /**
   * Verifies if IDE performs an unnecessary clean b.android.com/201411
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14606153
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project with basic activity
   *   2. Launch app, select a new (i.e. not already running) emulator to deploy
   *   3. Click Rerun button on the top left of the run tool window
   *   Verify:
   *   1. Clicking on re-run should not do a clean build just install application to emulator/device
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37506663
  @Test
  public void unnecessaryCleanCheck() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture
      .getRunToolWindow()
      .clickRerunApplication()
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }

  private void clickButtonAndWaitForVerification(IdeFrameFixture ideFrameFixtures, String[] expectedPatterns) {
    Wait.seconds(30).expecting("The app is ready and the button is clicked").until(() -> {
      try {
        emulator.getEmulatorConnection().tapRunningAvd(100, 300);
        DebuggerTestBase.checkAppIsPaused(ideFrameFixtures, expectedPatterns);
        return true;
      } catch (WaitTimedOutError e) {
        return false;
      }
    });
  }

  /**
   * Verifies that changes to the variable value should reflect during an active debug session (b.android.com/204792)
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14606155
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import Project204792
   *   2. Open TestJava.java file and set breakpoints to line #16
   *   3. Debug App on emulator/device
   *   4. Click on Button which makes debugger stop at line #16
   *   5. Verify 1
   *   6. Change 'x' value to 150
   *   7. Debug again
   *   8. Click on Button
   *   9. Verify 2
   *   Verify:
   *   1. Variable console should print x value (as 100)
   *   2. Variable console should print x value (as 150)
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37912103
  @Test
  public void modifyVariableDuringDebugSession() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProject("Project204792");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    final String TEST_FILE = "app/src/main/java/com/bug204792/myapplication/TestJava.java";
    final Pattern pattern = Pattern.compile(".*Connecting to com.bug204792.myapplication.*", Pattern.DOTALL);

    ideFrameFixture
      .getEditor()
      .open(TEST_FILE)
      .waitUntilErrorAnalysisFinishes()
      .moveBetween("", "}")
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);

    ideFrameFixture.debugApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getDebugToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(pattern), 120);

    String[] expectedPatterns = new String[]{
      DebuggerTestBase.variableToSearchPattern("x", "100"),
    };
    clickButtonAndWaitForVerification(ideFrameFixture, expectedPatterns);

    ideFrameFixture
      .getEditor()
      .open(TEST_FILE)
      .select("(100)")
      .enterText("150");

    ideFrameFixture.findDebugApplicationButton().click();
    ideFrameFixture.getDebugToolWindow()
      .findContent(APP_NAME)
      .waitForOutput(new PatternTextMatcher(pattern), 120);

    expectedPatterns = new String[]{
      DebuggerTestBase.variableToSearchPattern("x", "150"),
    };
    clickButtonAndWaitForVerification(ideFrameFixture, expectedPatterns);
  }

  /**
   * Verifies that instant run hot swap works as expected on a C++ support project.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603463
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmake.
   *   2. Update the gradle plugin version if necessary for testing purpose.
   *   3. Create an AVD with a system image API 21 or above.
   *   4. Run on the AVD
   *   5. Verify 1.
   *   6. Edit a java file.
   *   7. Run again.
   *   8. Verify 2.
   *   Verify:
   *   1. Make sure the right app is installed and started in Run tool window.
   *   2. Make sure the instant run hot swap is applied in Run tool window.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/37480946
  @Test
  public void cmakeHotSwap() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("BasicCmake");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(CMAKE_RUN_OUTPUT), 120);
    String pid = extractPidFromOutput(contentFixture.getOutput(), CMAKE_RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/java/google/basiccmake/MainActivity.java")
      .enterText(Strings.repeat("\n", 10));

    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .findApplyChangesButton()
      .click();

    contentFixture.waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(), CMAKE_RUN_OUTPUT);
    // (Hot swap) Verify the equality of PIDs before and after IR.
    assertThat(pid).isEqualTo(newPid);
  }

  /**
   * Verifies that Studio suggests to install when correspnding platform is not installed while deploying
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14606157
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Make sure that the platform-22 is not installed in our prebuilt SDK.
   *   3. Press run and select a device with API level 22. (Verify)
   *   Verify:
   *   IDE should ask if you want to install platform 22.
   *   If you answer yes, application should deploy and run with Instant Run enabled.
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void installingPlatformWhileDeployingApp() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createAVD(guiTest.ideFrame().invokeAvdManager(),
                       "x86 Images",
                       new ChooseSystemImageStepFixture.SystemImage("Lollipop", "22", "x86", "Android 5.1"),
                       "device under test");

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    JButton button = waitUntilShowingAndEnabled(guiTest.robot(), ideFrameFixture.target(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return component.getText().equals("Install and Continue");
      }
    });
    new JButtonFixture(guiTest.robot(), button).click();

    DialogFixture downloadDialog =
      findDialog(withTitle("SDK Quickfix Installation")).withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    JButtonFixture finish = downloadDialog.button(withText("Finish"));
    Wait.seconds(120).expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    ideFrameFixture.findApplyChangesButton().requireEnabled();
  }

  @NotNull
  private static String extractPidFromOutput(@NotNull String output, @NotNull Pattern pattern) {
    Matcher m = pattern.matcher(output);
    String pid = null;
    if (m.find()) {
      pid = m.group(1);
    }
    return pid;
  }
}
