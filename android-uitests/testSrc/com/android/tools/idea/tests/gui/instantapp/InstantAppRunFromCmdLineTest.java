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
package com.android.tools.idea.tests.gui.instantapp;

import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.firstDevice;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.isActivityWindowOnTop;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.isOnline;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.prepareAdbInstall;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.prepareAdbInstantAppLaunchIntent;
import static com.android.tools.idea.tests.gui.instantapp.InstantAppRunTestHelpersKt.waitForAppInstalled;

import com.android.SdkConstants;
import com.android.adblib.AdbSession;
import com.android.adblib.ConnectedDevice;
import com.android.adblib.tools.AdbLibSessionFactoryKt;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.sdk.IdeSdks;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.emulator.AvdSpec;
import com.android.tools.idea.tests.gui.framework.emulator.AvdTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class InstantAppRunFromCmdLineTest {
  private final GuiTestRule guiTest = new GuiTestRule().withTimeout(10, TimeUnit.MINUTES);

  private final AvdTestRule avdRule = AvdTestRule.Companion.buildAvdTestRule(() -> {
    // TODO avoid API 24 due to b/92112542
    AvdSpec.Builder builder = new AvdSpec.Builder();
    builder.setSystemImageSpec(
      new ChooseSystemImageStepFixture.SystemImage("Pie", "28", "x86", "Android 9.0 (Google APIs)")
    );

    return builder;
  });

  @Rule public final RuleChain emulatorRules = RuleChain
    .outerRule(avdRule)
    .around(guiTest);

  /**
   * The SDK used for this test requires the emulator and the system images to be
   * available. The emulator and system images are not available in the prebuilts
   * SDK. The AvdTestRule should generate such an SDK for us, but we need to set
   * the generated SDK as the SDK to use for our test.
   *
   * Unfortunately, GuiTestRule can overwrite the SDK we set in AvdTestRule, so
   * we need to set this in a place after GuiTestRule has been applied.
   */
  @Before
  public void setupSpecialSdk() {
    GuiTask.execute(() -> {
      ApplicationManager.getApplication().runWriteAction(() -> {
        IdeSdks.getInstance().setAndroidSdkPath(avdRule.getGeneratedSdkLocation(), null);
      });
    });
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
  @RunIn(TestGroup.FAST_BAZEL)
  public void runFromCmdLine() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("TopekaInstantApp", Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));
    GuiTests.waitForBackgroundTasks(guiTest.robot(), Wait.seconds(TimeUnit.MINUTES.toSeconds(5)));

    // TODO: Move these adb commands over to DeviceQueries and AndroidDebugBridgeUtils
    AndroidSdkHandler sdkHandler = AndroidSdks.getInstance().tryToChooseSdkHandler();
    String adbBinary = sdkHandler.getLocation().resolve(SdkConstants.OS_SDK_PLATFORM_TOOLS_FOLDER).resolve(SdkConstants.FN_ADB).toString();
    File prebuiltApks = new File(guiTest.ideFrame().getProjectPath(), "prebuilt");

    AdbSession session = AdbLibSessionFactoryKt.createStandaloneSession();

    Wait.seconds(120)
      .expecting("emulator to start")
      .until(() -> firstDevice(session) != null);

    ConnectedDevice device = firstDevice(session);

    Wait.seconds(120)
      .expecting("emulator to finish booting")
      .until(() -> isOnline(device));

    ProcessBuilder installCommand = prepareAdbInstall(adbBinary, prebuiltApks.listFiles());
    installCommand.inheritIO();
    installCommand.start().waitFor(10, TimeUnit.SECONDS);

    String expectedAppId = "com.google.samples.apps.topeka";
    waitForAppInstalled(device, expectedAppId);

    ProcessBuilder launchCommand = prepareAdbInstantAppLaunchIntent(adbBinary);
    launchCommand.inheritIO();
    launchCommand.start().waitFor(10, TimeUnit.SECONDS);

    Wait.seconds(10)
      .expecting("instant app activity to be launched and shown")
      .until(() ->
               isActivityWindowOnTop(
                 device,
                 expectedAppId + "/.activity.SignInActivity")
      );
  }
}
