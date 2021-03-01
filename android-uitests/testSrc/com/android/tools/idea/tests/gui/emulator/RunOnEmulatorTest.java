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
package com.android.tools.idea.tests.gui.emulator;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.CommandHandler;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.SimpleShellHandler;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class RunOnEmulatorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private FakeAdbServer fakeAdbServer;

  private static final String APP_NAME = "app";
  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*adb shell am start .*google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);


  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    ActivityManagerCommandHandler.ProcessStarter procStarter = new ActivityManagerCommandHandler.ProcessStarter() {
      @NotNull
      @Override
      public String startProcess(@NotNull DeviceState deviceState) {
        deviceState.startClient(1234, 1235, "google.simpleapplication", false);

        return "Starting: Intent { act=android.intent.action.VIEW cat=[android.intent.category.LAUNCHER] }";
      }
    };

    FakeAdbServer.Builder fakeAdbBuilder = new FakeAdbServer.Builder();
    fakeAdbBuilder.installDefaultCommandHandlers()
      .addDeviceHandler(new ActivityManagerCommandHandler(procStarter))
      .addDeviceHandler(new LsCommandHandler())
      .addDeviceHandler(new JdwpCommandHandler());

    fakeAdbServer = fakeAdbBuilder.build();

    DeviceState fakeDevice = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "9.0",
      "28",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    fakeDevice.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);
    fakeAdbServer.start();

    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

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
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void runOnEmulator() throws Exception {
    // Wait 2 minutes for import instead of the usual 1 minute, since projects are taking longer to import and initialize now.
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(2 * 60));

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture.runApp(APP_NAME, "Google Nexus 5X");

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);

    ideFrameFixture.getAndroidLogcatToolWindow().show().selectProcess(PROCESS_NAME);
    ideFrameFixture.stopApp();
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }

  private static class LsCommandHandler extends SimpleShellHandler {

    public LsCommandHandler() {
      super("ls");
    }

    @Override
    public void execute(@NotNull FakeAdbServer fakeAdbServer, @NotNull Socket responseSocket, @NotNull DeviceState device, @Nullable String args) {
      try {
        OutputStream output = responseSocket.getOutputStream();

        if (args == null) {
          CommandHandler.writeFail(output);
          return;
        }

        CommandHandler.writeOkay(output);

        String[] filepaths = args.split("\\s+");

        // Treat as if nothing is available
        StringBuilder respBuilder = new StringBuilder();
        for (String filepath : filepaths) {
          respBuilder.append("ls: ").append(filepath).append(": No Such file or directory\n");
        }

        CommandHandler.writeString(output, respBuilder.toString());
      }
      catch (IOException ignored) {
        // Unable to send anything back to client. Can't do anything, so swallow the exception
        // and continue on...
      }
    }
  }
}
