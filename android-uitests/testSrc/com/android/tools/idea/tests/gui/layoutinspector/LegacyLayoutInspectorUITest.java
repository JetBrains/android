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
package com.android.tools.idea.tests.gui.layoutinspector;

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.fakeadbserver.devicecommandhandlers.JdwpCommandHandler;
import com.android.fakeadbserver.shellcommandhandlers.ActivityManagerCommandHandler;
import com.android.tools.idea.editors.layoutInspector.actions.LayoutInspectorDebugStubber;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.AndroidProcessChooserDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeSettingsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RunToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.inspector.LegacyLayoutInspectorFixture;
import com.intellij.openapi.project.DumbService;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class LegacyLayoutInspectorUITest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  private FakeAdbServer fakeAdbServer;

  @Before
  public void setupFakeAdbServer() throws Exception {
    ActivityManagerCommandHandler.ProcessStarter startCmdHandler = new ActivityManagerCommandHandler.ProcessStarter() {
      @NotNull
      @Override
      public String startProcess(@NotNull DeviceState deviceState) {
        deviceState.startClient(1234, 1235, "google.simpleapplication", false);
        return "Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER]"
               + " cmp=google.simpleapplication/google.simpleapplication.MyActivity }";
      }
    };
    fakeAdbServer = new FakeAdbServer.Builder()
      .installDefaultCommandHandlers()
      .addDeviceHandler(new ActivityManagerCommandHandler(startCmdHandler))
      .addDeviceHandler(new JdwpCommandHandler())
      .build();

    DeviceState dev = fakeAdbServer.connectDevice(
      "test_device",
      "Google",
      "Nexus 5X",
      "9.0",
      "28",
      DeviceState.HostConnectionType.LOCAL
    ).get();
    dev.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  /**
   * Verify layout inspector is a full replacement for the hierarchy viewer
   *
   * <p>TT ID: 65743195-bcf9-4127-8f4a-b60fde2b269e
   *
   * <pre>
   *   Test steps:
   *   1. Create a new project.
   *   2. Open the layout inspector by following Tools > Layout Inspector from the menu.
   *   3. Select the process running this project's application.
   *   4. Retrieve the layout's elements from the process.
   *   Verify:
   *   1. Ensure that the layout's elements contain the expected elements, which include
   *      a RelativeLayout, a TextView, and a FrameLayout.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void launchLayoutInspectorViaChooser() throws Exception {
    String appConfigName = "app";
    IdeFrameFixture ideFrame = guiTest.importSimpleApplication();
    enableDynamicLayoutInspector(ideFrame, false);

    try {
      File layoutDumpDir = guiTest.copyProjectBeforeOpening("LayoutInspector");

      new LayoutInspectorDebugStubber().mockOutDebugger(
        ideFrame.getProject(),
        new File(layoutDumpDir, "recorded_layout_inspector.dump"),
        new File(layoutDumpDir, "recorded_layout_inspector.png")
      );

      ideFrame.runApp(appConfigName, "Google Nexus 5X", Wait.seconds(240));

      // The following includes a wait for the run tool window to appear.
      // Also show the run tool window in case of failure so we have more information.
      RunToolWindowFixture runWindow = ideFrame.getRunToolWindow();
      runWindow.activate();

      // TODO remove workaround due to http://b/126957955
      Wait.seconds(200)
        .expecting("Dumb mode to end")
        .until(() -> !GuiQuery.get(() -> DumbService.isDumb(ideFrame.getProject())));
      // TODO end workaround for http://b/126957955

      guiTest.ideFrame().waitAndInvokeMenuPath("Tools", "Legacy Layout Inspector");
      // easier to select via index rather than by path string which changes depending on the api version
      AndroidProcessChooserDialogFixture.find(guiTest.robot()).selectProcess().clickOk();
      List<String> layoutElements = new LegacyLayoutInspectorFixture(guiTest.robot()).getLayoutElements();
      checkLayout(layoutElements);
    }
    finally {
      enableDynamicLayoutInspector(ideFrame, true);
    }
  }

  private static void enableDynamicLayoutInspector(@NotNull IdeFrameFixture ideFrame, boolean enable) {
    IdeSettingsDialogFixture settingsDialog = ideFrame.openIdeSettings().selectExperimentalPage();
    JCheckBoxFixture dynamicLayoutInspector = settingsDialog.findCheckBox("Enable Live Layout Inspector");
    dynamicLayoutInspector.setSelected(enable);
    settingsDialog.clickOK();
  }

  private static void checkLayout(List<String> layoutElements) {
    assertThat(layoutElements).contains("android.widget.RelativeLayout");
    assertThat(layoutElements).contains("android.widget.TextView");
    assertThat(layoutElements).contains("android.widget.FrameLayout");
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }
}
