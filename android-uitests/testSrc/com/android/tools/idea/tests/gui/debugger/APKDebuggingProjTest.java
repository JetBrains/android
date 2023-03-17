/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.fakeadbserver.DeviceState;
import com.android.fakeadbserver.FakeAdbServer;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DebugToolWindowFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.matcher.DialogMatcher;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.finder.WindowFinder;
import org.fest.swing.fixture.DialogFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.Nullable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class APKDebuggingProjTest {
  /*
   * This is a rather long test. A lot of waiting is done in the background waiting
   * for file refreshes. This test needs the huge timeout, even though it does not use
   * the emulator.
   */
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private FakeAdbServer fakeAdbServer;
  private static final int WAIT_TIME = 30;

  @Before
  public void removingExistingApkProjects() throws Exception {
    /*
    An ~/ApkProjects directory will show us a dialog in the middle of the test
    to overwrite the directory. Delete the directory now, so it won't trip the test.
     */
    CreateAPKProjectTestUtil.removeApkProjectsDirectory();
  }

  public void setupFakeAdbServer() throws Exception {
    //Setup fake ADB server.
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
        fakeDevice.startClient(1234, 1235, "com.google.apkdebugging", false);
        serviceOutput.writeStdout("Debugger attached to process 1234");
      }
    });
    fakeDevice.setDeviceStatus(DeviceState.DeviceStatus.ONLINE);

    fakeAdbServer.start();
    AndroidDebugBridge.enableFakeAdbServerMode(fakeAdbServer.getPort());
  }

  @After
  public void shutdownFakeAdb() throws Exception {
    AndroidDebugBridge.terminate();
    AndroidDebugBridge.disableFakeAdbServerMode();
    fakeAdbServer.close();
  }

  /**
   * To Verify apk debugging with simple c++ project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: c6b1a798-0d0a-474f-abca-03343bb72a17
   * <p>
   *   <pre>
   *   Procedure:
   *   Steps for dev-app
   *   1. Create a Non-kotlin c++ project say ApkDebugging
   *   2. After grade sync do > Build > Make project or Build > Build Bundle/APK(s) > Build APK's (Verify 1)
   *   Test Steps:
   *   1. From any Studio window Select File > Profile APK Debugging
   *   2. Select api created at below location
   *      ApkDebugging/app/build/outputs/apk/debug/app-debug.apk.
   *   3. Select create new folder if studio asks
   *   4. If studio asks choose open project in new window (Verify 2)
   *   5. Select "Add debug Symbols" > Click on "Add"
   *   6. Select symbols from below location
   *      ApkDebugging/app/build/intermediates/cmake/debug/obj > OK (Verify 3)
   *   7. From Android view browse cpp > libnative-lib > Users > <Path_to_project> > app > src > main > cpp >native-lib.cpp. Open cpp file (Verify 4)
   *   8. Place breakpoint at return statement in native-lib.cpp
   *   9. Debug (Verify 5)
   *   10. Continue (F9) (Verify 6)
   *   Verify:
   *   1. Debug apk created at ApkDebugging/app/build/outputs/apk/debug/app-debug.apk location.
   *   2. app-debug project opens in new window with a Yellow banner says "Add debug symbols"
   *   3. Symbols are added for four ABI's without any errors
   *   4. You should able to open cpp file in readable format
   *   5. Breakpoint hits and string hello as value "Hello from C++"
   *   6. Debugger should continue app loads up on device/emulator
   *   </pre>
   */

  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void testApkDebuggingOnCPlusPlusProject() throws Exception {
    File projectRoot = buildAPKLocally();

    //Setting up fake ADB
    setupFakeAdbServer();

    profileOrDebugApk(guiTest.welcomeFrame(), new File(projectRoot, "app/build/outputs/apk/debug/app-debug.apk"));

    //Handle the import dialog box if it pops in any case.
    handleApkImportDialogBox();

    IdeFrameFixture myIdeFrameFixture = guiTest.ideFrame();
    EditorFixture myEditorFixture = myIdeFrameFixture.getEditor();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture.clearNotificationsPresentOnIdeFrame();

    //Add debug symbols.
    File debugSymbols = new File(projectRoot, "app/build/intermediates/merged_native_libs/debug/out");
    myEditorFixture.open("lib/x86/libapkdebugging.so")
      .getLibrarySymbolsFixture()
      .addDebugSymbols(debugSymbols);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //myEditorFixture.open("app/src/main/cpp/native-lib.cpp");
    //Opening native-lib.cpp file and placing debugger.
    openNativeLibCpp(myIdeFrameFixture);
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myEditorFixture.moveBetween("ret", "urn");
    myEditorFixture.invokeAction(EditorFixture.EditorAction.TOGGLE_LINE_BREAKPOINT);

    //Debugging the app using fake-adb
    myIdeFrameFixture.findDebugApplicationButton().click();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Due to the limitations of fake ADB and emulator, verifying if,
    //the debug tool window is opened and the window is displaying the app.
    DebugToolWindowFixture debugWindow = myIdeFrameFixture.getDebugToolWindow();
    assertThat(debugWindow.getDebuggerContent("app-debug").isSelected()).isTrue();
    assertThat(debugWindow.getDebuggerContent("app-debug").getDisplayName()).isEqualTo("app-debug");

    //Stopping the app before closing the project
    myIdeFrameFixture.invokeMenuPath("Run", "Stop \'app-debug\'");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  private File buildAPKLocally() throws Exception {
    //IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish(apkProjectToImport);
    WizardUtils.createNativeCPlusPlusProject(guiTest, "ApkDebugging", "com.google.apkdebugging", 30, Language.Java);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();

    ideFrame.clearNotificationsPresentOnIdeFrame();

    ideFrame.invokeAndWaitForBuildAction("Build", "Build Bundle(s) / APK(s)", "Build APK(s)");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    File projectRoot = ideFrame.getProjectPath();

    /*
    We will have another window opened for the APK project. Close this window so,
    we don't have to manage two windows.
      */
    ideFrame.closeProject();
    Wait.seconds(WAIT_TIME)
      .expecting("Project to be closed")
      .until(() -> ProjectManagerEx.getInstanceEx().getOpenProjects().length == 0);
    return projectRoot;
  }

  private void profileOrDebugApk(WelcomeFrameFixture welcomeFrame, File apk) throws Exception {
    /*
    Opening the APK profiling/debugging dialog can set the Modality to a state where
    VfsUtil.findFileByIoFile blocks us indefinitely.
    Retrieve VirtualFile before we open the dialog:
     */
    @Nullable
    VirtualFile apkFile = VfsUtil.findFileByIoFile(apk, true);

    // NOTE: This step generates the ~/ApkProjects/app-x86-debug directory.
    welcomeFrame.profileOrDebugApk(apk)
      .select(apkFile)
      .clickOkAndWaitToClose();
  }

  private void openNativeLibCpp(IdeFrameFixture ideFrame) throws Exception {
    //Note: Opening "native-lib.cpp" file using project view.
    ideFrame.getProjectView()
      .selectProjectPane()
      .clickPath("app-debug", "cpp", "native-lib.cpp");
    ideFrame.robot().pressAndReleaseKeys(KeyEvent.VK_ENTER);

    Wait.seconds(10)
      .expecting("Opening native-lib.cpp file.")
      .until(() -> ideFrame.getEditor()
        .getCurrentFileName()
        .equalsIgnoreCase("native-lib.cpp")
      );
  }

  private void handleApkImportDialogBox() throws Exception {
    DialogFixture downloadDialog;
    try {
      downloadDialog = WindowFinder
        .findDialog(DialogMatcher.withTitle("APK Import"))
        .withTimeout(1, TimeUnit.SECONDS)
        .using(guiTest.robot());
    }
    catch (Exception e) {
      downloadDialog = null;
    }
    if (downloadDialog != null) {
      JButtonFixture useExistFolder = downloadDialog.button(JButtonMatcher.withText("Use existing folder"));
      Wait.seconds(120).expecting("Android source to be installed").until(useExistFolder::isEnabled);
      useExistFolder.click();
    }
  }

  private void handleProjectTermination() throws Exception {
    DialogFixture projectTermination;
    try {
      projectTermination = WindowFinder
        .findDialog(DialogMatcher.withTitle("Process \'app-debug\' Is Running"))
        .withTimeout(1, TimeUnit.SECONDS)
        .using(guiTest.robot());
    }
    catch (Exception e) {
      projectTermination = null;
    }
    if (projectTermination != null) {
      JButtonFixture terminateButton = projectTermination.button(JButtonMatcher.withText("Terminate"));
      Wait.seconds(120).expecting("Closing the project").until(terminateButton::isEnabled);
      terminateButton.click();
    }
  }
}
