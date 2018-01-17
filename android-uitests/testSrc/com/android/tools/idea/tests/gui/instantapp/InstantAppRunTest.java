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

import com.android.SdkConstants;
import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.CollectingOutputReceiver;
import com.android.ddmlib.IDevice;
import com.android.sdklib.repository.AndroidSdkHandler;
import com.android.tools.idea.explorer.adbimpl.AdbDeviceCapabilities;
import com.android.tools.idea.explorer.adbimpl.AdbFileOperations;
import com.android.tools.idea.npw.FormFactor;
import com.android.tools.idea.sdk.AndroidSdks;
import com.android.tools.idea.tests.gui.emulator.EmulatorTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ExecutionToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.ChooseSystemImageStepFixture.SystemImage;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.NewProjectWizardFixture;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import org.fest.swing.timing.Wait;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class InstantAppRunTest {
  private static final String O_AVD_NAME = "O dev under test";
  private static final SystemImage O_AVD_IMAGE = new SystemImage("Oreo", "26", "x86", "Android 8.0 (Google APIs)");

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final EmulatorTestRule emulator = new EmulatorTestRule();

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

    emulator.createAVD(
      ideFrame.invokeAvdManager(),
      "x86 Images",
      O_AVD_IMAGE,
      O_AVD_NAME
    );

    ideFrame.runApp(runConfigName)
      .selectDevice(O_AVD_NAME)
      .clickOk();

    Pattern CONNECTED_APP_PATTERN = Pattern.compile(".*Connected to process.*", Pattern.DOTALL);

    ExecutionToolWindowFixture.ContentFixture runWindow = ideFrame.getRunToolWindow().findContent(runConfigName);
    runWindow.waitForOutput(new PatternTextMatcher(CONNECTED_APP_PATTERN), TimeUnit.MINUTES.toSeconds(2));

    runWindow.waitForStopClick();
  }


  /**
   * Verify created instant apps can be deployed to an emulator running API 26 or newer.
   *
   * <p>TT ID: 84f8150d-0319-4e7e-b510-8227890aca3f
   *
   * <pre>
   *   Test steps:
   *   1. Create an instant app project.
   *   2. Set up an emulator running API 26.
   *   3. Run the instantapp run configuration.
   *   Verify:
   *   1. Check if the run tool window appears.
   *   2. Check if the "Connected to process" message appears in the run tool window.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.QA_UNRELIABLE) // b/70567643
  public void createAndRunInstantApp() throws Exception {
    String runConfigName = "instantapp";
    NewProjectWizardFixture newProj = guiTest.welcomeFrame().createNewProject();

    newProj.clickNext();
    newProj.getConfigureFormFactorStep()
      .selectMinimumSdkApi(FormFactor.MOBILE, "23")
      .findInstantAppCheckbox(FormFactor.MOBILE)
      .select();

    newProj.clickNext()
      .clickNext()
      .clickNext()
      .clickFinish();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    guiTest.waitForBackgroundTasks();

    emulator.createAVD(
      ideFrame.invokeAvdManager(),
      "x86 Images",
      O_AVD_IMAGE,
      O_AVD_NAME
    );

    // Stuff can be happening again in the background while the emulator is being created.
    // We should wait for it to finish.
    guiTest.waitForBackgroundTasks();

    ideFrame.runApp(runConfigName)
      .selectDevice(O_AVD_NAME)
      .clickOk();

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
  @RunIn(TestGroup.QA)
  public void runFromCmdLine() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("TopekaInstantApp");

    emulator.createAVD(
      ideFrame.invokeAvdManager(),
      "x86 Images",
      O_AVD_IMAGE,
      O_AVD_NAME
    );

    AvdManagerDialogFixture avdManager = ideFrame.invokeAvdManager();
    avdManager.startAvdWithName(O_AVD_NAME);
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

  @NotNull
  private ProcessBuilder prepareAdbInstall(@NotNull File adb, @NotNull File... apkFiles) {
    List<String> cmdLine = ContainerUtil.newArrayList();
    cmdLine.add(adb.getAbsolutePath());
    cmdLine.add("install-multiple");
    cmdLine.add("-t");
    cmdLine.add("-r");
    cmdLine.add("--ephemeral");
    for (File apk : apkFiles) {
      cmdLine.add(apk.getAbsolutePath());
    }
    return new ProcessBuilder(cmdLine);
  }

  private void waitForAppInstalled(@NotNull IDevice device, @NotNull String appId) {
    ExecutorService exec = Executors.newSingleThreadExecutor();
    AdbFileOperations adbOps = new AdbFileOperations(
      device,
      new AdbDeviceCapabilities(device),
      exec);
    try {
      Wait.seconds(10)
        .expecting("instant app to be listed from `pm packages list`")
        .until(() -> {

          List<String> packages = null;
          try {
            packages = adbOps.listPackages().get(10, TimeUnit.SECONDS);
          }
          catch (InterruptedException interrupt) {
            Thread.currentThread().interrupt();
            // Do nothing else. Let packages remain null.
          }
          catch (Exception otherExceptions) {
            // Do nothing. Let packages remain null.
          }

          if (packages == null) {
            return false;
          }

          for (String pkg : packages) {
            if (appId.equals(pkg)) {
              return true;
            }
          }

          return false;
        });
    } finally {
      exec.shutdown();
    }
  }

  @NotNull
  private ProcessBuilder prepareAdbInstantAppLaunchIntent(@NotNull File adb) {
    List<String> cmdLine = ContainerUtil.newArrayList();
    cmdLine.add(adb.getAbsolutePath());
    cmdLine.add("shell");
    cmdLine.add("am");
    cmdLine.add("start");
    cmdLine.add("-a");
    cmdLine.add("android.intent.action.VIEW");
    cmdLine.add("-c");
    cmdLine.add("android.intent.category.BROWSABLE");
    cmdLine.add("-d");
    cmdLine.add("http://topeka.samples.androidinstantapps.com/");
    return new ProcessBuilder(cmdLine);
  }

  private boolean isActivityWindowOnTop(@NotNull IDevice dev, @NotNull String activityComponentName) {
    Component expectedComp = Component.getComponentFromString(activityComponentName);

    CollectingOutputReceiver receiver = new CollectingOutputReceiver();
    try {
      dev.executeShellCommand("dumpsys activity activities", receiver, 30, TimeUnit.SECONDS);
    } catch (Exception cmdFailed) {
      return false;
    }

    String output = receiver.getOutput();
    String[] lines = output.split("\n");

    // The line containing "mResumedActivity" has information on the top activity
    Pattern resumedActivityMatcher = Pattern.compile("^mResumedActivity");
    for(String line : lines) {
      String trimmedLine = line.trim();
      Matcher m = resumedActivityMatcher.matcher(trimmedLine);
      if (m.find() && m.end() < trimmedLine.length()) {
        // Slice the string apart to extract the application ID and activity's full name
        String componentNameStr = parseComponentNameFromResumedActivityLine(trimmedLine);
        Component parsedComp = Component.getComponentFromString(componentNameStr);

        if (expectedComp.equals(parsedComp)) {
          return true;
        }
      }
    }

    // Scanned through all of the output without finding what we wanted
    return false;
  }

  /**
   * The output from dumpsys looks like
   *
   * <p>mResumedActivity: ActivityRecord{285ebc u0 com.android.settings/.CryptKeeper t1}
   *
   * <p>Given the above example, we want to parse the line and return "com.android.settings/.CryptKeeper"
   */
  @NotNull
  private String parseComponentNameFromResumedActivityLine(@NotNull String line) {
    String processedLine = line.trim();
    String[] lineTokens = processedLine.split("\\s+");
    if (lineTokens.length != 5) {
      throw new IllegalArgumentException("line does not split into 5 tokens. line = " + line);
    }

    return lineTokens[3];
  }

  private static class Component {
    @NotNull
    public static Component getComponentFromString(@NotNull String componentName) {
      String[] componentNameTokens = componentName.split("/");

      // Need exactly 2 tokens. Activity component names are of the form
      // {application ID}/{Activity class name}
      if (componentNameTokens.length != 2) {
        throw new IllegalArgumentException("Component names must be composed of two tokens separated by 1 '/'");
      }

      String appId = componentNameTokens[0];
      String activityClassName = componentNameTokens[1];

      // Expand the class name if the name is in the shortened version:
      if(activityClassName.startsWith(".")) {
        activityClassName = appId + activityClassName;
      }

      return new Component(appId, activityClassName);
    }

    private final String applicationId;
    private final String componentClassName;

    private Component(@NotNull String applicationId, @NotNull String componentClassName) {
      this.applicationId = applicationId;
      this.componentClassName = componentClassName;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof Component)) {
        return false;
      }

      if(other == null) {
        return false;
      }

      Component that = (Component) other;

      return applicationId.equals(that.applicationId) && componentClassName.equals(that.componentClassName);
    }

    @Override
    public int hashCode() {
      return applicationId.hashCode() + componentClassName.hashCode();
    }
  }
}
