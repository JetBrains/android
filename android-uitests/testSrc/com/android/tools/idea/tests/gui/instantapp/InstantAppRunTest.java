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
package com.android.tools.idea.tests.gui.instantapp;

import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.isActivityWindowOnTop;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.prepareAdbInstall;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.prepareAdbInstantAppLaunchIntent;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.waitForAppInstalled;

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.IDevice;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.EmulatorGenerator;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture.SystemImage;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class InstantAppRunTest {
  private static final SystemImage O_AVD_IMAGE = new SystemImage("Oreo", "26", "x86", "Android 8.0 (Google APIs)");

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule(false);

  /**
   * Verify imported instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 56be2a70-25a2-4b1f-9887-c19073874aa2
   *
   * <pre>
   *   Test steps:
   *   1. Import an instant app project.
   *   2. Set up an emulator running API 26.
   *   3. Run the instant app configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE)
  public void importAndRunInstantApp() throws Exception {
    String runConfigName = "topekabundle";
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("TopekaInstantApp");

    String avdName = EmulatorGenerator.ensureAvdIsCreated(
      ideFrame.invokeAvdManager(),
      new AvdSpec.Builder()
        .setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
        .setSystemImageSpec(O_AVD_IMAGE)
        .build()
    );

    ideFrame.runApp(runConfigName, avdName);

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);

    ExecutionToolWindowFixture.ContentFixture runWindow = ideFrame.getRunToolWindow().findContent(runConfigName);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), TimeUnit.MINUTES.toSeconds(2));

    runWindow.waitForStopClick();
  }

  /**
   * Verifies instant apps can be launched from the command line
   *
   * <p>TT ID: 769cb342-c858-43f7-91c1-f4117e4f7627
   *
   * <pre>
   *   Test steps:
   *   1. Create and start an emulator running API 26 or newer.
   *   2. Retrieve prebuilt instant APKs from test project directory
   *   3. Install prebuilt APKs
   *   4. Launch instant app by using an implicit intent through the command line
   *   Verify:
   *   1. Dump emulator window information using adb to check if the
   *      instant app's activity is on top.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/114304149, fast
  public void runFromCmdLine() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProject("TopekaInstantApp")
      .waitForGradleProjectSyncToFinish();

    String avdName = EmulatorGenerator.ensureAvdIsCreated(
      ideFrame.invokeAvdManager(),
      new AvdSpec.Builder()
        .setSystemImageGroup(AvdSpec.SystemImageGroups.X86)
        .setSystemImageSpec(O_AVD_IMAGE)
        .build()
    );

    AvdManagerDialogFixture avdManager = ideFrame.invokeAvdManager();
    avdManager.startAvdWithName(avdName);
    avdManager.close();

    // TODO: Move these adb commands over to DeviceQueries and AndroidDebugBridgeUtils
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    File adbBinary = new File(sdkHandler.getLocation(), FileUtil.join(SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER, SdkConstants.FN_ADB));
    File prebuiltApks = new File(ideFrame.getProjectPath(), "prebuilt");

    AndroidDebugBridge adb = AndroidDebugBridge.createBridge(adbBinary.getAbsolutePath(), false);
    Wait.seconds(120)
      .expecting("emulator to start")
      .until(() -> adb.getDevices().length > 0);

    IDevice[] devices = adb.getDevices();

    Wait.seconds(120)
      .expecting("emulator to finish booting")
      .until(() -> devices[0].isOnline() && devices[0].getProperty("dev.bootcomplete") != null);

    ProcessBuilder installCommand = prepareAdbInstall(adbBinary, prebuiltApks.listFiles());
    installCommand.inheritIO();
    installCommand.start().waitFor(10, TimeUnit.SECONDS);

    String expectedAppId = "com.google.samples.apps.topeka";
    waitForAppInstalled(devices[0], expectedAppId);

    ProcessBuilder launchCommand = prepareAdbInstantAppLaunchIntent(adbBinary);
    launchCommand.inheritIO();
    launchCommand.start().waitFor(10, TimeUnit.SECONDS);

    Wait.seconds(10)
      .expecting("instant app activity to be launched and shown")
      .until(() ->
        isActivityWindowOnTop(
          devices[0],
          expectedAppId + "/.activity.SignInActivity")
      );
  }
}
