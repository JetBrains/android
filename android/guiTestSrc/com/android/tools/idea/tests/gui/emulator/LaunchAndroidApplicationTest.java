/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator;

import com.android.tools.idea.avdmanager.AvdManagerConnection;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdEditWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.AvdManagerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.avdmanager.MockAvdManagerConnection;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import org.fest.swing.util.PatternTextMatcher;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.regex.Pattern;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertTrue;

@RunWith(GuiTestRunner.class)
public class LaunchAndroidApplicationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  private static final String APP_NAME = "app";
  private static final String PROCESS_NAME = "google.simpleapplication";
  private static final Pattern LOCAL_PATH_OUTPUT = Pattern.compile(
    ".*adb shell am start .*google\\.simpleapplication.+Connected to process.*", Pattern.DOTALL);
  private static final String AVD_NAME = "device under test";

  @Before
  public void setUp() throws Exception {
    MockAvdManagerConnection.inject();
    AvdManagerDialogFixture avdManagerDialog = guiTest.importSimpleApplication().invokeAvdManager();
    AvdEditWizardFixture avdEditWizard = avdManagerDialog.createNew();

    avdEditWizard.selectHardware()
      .selectHardwareProfile("Nexus 5");
    avdEditWizard.clickNext();

    avdEditWizard.getChooseSystemImageStep()
      .selectTab("x86 Images")
      .selectSystemImage("KitKat", "19", "x86", "Android 4.4");
    avdEditWizard.clickNext();

    avdEditWizard.getConfigureAvdOptionsStep()
      .setAvdName(AVD_NAME);
    avdEditWizard.clickFinish();
    avdManagerDialog.close();
  }

  @After
  public void tearDown() throws Exception {
    // Close a no-window emulator by calling 'adb emu kill'
    // because default stopAVD implementation (i.e., 'kill pid') cannot close a no-window emulator.
    ((MockAvdManagerConnection)AvdManagerConnection.getDefaultAvdManagerConnection()).stopRunningAvd();

    guiTest.ideFrame().invokeAvdManager().deleteAvd(AVD_NAME).close();
  }

  @Ignore("http://b.android.com/216396")
  @Test
  public void testRunOnEmulator() throws IOException, ClassNotFoundException {
    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();

    ideFrameFixture
      .runApp(APP_NAME)
      .selectDevice(AVD_NAME)
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    ideFrameFixture.getRunToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);

    ideFrameFixture.getAndroidToolWindow().selectDevicesTab().selectProcess(PROCESS_NAME).clickTerminateApplication();
  }

  @Ignore("failed in http://go/aj/job/studio-ui-test/389 and from IDEA")
  @Test
  public void testDebugOnEmulator() throws IOException, ClassNotFoundException, EvaluateException {
    guiTest.importSimpleApplication();
    String contents = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/java/google/simpleapplication/MyActivity.java", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    assertThat(contents).contains("setContentView(R.layout.activity_my);");

    guiTest.ideFrame()
      .debugApp(APP_NAME)
      .selectDevice("Nexus7")
      .clickOk();

    // Make sure the right app is being used. This also serves as the sync point for the package to get uploaded to the device/emulator.
    guiTest.ideFrame().getDebugToolWindow().findContent(APP_NAME).waitForOutput(new PatternTextMatcher(LOCAL_PATH_OUTPUT), 120);

    guiTest.ideFrame().getAndroidToolWindow().selectDevicesTab()
                                       .selectProcess(PROCESS_NAME)
                                       .clickTerminateApplication();
  }
}
