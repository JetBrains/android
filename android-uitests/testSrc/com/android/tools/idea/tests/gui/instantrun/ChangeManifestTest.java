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
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.exception.WaitTimedOutError;
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
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ChangeManifestTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(9, TimeUnit.MINUTES);

  private FakeAdbServer fakeAdbServer;
  private CountingProcessStarter myProcessStarter;

  private static final String APP_NAME = "app";
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*Connected to process (\\d+) .*", Pattern.DOTALL);

  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    myProcessStarter = new CountingProcessStarter();

    FakeAdbServer.Builder adbBuilder = new FakeAdbServer.Builder();
    adbBuilder.installDefaultCommandHandlers()
      .setShellCommandHandler(ActivityManagerCommandHandler.COMMAND, () -> new ActivityManagerCommandHandler(myProcessStarter))
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
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void changeManifest() throws Exception {
    IdeFrameFixture ideFrameFixture;
    try {
      guiTest.importSimpleLocalApplication();
    } catch (WaitTimedOutError indexSyncTimeout) {
      // We really do not care about timeouts during project indexing and sync.
      GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(300));
    } finally {
      ideFrameFixture = guiTest.ideFrame();
    }

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(new StringTextMatcher("Google Nexus 5X"))
      .clickOk();

    ExecutionToolWindowFixture.ContentFixture contentFixture = ideFrameFixture.getRunToolWindow().findContent(APP_NAME);
    contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), TimeUnit.MINUTES.toSeconds(5));

    ideFrameFixture
      .getEditor()
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("", "<application")
      .pasteText("<uses-permission android:name=\"android.permission.INTERNET\" />\n");

    ActionButtonFixture applyChanges = ideFrameFixture.findApplyChangesButton();
    // Clear the console so the next window check doesn't confuse this run's outputs with the next
    // run's outputs
    //contentFixture.clickClearAllButton();
    try {
      applyChanges.click();
    } catch (IllegalStateException buttonNotEnabled) {
      throw new AssertionError("Apply changes button not enabled. Is Android process nonexistent?", buttonNotEnabled);
    }

    guiTest.waitForBackgroundTasks();

    try {
      // Verify that the IDE shows that the app has been restarted:
      contentFixture.waitForOutput(new PatternTextMatcher(RUN_OUTPUT), TimeUnit.MINUTES.toSeconds(5));
    } catch(WaitTimedOutError timeout) {
      // Check the num of start counts. Perhaps the listening logic for waiting for process to start has a race condition?
      throw new Exception("We started " + myProcessStarter.getStartCount() + " processes", timeout);
    }
    // (Cold swap) Verify that the app was started twice through ActivityManager
    assertThat(myProcessStarter.getStartCount()).isEqualTo(2);
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }

  /**
   * A thread-safe implementation of a ProcessHandler, since it's possible for ADB to receive multiple
   * am commands, all of which run on the different threads that access this shared object.
   */
  private static class CountingProcessStarter implements ActivityManagerCommandHandler.ProcessStarter {
    private final Object lock;
    private ClientState prevState;
    private int pid;

    private int procStartCounter;

    public CountingProcessStarter() {
      pid = 1234;
      procStartCounter = 0;
      lock = new Object();
    }

    @NotNull
    @Override
    public String startProcess(@NotNull DeviceState deviceState) {
      synchronized(lock) {
        if (prevState != null) {
          deviceState.stopClient(prevState.getPid());
        }
        prevState = deviceState.startClient(pid, 1235, "google.simpleapplication", false);
        pid++;
        procStartCounter++;
      }
      return "";
    }

    public int getStartCount() {
      synchronized (lock) {
        return procStartCounter;
      }
    }
  }
}
