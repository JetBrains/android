/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.cpp;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.device.FormFactor;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectCodeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.InspectionsFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class WarningsCheckInNewCPPProjectTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  FormFactor selectMobileTab = FormFactor.MOBILE;

  IdeFrameFixture myIdeFrameFixture;
  EditorFixture myEditorFixture;
  private static final String NativeLibFilePath = "app/src/main/cpp/native-lib.cpp";

  @Before
  public void setup() {
    WizardUtils.createCppProject(guiTest, selectMobileTab, "Native C++", Language.Java);
    GuiTests.waitForProjectIndexingToFinish(guiTest.ideFrame().getProject());
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture = guiTest.ideFrame();
    myEditorFixture = myIdeFrameFixture.getEditor();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /*
   * This test was added as part of b/285205714
   * <pre>
   *   Test Steps:
   *   1.Creates a new default CPP project
   *   2.Initiate a variable in cpp file
   *   3.Inspect code (Code -> Inspect Code -> Click on Analyze
   *   Verification:
   *   1. "Local variable 'x' is only assigned but never accessed" warning is displayed
   * </pre>
   */
  @Test
  public void testNoWarningsInNewCPPProjects() {
    myEditorFixture.open(NativeLibFilePath)
      .waitForFileToActivate();

    myEditorFixture.moveBetween("\"Hello from C++\";", "")
      .moveBetween("\"Hello from C++\";", "") //To reduce flakiness
      .pressAndReleaseKeys(KeyEvent.VK_ENTER)
      .typeText("int x = 0;\n");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture.requestFocusIfLost();
    InspectionsFixture myInspections = myIdeFrameFixture.openFromMenu(InspectCodeDialogFixture::find, "Code", "Inspect Code...")
      .clickAnalyze();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myIdeFrameFixture.waitUntilProgressBarNotDisplayed();

    GuiTests.takeScreenshot(myIdeFrameFixture.robot(), "Problems panel");
    myIdeFrameFixture.requestFocusIfLost();
    String inspectionResults = myInspections.getResults(); //get inspections results from the problem panel
    assertThat((inspectionResults).contains("Local variable 'x' is only assigned but never accessed")).isTrue();
    assertThat((inspectionResults).contains("The value is never used")).isTrue();
  }
}