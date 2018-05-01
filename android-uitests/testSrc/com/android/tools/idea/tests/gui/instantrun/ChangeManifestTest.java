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
package com.android.tools.idea.tests.gui.instantrun;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.ClientState;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.fest.swing.util.StringTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import static com.android.tools.idea.tests.gui.instantrun.InstantRunTestUtility.extractPidFromOutput;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ChangeManifestTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private FakeAdbServer fakeAdbServer;

  private static final String APP_NAME = "app";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);
  private static final int OUTPUT_RESET_TIMEOUT = 30;

  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    ActivityManagerCommandHandler.ProcessStarter startCmdHandler = new ActivityManagerCommandHandler.ProcessStarter() {
      private ClientState prevState;
      private int pidCount = 1234;

      @NotNull
      @Override
      public String startProcess(@NotNull DeviceState deviceState) {
        if (prevState != null) {
          deviceState.stopClient(prevState.getPid());
        }

        prevState = deviceState.startClient(pidCount, 1235, "google.simpleapplication", false);
        pidCount++;

        return "";
      }
    };

    FakeAdbServer.Builder adbBuilder = new FakeAdbServer.Builder();
    adbBuilder.installDefaultCommandHandlers()
      .setShellCommandHandler(ActivityManagerCommandHandler.COMMAND, () -> new ActivityManagerCommandHandler(startCmdHandler))
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
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleLocalApplication();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(new StringTextMatcher("Google Nexus 5X"))
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String output = contentFixture.getOutput(10);
    String pid = extractPidFromOutput(contentFixture.getOutput(10), RUN_OUTPUT);

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("", "<application")
      .pasteText("<uses-permission android:name=\"android.permission.INTERNET\" />\n");

    ideFrameFixture
      .findApplyChangesButton()
      .click();

    // Studio takes a few seconds to reset Run tool window contents.
    Wait.seconds(OUTPUT_RESET_TIMEOUT)
        .expecting("Run tool window output has been reset")
        .until(() -> !contentFixture.getOutput(10).contains(output));
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
    String newPid = extractPidFromOutput(contentFixture.getOutput(10), RUN_OUTPUT);
    // (Cold swap) Verify the inequality of PIDs before and after IR
    assertThat(pid).isNotEqualTo(newPid);
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }
}
