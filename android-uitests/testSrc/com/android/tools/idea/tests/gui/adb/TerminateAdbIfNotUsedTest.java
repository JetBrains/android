/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.adb;


import static com.android.fakeadbserver.DeviceState.HostConnectionType.USB;
import static com.android.tools.deployer.devices.FakeDevice.MANUFACTURER;
import static com.android.tools.deployer.devices.FakeDevice.MODEL;
import static com.google.common.truth.Truth.assertThat;
import static org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.MavenVersionSelectorScheme.RELEASE;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class TerminateAdbIfNotUsedTest {
  private static final String PROJECT_NAME = "simple";
  private static final String SERIAL = "test_device_001";
  private static final String SDK = "28";
  private static final int WAIT_TIME = 30;

  @Rule public final GuiTestRule myGuiTest = new GuiTestRule();
  private FakeAdbServer myAdbServer;
  private AndroidDebugBridge myBridge;

  @After
  public void shutDown() throws InterruptedException {
    if (myBridge != null) {
      Wait.seconds(WAIT_TIME).expecting("Android Debug Bridge is disconnected").until(() -> !myBridge.isConnected());
    }

    if (myAdbServer != null) {
      boolean status = myAdbServer.awaitServerTermination(WAIT_TIME, TimeUnit.SECONDS);
      assertThat(status).isTrue();
    }

    AdbService.getInstance().dispose();
    AndroidDebugBridge.disableFakeAdbServerMode();
  }

  @Test
  public void testSingleProject() throws Exception {
    // Start a new server process --> Open an Android Project --> Close this project.
    // ADB is expected to be terminated when last Android Project is being disposed.
    buildAdbService();
    Project project = myGuiTest.openProject(PROJECT_NAME);
    startAdb(project);
    myGuiTest.ideFrame().closeProject();
    Wait.seconds(WAIT_TIME)
      .expecting("Project to be closed")
      .until(() -> ProjectManagerEx.getInstanceEx().getOpenProjects().length == 0);
  }

  private void buildAdbService() throws IOException, ExecutionException, InterruptedException {
    // Build the server and configure it to use the default ADB command handlers.
    myAdbServer = new FakeAdbServer.Builder().installDefaultCommandHandlers().build();

    // Connect a test device to simulate device connection before server bring-up.
    myAdbServer.connectDevice(SERIAL, MANUFACTURER, MODEL, RELEASE, SDK, USB).get();

    // Start server execution.
    myAdbServer.start();

    // Terminate the service if it's already started (it's a UI test, so there might be no shutdown between tests).
    AdbService.getInstance().dispose();

    // Start ADB with fake server and its port.
    AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());
  }

  private void startAdb(Project project) throws ExecutionException, InterruptedException {
    // Get the bridge synchronously, since we're in test mode.
    myBridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(project)).get();

    // Wait for ADB.
    Wait.seconds(WAIT_TIME).expecting("Android Debug Bridge to connect").until(() -> myBridge.isConnected());
  }
}
