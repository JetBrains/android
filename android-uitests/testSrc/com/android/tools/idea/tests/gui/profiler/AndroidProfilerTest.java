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
package com.android.tools.idea.tests.gui.profiler;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.idea.adb.AdbService;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidProfilerToolWindowFixture;
import com.google.common.truth.Correspondence;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.timing.Wait;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static com.android.fakeadbserver.DeviceState.HostConnectionType.USB;
import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class AndroidProfilerTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String PROJECT_NAME = "MinimalMinSdk26Apk";

  private static final String SERIAL = "test_device_001";
  private static final String MANUFACTURER = "Google";
  private static final String MODEL = "Alphabet Google Android Pixel Silver Really Red Edition";
  private static final String RELEASE = "9.0";
  private static final String SDK = "28";

  private FakeAdbServer myAdbServer;
  private AndroidDebugBridge myBridge;

  @Before
  public void before() throws Exception {
    // Build the server and configure it to use the default ADB command handlers.
    myAdbServer = new FakeAdbServer.Builder().installDefaultCommandHandlers().build();

    // Connect a test device to simulate device connection before server bring-up.
    myAdbServer.connectDevice(SERIAL, MANUFACTURER, MODEL, RELEASE, SDK, USB).get();

    // Start server execution.
    myAdbServer.start();

    // Start ADB with fake server and its port.
    AndroidDebugBridge.enableFakeAdbServerMode(myAdbServer.getPort());

    Project project = openProject(PROJECT_NAME);

    // Get the bridge synchronously, since we're in test mode.
    myBridge = AdbService.getInstance().getDebugBridge(AndroidSdkUtils.getAdb(project)).get();

    // Wait for ADB.
    Wait.seconds(10).expecting("Android Debug Bridge to connect").until(() -> myBridge.isConnected());
    Wait.seconds(5).expecting("Initial device list is available").until(() -> myBridge.hasInitialDeviceList());

    // Make sure that ADB is reporting back the one and only device.
    assertThat(myBridge.getDevices()).asList().comparingElementsUsing(new Correspondence<IDevice, String>() {
      @Override
      public boolean compare(IDevice actual, String expected) {
        return SERIAL.equals(actual.getName());
      }

      @Override
      public String toString() {
        return "Compares the serial of the actual device to the given serial";
      }
    }).containsExactly(SERIAL);
  }

  @NotNull
  private Project openProject(@NotNull String projectDirName) throws Exception {
    File projectDir = guiTest.copyProjectBeforeOpening(projectDirName);
    VirtualFile fileToSelect = VfsUtil.findFileByIoFile(projectDir, true);

    ProjectManager.getInstance().loadAndOpenProject(fileToSelect.getPath());

    Wait.seconds(10).expecting("project to be open").until(() -> ProjectManager.getInstance().getOpenProjects().length == 1);

    Project project = ProjectManager.getInstance().getOpenProjects()[0];
    GuiTests.waitForProjectIndexingToFinish(project);
    return project;
  }

  @After
  public void after() throws Exception {
    if (myAdbServer != null) {
      myAdbServer.stop().get();
    }
    AndroidDebugBridge.terminate();
  }

  @Test
  public void openToolWindow() {
    guiTest.ideFrame().openFromMenu(AndroidProfilerToolWindowFixture::find, "View", "Tool Windows", "Profiler");
  }
}
