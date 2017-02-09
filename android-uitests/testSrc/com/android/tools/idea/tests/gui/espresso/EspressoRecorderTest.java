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
package com.android.tools.idea.tests.gui.espresso;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.util.PatternTextMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(GuiTestRunner.class)
public class EspressoRecorderTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "MyActivityTest";
  private static final String TEST_RECORDER_APP = "TestRecorderapp";
  private static final Pattern DEBUG_OUTPUT =
    Pattern.compile(".*google\\.simpleapplication\\.test.*Connecting to google\\.simpleapplication.*", Pattern.DOTALL);
  private static final Pattern RUN_OUTPUT =
    Pattern.compile(".*adb shell am instrument.*google\\.simpleapplication\\.MyActivityTest.*Tests ran to completion.*", Pattern.DOTALL);
  private static final String AVD_NAME = "device under test";

  @Before
  public void setUp() throws Exception {
    MockAvdManagerConnection.inject();
    getEmulatorConnection().deleteAvdByDisplayName(AVD_NAME);
  }

  @After
  public void tearDown() throws Exception {
    // Close a no-window emulator by calling 'adb emu kill'
    // because default stopAVD implementation (i.e., 'kill pid') cannot close a no-window emulator.
    getEmulatorConnection().stopRunningAvd();
    getEmulatorConnection().deleteAvdByDisplayName(AVD_NAME);
  }

  /**
   * To verify espresso adds dependencies after recording in new project
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14581575
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Import SimpleApplication
   *   2. Run | Record Espresso Test
   *   3. Wait for recording dialog and click OK
   *   5. Wait for test class name input dialog and click OK
   *   6. Click yes to add missing Espresso dependencies
   *   7. Run test
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void addDependencyOnFly() throws Exception {
    guiTest.importSimpleApplication();
    createAVD();

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .invokeMenuPath("Run", "Record Espresso Test");
    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(AVD_NAME)
      .clickOk();

    ideFrameFixture.getDebugToolWindow().findContent(TEST_RECORDER_APP).waitForOutput(new PatternTextMatcher(DEBUG_OUTPUT), 120);

    RecordingDialogFixture.find(guiTest.robot()).clickOk();
    TestClassNameInputDialogFixture.find(guiTest.robot()).clickOk();
    MessagesFixture.findByTitle(guiTest.robot(), "Missing Espresso dependencies").clickYes();

    // Run Android test.
    ideFrameFixture.waitForGradleProjectSyncToFinish()
      .invokeMenuPath("Run", "Run...");
    new JListFixture(guiTest.robot(), GuiTests.waitForPopup(guiTest.robot())).clickItem("Wrapper[MyActivityTest]");
    DeployTargetPickerDialogFixture.find(guiTest.robot())
      .selectDevice(AVD_NAME)
      .clickOk();

    // Wait until tests run completion.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(RUN_OUTPUT), 120);
  }

  private void createAVD() {
    AvdManagerDialogFixture avdManagerDialog = guiTest.ideFrame().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware()
      .selectHardwareProfile("Nexus 5");
    avdEditWizard.clickNext();

    avdEditWizard.getChooseSystemImageStep()
      .selectTab("x86 Images")
      .selectSystemImage("Nougat", "24", "x86", "Android 7.0");
    avdEditWizard.clickNext();

    avdEditWizard.getConfigureAvdOptionsStep()
      .setAvdName(AVD_NAME)
      .selectGraphicsSoftware();
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  @NotNull
  private static MockAvdManagerConnection getEmulatorConnection() {
    return (MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection();
  }

}
