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

import static com.android.tools.idea.tests.gui.npw.NewCppProjectTestUtil.createNewProjectWithCpp;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.npw.CppStandardType;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewProjectWithCpp1Test {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setupFakeAdbServer() throws IOException, InterruptedException, ExecutionException {
    FakeAdbServer.Builder adbBuilder = new FakeAdbServer.Builder();
    adbBuilder.installDefaultCommandHandlers();

    fakeAdbServer = adbBuilder.build();
    DeviceState fakeDevice = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "9.0",
      "28",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    fakeDevice.setActivityManager((args, serviceOutput) -> {
      if ("start".equals(args.get(0))) {
        fakeDevice.startClient(1234, 1235, "com.example.myapplication", false);
        serviceOutput.writeStdout("Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]");
      }
    });
    fakeDevice.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  /**
   * Verify creating a new project from default template.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ede43cef-f4a1-484b-9b1b-58000c4ba17c
   * <p>
   *   <pre>
   *   Steps:
   *   1. Create a new project; check the box "Include C++ Support"
   *   2. Click next until you get to the window for "Customize C++ Support" don't check any boxes
   *   3. Click Finish
   *   4. Run
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void createNewProjectWithCpp1() throws Exception {
    createNewProjectWithCpp(CppStandardType.DEFAULT, guiTest);
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }
}
