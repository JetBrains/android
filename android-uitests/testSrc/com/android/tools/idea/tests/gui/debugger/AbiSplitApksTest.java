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
package com.android.tools.idea.tests.gui.debugger;

import static org.junit.Assert.assertTrue;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AbiSplitApksTest extends DebuggerTestBase {

  private static final int GRADLE_SYNC_TIMEOUT_SECONDS = 90;

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES).settingNdkPath();

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setupFakeAdbServer() throws Exception {

    fakeAdbServer = new FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .build();

    DeviceState device = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "8.1",
      "31",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    device.getProperties().put("ro.product.cpu.abilist", "x86_64");
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
   *   3. Start a native debugging session in Android Studio (deploy in emulator X86_64).
   *   4. Now hit the stop button.
   *   4. Go the folder ~<project folder="">/app/build/intermediates/instant-run-apk/debug and check
   *      the apk generated (Verify 1, 2).
   *   Verify:
   *   1. APK generated should not be universal (You can verify this by trying to install the apk
   *      in a non X86_64 emulator or device)
   *   2. APK generated should explicitly for the ABI X86_64
   *   </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void testX64AbiSplitApks() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("debugger/BasicCmakeAppForUI", Wait.seconds(GRADLE_SYNC_TIMEOUT_SECONDS));

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

    ideFrame.requestProjectSyncAndWaitForSyncToFinish(Wait.seconds(GRADLE_SYNC_TIMEOUT_SECONDS));

    String expectedApkName = "app-x86_64-debug.apk";
    // Request debugging and wait for Gradle build to finish.
    assertTrue("Build failed", ideFrame.actAndWaitForBuildToFinish(it -> it.debugApp("app", "Google Nexus 5X")).isBuildSuccessful());

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
      fakeAdbServer.awaitServerTermination(120, TimeUnit.SECONDS);
    } catch (InterruptedException ignored) {
    }
  }
}
