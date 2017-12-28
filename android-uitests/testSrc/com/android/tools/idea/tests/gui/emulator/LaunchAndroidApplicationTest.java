/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.BrowseSamplesWizardFixture;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.SystemProperties;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;

import static com.android.tools.idea.gradle.util.BuildMode.REBUILD;
import static com.android.tools.idea.tests.gui.framework.fixture.MessagesToolWindowFixture.MessageMatcher.firstLineStartingWith;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.ide.errorTreeView.ErrorTreeElementKind.ERROR;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.fest.swing.core.matcher.DialogMatcher.withTitle;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.fest.swing.finder.WindowFinder.findDialog;

@RunWith(GuiTestRunner.class)
public class LaunchAndroidApplicationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

  private static final String APP_NAME = "app";
  private static final String APPLICATION_STARTED = ".*Application started.*";
  private static final String FATAL_SIGNAL_11_OR_6 = ".*SIGSEGV.*|.*SIGABRT.*";
  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final String INSTRUMENTED_TEST_CONF_NAME = "instrumented_test";
  private static final String ANDROID_INSTRUMENTED_TESTS = "Android Instrumented Tests";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*adb shell am start .*google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern INSTRUMENTED_TEST_OUTPUT = Pattern.compile(
    ".*adb shell am instrument .*AndroidJUnitRunner.*Tests ran to completion.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);
  private static final Pattern DEBUG_OUTPUT = Pattern.compile(".*Connected to the target VM.*", Pattern.DOTALL);


  /**
   * Verifies that a project can be deployed on an emulator
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 579892c4-e1b6-48f7-a5a2-69a12c12ce83
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Add a few layout elements to the default activity
   *   3. Click Run
   *   4. From the device chooser dialog, select the running emulator and click Ok
   *   Verify:
   *   Project builds successfully and runs on the emulator
   *   </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void testRunOnEmulator() throws Exception {
    InstantRunSettings.setShowStatusNotifications(false);
    guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture.getAndroidToolWindow().selectDevicesTab().selectProcess(PROCESS_NAME);
    ideFrameFixture.stopApp();
  }

  /**
   * Verifies that debugger can be invoked on an application by setting breakpoints
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: b9daba2b-067f-434b-91b2-c02197ac1521
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Open the default activity class file
   *   3. In the OnCreate method, add a breakpoint
   *   4. Click on Debug project
   *   5. Select a running emulator
   *   Verify:
   *   The application is deployed on the emulator/device and the breakpoint is hit when the first screen loads
   *   </pre>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void testDebugOnEmulator() throws IOException, ClassNotFoundException, EvaluateException {
    guiTest.importSimpleApplication();
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    ideFrameFixture.getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), 120);

    ideFrameFixture.getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);
    ideFrameFixture.stopApp();
  }

  /**
   * To verify NDK project compiles when running two files with same filename in different libs.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1a36d98e-a0bf-4a4f-8eed-6fd55aa61a30
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import NdkDupeFilename project.
   *   3. Compile and run on the emulator.
   *   Verify:
   *   1. Application can run without errors.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void testNdkHandlesDupeFilename() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("NdkDupeFilename");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());
    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();
    ExecutionToolWindowFixture.ContentFixture contentWindow = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentWindow.waitForOutput(new PatternTextMatcher(Pattern.compile(APPLICATION_STARTED, Pattern.DOTALL)), 120);
    contentWindow.stop();
  }

  /**
   * To verify app crashes if vulkan graphics is not supported.
   * <p>
   * The ideal test would launch the app on a real device (Nexus 5X or 6P). Since there
   * if no current framework support to run the app on a real device, this test reverses
   * the scenario and verifies the app will crash when the vulcan graphics card is not
   * present on the emulator when running the app.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 62320838-5ab1-4808-9a56-11e1fe349e1a
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import VulkanCrashes project.
   *   3. Navigate to the downloaded vulkan directory.
   *   3. Compile and run build.gradle file on the emulator.
   *   Verify:
   *   1. Application crashes in the emulator.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void testVulkanCrashes() throws IOException, ClassNotFoundException {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("VulkanCrashes");

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    // The app must run under the debugger, otherwise there is a race condition where
    // the app may crash before Android Studio can connect to the console.
    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // Look for text indicating a crash. Check for both SIGSEGV and SIGABRT since they are both given in some cases.
    ExecutionToolWindowFixture.ContentFixture contentWindow = ideFrameFixture.getDebugToolWindow().findContent(APP_NAME);
    contentWindow.waitForOutput(new PatternTextMatcher(Pattern.compile(FATAL_SIGNAL_11_OR_6, Pattern.DOTALL)), 120);
    contentWindow.stop();
  }

  /**
   * To verify that sample ndk projects can be imported and breakpoints in code are hit.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1eb26e7c-5127-49aa-83c9-32d9ff160315
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import Teapot from the cloned repo
   *   3. Open TeapotNativeActivity.cpp
   *   4. Click on any method and make sure it finds the method or its declaration
   *   5. Search for the symbol HandleInput and put a break point in the method
   *   6. Deploy to a device/emulator
   *   7. Wait for notification that the debugger is connected
   *   8. Teapot should show up on the device
   *   9. Touch to interact
   *   Verify:
   *   1. After step 9, the breakpoint is triggered.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA_UNRELIABLE)
  @Test
  public void testCppDebugOnEmulatorWithBreakpoint() throws Exception {
    BrowseSamplesWizardFixture samplesWizard = guiTest.welcomeFrame()
      .importCodeSample();
    samplesWizard.selectSample("Ndk/Teapots")
      .clickNext()
      .getConfigureFormFactorStep()
      .enterApplicationName("TeapotTest");

    samplesWizard.clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    ideFrameFixture
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/jni/TeapotNativeActivity.cpp")
      .moveBetween("g_engine.Draw", "Frame()")
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT) // First break point - First Frame is drawn
      .moveBetween("static int32_t Handle", "Input(")
      .invokeAction(EditorFixture.EditorAction.GOTO_IMPLEMENTATION)
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT); // Second break point - HandleInput()

    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains("int32_t Engine::HandleInput(");

    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    ideFrameFixture
      .debugApp(APP_NAME)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    // Wait for the UI App to be up and running, by waiting for the first Frame draw to get hit.
    expectBreakPoint("g_engine.DrawFrame()");

    // Simulate a screen touch
    emulator.getEmulatorConnection().tapRunningAvd(400, 400);

    // Wait for the Cpp HandleInput() break point to get hit.
    expectBreakPoint("Engine* eng = (Engine*)app->userData;");
  }

  /**
   * To verify that importing a sample project and deploying on test device.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ae1223a3-b42d-4c8f-8837-5c6f7e8c583a
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Import Background/Job Scheduler from sample projects
   *   3. Create an emulator
   *   4. Deploy the project on the emulator
   *   Verify:
   *   1. The sample project is built successfully and deployed on the emulator.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void importSampleProject() throws Exception {
    BrowseSamplesWizardFixture samplesWizard = guiTest.welcomeFrame()
      .importCodeSample();
    samplesWizard.selectSample("Ui/Done Bar")
      .clickNext()
      .clickFinish();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    // Currently, Build Tools 26.0.1 is required for this sample project.
    // However, in prebuild, Build Tools 26.0.1 is not installed.
    // Here, we expect a Gradle sync failure.
    ideFrameFixture.waitForGradleProjectSyncToFail();

    MessagesToolWindowFixture messagesToolWindow = ideFrameFixture.getMessagesToolWindow();
    MessagesToolWindowFixture.MessageFixture message = messagesToolWindow.getGradleSyncContent()
      .findMessage(ERROR, firstLineStartingWith("Failed to find Build Tools revision"));
    MessagesToolWindowFixture.HyperlinkFixture hyperlink = message.findHyperlinkByContainedText("Install Build Tools");
    hyperlink.clickAndContinue();

    DialogFixture downloadDialog = findDialog(withTitle("SDK Quickfix Installation"))
      .withTimeout(SECONDS.toMillis(30)).using(guiTest.robot());
    JButtonFixture finish = downloadDialog.button(withText("Finish"));
    Wait.seconds(120).expecting("Android source to be installed").until(finish::isEnabled);
    finish.click();

    GuiTests.findAndClickButtonWhenEnabled(
      ideFrameFixture.waitForDialog("Android Gradle Plugin Update Recommended", 120),
      "Update");

    ideFrameFixture.requestProjectSync().waitForGradleProjectSyncToFinish(Wait.seconds(30));

    emulator.createDefaultAVD(ideFrameFixture.invokeAvdManager());

    String appName = "Application";
    ideFrameFixture
      .runApp(appName)
      .selectDevice(emulator.getDefaultAvdName())
      .clickOk();

    ideFrameFixture.getRunToolWindow().findContent(appName)
      .waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }

  /**
   * To verify that instrumentation tests can be added and executed.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c8fc3fd5-1a7d-405d-974f-5e4f0b42e168
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio
   *   2. Create a new project
   *   3. Create an avd
   *   4. Open the default instrumented test example
   *   5. Open Run/Debug Configuration Settings
   *   6. Click on the "+" button and select Android Instrumented Tests
   *   7. Add a name to the test
   *   8. Select the app module and click OK"
   *   9. Run "ExampleInstrumentedTest" with test configuration created previously
   *   Verify:
   *   1. Test runs successfully by checking the output of running the instrumented test.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY)
  @Test
  public void testRunInstrumentationTest() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("InstrumentationTest");
    emulator.createDefaultAVD(guiTest.ideFrame().invokeAvdManager());

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(guiTest.robot())
        .clickAddNewConfigurationButton()
        .selectConfigurationType(ANDROID_INSTRUMENTED_TESTS)
        .enterAndroidInstrumentedTestConfigurationName(INSTRUMENTED_TEST_CONF_NAME)
        .selectModuleForAndroidInstrumentedTestsConfiguration(APP_NAME)
        .clickOk();

    ideFrameFixture.runApp(INSTRUMENTED_TEST_CONF_NAME).selectDevice(emulator.getDefaultAvdName()).clickOk();

    ideFrameFixture.getRunToolWindow().findContent(INSTRUMENTED_TEST_CONF_NAME)
        .waitForOutput(new PatternTextMatcher(INSTRUMENTED_TEST_OUTPUT), 120);
  }

  private void expectBreakPoint(String lineText) {
    DebugToolWindowFixture debugToolWindow = guiTest.ideFrame()
      .getDebugToolWindow()
      .waitForBreakPointHit();

    // Check we have the right debug line
    assertThat(guiTest.ideFrame().getEditor().getCurrentLine())
      .contains(lineText);

    // Remove break point
    guiTest.ideFrame()
      .getEditor()
      .invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);

    debugToolWindow.pressResumeProgram();
  }

  /**
   * To verify that build cache can be turned off successfully and ensure that android.dexOptions.preDexLibraries is either true or false
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 73c60c6c-6228-41ad-87b1-2f0708bbb50e
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. <user-home-directory>/.android/build-cache delete the existing build-cache directory.
   *   3. Open the gradle.properties and don't do any modification
   *   4. Build the project (verify 1)
   *   5. Repeat 1 to 4 with below modifications
   *   On (3),add android.enableBuildCache=false (verify 2)
   *   On (3),add android.enableBuildCache=true (verify 3)
   *   Verify:
   *   1. You should be able to see build-cache generated with cached data in <user-home-directory>/.android/build-cache . Build cache will be enalbed by default
   *   2. You should see that build-cache is not generated.
   *   3. Verify that build-cache is generated.
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void turnOnOrOffBuildCache() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    File homeDir = new File(SystemProperties.getUserHome());
    File androidHomeDir = new File(homeDir, ".android");
    File buildCacheDir = new File(androidHomeDir, "build-cache");
    FileUtil.delete(buildCacheDir);

    ideFrameFixture.waitAndInvokeMenuPath("Build", "Rebuild Project")
      .waitForBuildToFinish(REBUILD);
    assertThat(buildCacheDir.exists()).isTrue();

    FileUtil.delete(buildCacheDir);
    ideFrameFixture.getEditor()
      .open("gradle.properties")
      .moveBetween("true", "")
      .enterText("\nandroid.enableBuildCache=false");
    ideFrameFixture.waitAndInvokeMenuPath("Build", "Rebuild Project")
      .waitForBuildToFinish(REBUILD);
    assertThat(buildCacheDir.exists()).isFalse();

    ideFrameFixture.getEditor()
      .open("gradle.properties")
      .select("(false)")
      .enterText("true");
    ideFrameFixture.waitAndInvokeMenuPath("Build", "Rebuild Project")
      .waitForBuildToFinish(REBUILD);
    assertThat(buildCacheDir.exists()).isTrue();
  }
}
