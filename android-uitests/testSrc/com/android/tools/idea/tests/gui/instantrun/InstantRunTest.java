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
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture;
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
@RunIn(TestGroup.QA)
public class InstantRunTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final Pattern EMPTY_OUTPUT= Pattern.compile("^$", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern CMAKE_RUN_OUTPUT =
    Pattern.compile(".*adb shell am start .*google\\.basiccmake.*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final Pattern HOT_SWAP_OUTPUT =
    Pattern.compile(".*Hot swapped changes, activity restarted.*", Pattern.DOTALL);
  private static final int OUTPUT_RESET_TIMEOUT = 30;

  /**
   * Verifies that instant run hot swap works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 80302441-094b-44e0-b2a8-bd076a5f001d
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
  @RunIn(TestGroup.SANITY)
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
      .moveBetween("setContentView(R.layout.activity_my);", "")
      .enterText("\nSystem.out.println(\"Hello, hot swap!\");");

    ideFrameFixture
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
   * TT ID: 2dd47337-e044-4607-9d62-03c324cff486
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
  @RunIn(TestGroup.SANITY)
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
      .waitForRenderToFinish(Wait.seconds(30))
      .dragComponentToSurface("Text", "TextView")
      .waitForRenderToFinish(Wait.seconds(30));

    ideFrameFixture
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(OUTPUT_RESET_TIMEOUT).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
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
   * TT ID: 6fcc57cb-6b38-410f-b26c-7faacb99fca4, 56306388-53c0-4b18-b4be-c43110933fc4
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
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/37506663
  public void activityRunningOnSeparateProcess() throws Exception {
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
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(OUTPUT_RESET_TIMEOUT).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
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
   * TT ID: 02d4c23c-70e5-46ef-ab1f-7c29577ea6ed
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
  @RunIn(TestGroup.SANITY)
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
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(OUTPUT_RESET_TIMEOUT).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
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
   * TT ID: c457a7e4-e95a-4ad5-bda6-5c0f4c5668fa
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
  @Test
  public void unnecessaryCleanCheck() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput();

    ideFrameFixture
      .getRunToolWindow()
      .clickRerunApplication()
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    Wait.seconds(OUTPUT_RESET_TIMEOUT).expecting("Run tool window output has been reset").until(() -> !contentFixture.getOutput().contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
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
   * TT ID: bac65e3a-ff72-4e5f-a9a3-c403b3dacc19
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
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/68046183 - this test is flaking out
  public void modifyVariableDuringDebugSession() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("Project204792");
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
   * TT ID: 23a7eaf2-aba0-4aca-9bd8-28f9c24c855b
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
  @RunIn(TestGroup.SANITY)
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
      .moveBetween("tv.setText(stringFromJNI());", "")
      .enterText("\nSystem.out.println(\"Hello, CMake hot swap!\");");

    ideFrameFixture
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
   * TT ID: e5057c86-a850-4fe7-aac8-a0764c5682ce
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
  @RunIn(TestGroup.QA_UNRELIABLE)
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
    Wait.seconds(5).expecting("Apply changes button to be enabled").until(() -> ideFrameFixture.findApplyChangesButton().isEnabled());
  }

  /**
   * Verifies that Instant Run should not break after interchanging order of resources : b.android.com/200895
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 22759807-e0c4-48a0-af6a-a83ca8647e56
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project with empty activity
   *   2. Open Main xml file
   *   3. Add Two different elements say, TextView and Button
   *   <TextView id="@+id/view1/>
   *   <Button id="@+id/view2/>
   *   And in onCreate() access them:
   *   TextView a = (TextView) findViewById(R.id.view1);
   *   Button b = (Button) findViewById(R.id.view2);
   *   Change order of the views:
   *   <Button id="@+id/view2/>
   *   <TextView id="@+id/view1/>
   *   4. Make Instant Run
   *   Verify:
   *   Application should run smoothly without any errors and showing "Hot swapped changes, activity restarted."
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void changeOrderOfResources() throws Exception {
    NewProjectWizardFixture newProjectWizard = guiTest.welcomeFrame().createNewProject();
    newProjectWizard
      .getConfigureAndroidProjectStep()
      .enterApplicationName("Test Application")
      .enterPackageName("com.test.project");
    newProjectWizard
      .clickNext()
      .clickNext()
      .clickNext()
      .clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame().waitForGradleProjectSyncToFinish();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    String MAIN_LAYOUT_FILE = "app/src/main/res/layout/activity_main.xml";
    String MAIN_ACTIVITY_FILE = "app/src/main/java/com/test/project/MainActivity.java";

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.EDITOR)
      .moveBetween("<TextView", "")
      .enterText("\nandroid:id=\"@+id/view1\"");

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Buttons", "Button");

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.EDITOR)
      .select("\"@\\+id/(button)\"")
      .enterText("view2");

    ideFrameFixture
      .getEditor()
      .open(MAIN_ACTIVITY_FILE, EditorFixture.Tab.EDITOR)
      .moveBetween("setContentView(R.layout.activity_main);", "")
      .enterText("\nandroid.widget.TextView a = (android.widget.TextView)findViewById(R.id.view1);" +
                 "\nandroid.widget.Button b = (android.widget.Button)findViewById(R.id.view2);");

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
    ExecutionToolWindowFixture.ContentFixture contentFixture =
      ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.EDITOR)
      .select("(<TextView[\\s\\S]*/>\\s)*<Button")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE);

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .dragComponentToSurface("Text", "TextView");

    ideFrameFixture
      .getEditor()
      .open(MAIN_LAYOUT_FILE, EditorFixture.Tab.EDITOR)
      .select("\"@\\+id/(textView)\"")
      .enterText("view1");

    ideFrameFixture
      .findApplyChangesButton()
      .click();

    contentFixture.waitForOutput(new PatternTextMatcher(HOT_SWAP_OUTPUT), 120);
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


  /**
   * Verifies that Studio InstantRun performs a full build and reinstall when manifest values are changed
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b8a07ba8-a6ff-4fe3-ac83-52c8e7c02465
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open test project
   *   2. Hit Run and deploy app to emulator running API level 23.
   *   3. Open app/manifests/AndroidManifest.xml
   *   4. Change android:label="@string/app_name" to android:label="Instant Run"
   *   5. Hit Instant Run
   *   Verify:
   *   Verify that Android Studio does a full build and re-installs the app.
   *   </pre>
   */
  @RunIn(TestGroup.QA_UNRELIABLE) // http://b/62204067
  @Test
  public void fullBuildAndReinstall() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("Topeka");
    emulator.createAVD(guiTest.ideFrame().invokeAvdManager(),
                       "x86 Images",
                       new ChooseSystemImageStepFixture.SystemImage("Marshmallow", "23", "x86", "Android 6.0"),
                       "device under test");
    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    PatternTextMatcher runningAppMatcher = new PatternTextMatcher(RUN_OUTPUT);

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture
      .getRunToolWindow()
      .findContent(APP_NAME);
    contentFixture.waitForOutput(runningAppMatcher, EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    contentFixture.clickClearAllButton()
      .waitForOutput(new PatternTextMatcher(EMPTY_OUTPUT), EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .select("android:label=([\\S]*)")
      .enterText("\"Instant Run\"");

    ideFrameFixture
      .findApplyChangesButton()
      .click();
    contentFixture.waitForOutput(runningAppMatcher, EmulatorTestRule.DEFAULT_EMULATOR_WAIT_SECONDS);
  }
}
