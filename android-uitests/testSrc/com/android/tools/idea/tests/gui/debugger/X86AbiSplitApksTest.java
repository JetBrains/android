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
package com.android.tools.idea.tests.gui.debugger;

import static org.junit.Assert.assertTrue;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class X86AbiSplitApksTest extends DebuggerTestBase {

  private static final int TIMEOUT_SECONDS = 120;

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES).settingNdkPath();

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setupFakeAdbServer() throws Exception {

    fakeAdbServer = new FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      // This test needs to query the device for ABIs, so we need some expanded functionality for the
      // getprop command handler:
      .addDeviceHandler(new GetAbiListPropCommandHandler(Arrays.asList("x86")))
      .build();

    DeviceState device = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "8.1",
      "31",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    device.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);
    device.setActivityManager((args, serviceOutput) -> {
      if ("start".equals(args.get(0))) {
        device.startClient(1234, 1235, "com.example.basiccmakeapp", false);
        serviceOutput.writeStdout("Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]"
                                  + " cmp=com.example.basiccmakeapp/com.example.basiccmakeapp.MainActivity }");
      }
    });
    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  /**
   * Verifies ABI split apks are generated as per the target emulator/device during a native
   * debug session.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 6b2878da-4464-4c32-be85-dd20a2f1bff2
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import BasicCmakeAppForUI.
   *   2. Enable split by adding the following to app/build.gradle: android.splits.abi.enable true.
   *   3. Start a native debugging session in Android Studio (deploy in emulator using x86 architecture).
   *   4. Now hit the stop button.
   *   4. Go the folder ~<project folder="">/app/build/intermediates/apk and check
   *      the apk generated (Verify 1, 2).
   *   Verify:
   *   1. APK generated should not be universal (You can verify this by trying to install the apk
   *      in a non X86 emulator or device)
   *   2. APK generated should explicitly for the ABI X86
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void x86AbiSplitApks() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/BasicCmakeAppForUI", Wait.seconds(TIMEOUT_SECONDS));

    DebuggerTestUtil.setDebuggerType(ideFrame, DebuggerTestUtil.NATIVE);

    ideFrame.getEditor()
      .open("app/build.gradle", EditorFixture.Tab.EDITOR)
      .moveBetween("apply plugin: 'com.android.application'", "")
      .enterText("\n\nandroid.splits.abi.enable true")
      .invokeAction(EditorFixture.EditorAction.SAVE);

    // ndk.dir will be deprecated soon.
    // Remove it now in test. When ndk.dir is deprecated, will update the test if necessary.
    ideFrame.getEditor().open("local.properties")
      .select("(ndk.dir=.*\n)")
      .enterText("#");

    ideFrame.requestProjectSyncAndWaitForSyncToFinish(Wait.seconds(TIMEOUT_SECONDS));

    String expectedApkName = "app-x86-debug.apk";

    // Request debugging and wait for build to complete.
    assertTrue("Build failed", ideFrame.actAndWaitForBuildToFinish(Wait.seconds(TIMEOUT_SECONDS), it ->
      it.debugApp("app", "Google Nexus 5X")).isBuildSuccessful());

    // TODO: Handle the case when app installation failed: "Application Installation Failed" dialog shows up.
    // Currently, cannot reproduce this issue locally to get the screenshot with the "Application Installation Failed" dialog shows up.

    File projectRoot = ideFrame.getProjectPath();
    File expectedPathOfApk = new File(projectRoot, "app/build/intermediates/apk/debug/" + expectedApkName);
    Wait.seconds(30).expecting("Apk file to be generated.")
      .until(() -> expectedPathOfApk.exists());
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();

    try {
      fakeAdbServer.awaitServerTermination(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }
  }
}
