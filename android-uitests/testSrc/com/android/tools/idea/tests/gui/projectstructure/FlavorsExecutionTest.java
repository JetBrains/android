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

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ShellCommandHandler;
import com.android.tools.idea.fd.InstantRunSettings;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.FlavorsTabFixture;
import com.android.tools.idea.tests.gui.framework.fixture.projectstructure.ProjectStructureDialogFixture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class FlavorsExecutionTest {

  private final static Logger LOG = Logger.getInstance(FlavorsExecutionTest.class);

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final String ACTIVITY_OUTPUT_PATTERN =
    ".*adb shell am start .*google\\.simpleapplication\\.Main_Activity.*Connected to process.*";
  private static final String FIRST_ACTIVITY_NAME = "F1_Main_Activity";
  private static final String SECOND_ACTIVITY_NAME = "F2_Main_Activity";
  private static final String FLAVOR1 = "flavor1";
  private static final String FLAVOR2 = "flavor2";

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    ActivityManagerCommandHandler.ProcessStarter startCmdHandler = new ActivityManagerCommandHandler.ProcessStarter() {
      @NotNull
      @Override
      public String startProcess(@NotNull DeviceState deviceState) {
        deviceState.startClient(1234, 1235, PROCESS_NAME, false);
        return "";
      }
    };

    FakeAdbServer.Builder adbBuilder = new FakeAdbServer.Builder();
    adbBuilder.installDefaultCommandHandlers()
              .setShellCommandHandler(ActivityManagerCommandHandler.COMMAND, () -> new ActivityManagerCommandHandler(startCmdHandler))
              .setShellCommandHandler(LogcatCommandHandler.COMMAND, LogcatCommandHandler::new)
              .setDeviceCommandHandler(JdwpCommandHandler.COMMAND, JdwpCommandHandler::new);

    fakeAdbServer = adbBuilder.build();
    DeviceState fakeDevice = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "8.1",
      "27",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    fakeDevice.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  /***
   * To verify that the selected app flavor activity can be launched using build variants
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 5bf8bdbc-2ef1-4cd7-aa13-cbc91323cac9
   * <pre>
   *   Test Steps:
   *   1. Import a project
   *   2. Open Project Structure Dialog
   *   3. Select app module, add two new flavors (Flavor1 and Flavor2),
   *      and add flavorDimensions to build.gradle (Module: app)
   *   4. Switch to Project View
   *   5. Select app
   *   6. Add launcher activities under Flavor1 and Flavor2 and name them F1_Main_Activity and F2_Main_Activity
   *   7. Open Build variants window and select flavor1Debug
   *   8. Deploy the project on an AVD (Verify 1)
   *   9. Select flavor2Debug from Build variants
   *   10. Deploy the project on an AVD (Verify 2)
   *   Verification:
   *   1. Verify in Android Run tool window for the launch of F1_Main_Activity
   *   2. Verify in Android Run tool window for the launch of F2_Main_Activity
   * </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void runBuildFlavors() throws Exception {
    guiTest.importSimpleLocalApplication();

    InstantRunSettings.setShowStatusNotifications(false);

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    FlavorsTabFixture flavorsTabFixture = ideFrameFixture
      .openFromMenu(ProjectStructureDialogFixture::find, "File", "Project Structure...")
      .selectConfigurable("app")
      .selectFlavorsTab()
      .clickAddButton()
      .setFlavorName(FLAVOR1)
      .clickAddButton()
      .setFlavorName(FLAVOR2);

    try {
      flavorsTabFixture.clickOk();
    } catch (RuntimeException e) {
      System.out.println("Expected to fail here. Need to add dimension feature.");
    }

    String dimenName = "demo";
    String dimen = "\n dimension \"" + dimenName + "\"";
    ideFrameFixture.getEditor()
      .open("/app/build.gradle")
      .moveBetween("", "productFlavors {")
      .enterText("flavorDimensions(\"" + dimenName + "\")\n")
      .moveBetween(FLAVOR2 + " {", "")
      .enterText(dimen)
      .moveBetween(FLAVOR1 + " {", "")
      .enterText(dimen)
      .invokeAction(EditorFixture.EditorAction.SAVE)
      .close();

    ideFrameFixture
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Basic Activity")
      .getConfigureActivityStep()
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME, FIRST_ACTIVITY_NAME)
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT, "activity_f1_main")
      .selectLauncherActivity()
      .setTargetSourceSet(FLAVOR1)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      // Close layout editor to speed up the rest of the test. Too many editor components slow down lookups
      .getEditor()
      .close();

    ideFrameFixture
      .getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .openFromMenu(NewActivityWizardFixture::find, "File", "New", "Activity", "Basic Activity")
      .getConfigureActivityStep()
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.NAME, SECOND_ACTIVITY_NAME)
      .enterTextFieldValue(ConfigureBasicActivityStepFixture.ActivityTextField.LAYOUT, "activity_f2_main")
      .selectLauncherActivity()
      .setTargetSourceSet(FLAVOR2)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      // Close layout editor to speed up the rest of the test. Too many editor components slow down lookups
      .getEditor()
      .close();

    // Ensure that the new activity wizard actually gave us a launcher activity
    String flavor1Manifest = ideFrameFixture
      .getEditor()
      .open("app/src/flavor1/AndroidManifest.xml")
      .getCurrentFileContents();
    assertThat(flavor1Manifest).contains("android.intent.category.LAUNCHER");

    ideFrameFixture
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor1Debug");
    ideFrameFixture
      .runApp("app")
      .selectDevice("Google Nexus 5X")
      .clickOk();
    ideFrameFixture.getRunToolWindow().findContent("app")
      .waitForOutput(new PatternTextMatcher(Pattern.compile(
        ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", FIRST_ACTIVITY_NAME), Pattern.DOTALL)), 120);
    ideFrameFixture
      .getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);

    // Ensure that the new activity wizard actually gave us a launcher activity
    String flavor2Manifest = ideFrameFixture
      .getEditor()
      .open("app/src/flavor2/AndroidManifest.xml")
      .getCurrentFileContents();
    assertThat(flavor2Manifest).contains("android.intent.category.LAUNCHER");

    ideFrameFixture
      .getBuildVariantsWindow()
      .selectVariantForModule("app", "flavor2Debug");
    ideFrameFixture
      .runApp("app")
      .selectDevice("Google Nexus 5X")
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentWindow = ideFrameFixture.getRunToolWindow().findContent("app");

    String pattern = ACTIVITY_OUTPUT_PATTERN.replace("Main_Activity", SECOND_ACTIVITY_NAME);
    LOG.info("Starting search for " +  pattern + " at system time " + System.currentTimeMillis());
    contentWindow.waitForOutput(new PatternTextMatcher(Pattern.compile(pattern, Pattern.DOTALL)), 120);
    LOG.info("Ending search for " + pattern + " at system time " + System.currentTimeMillis());

    ideFrameFixture
      .getAndroidToolWindow()
      .selectDevicesTab()
      .selectProcess(PROCESS_NAME);
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }

  private class LogcatCommandHandler extends ShellCommandHandler {
    @NotNull public static final String COMMAND = "logcat";

    @Override
    public boolean invoke(@NotNull FakeAdbServer fakeAdbServer,
                          @NotNull Socket responseSocket,
                          @NotNull DeviceState device,
                          @Nullable String args) {
      try {
        OutputStream output = responseSocket.getOutputStream();

        if (args == null) {
          CommandHandler.writeFail(output);
          return false;
        }

        CommandHandler.writeOkay(output);

        String response;
        if (args.startsWith("--help")) {
          response = "epoch";
        } else {
          response = "";
        }

        CommandHandler.writeString(output, response);
      }
      catch (IOException ignored) {
        // Unable to write to socket. Can't communicate anything with client. Just swallow
        // the exception and move on
      }

      return false;
    }
  }
}
