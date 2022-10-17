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
package com.android.tools.idea.tests.gui.uibuilder;


import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;


import static com.google.common.truth.Truth.assertThat;


@RunWith(GuiTestRemoteRunner.class)
public class RunAppEnableTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty View Activity";

  @Rule
  public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verifies that a project can be deployed on an emulator
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID:
   * Bug: https://b.corp.google.com/issues/232427565
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create Empty Activity project (verify 1)
   *   2. Update build.gradle to have abiFilter with x86_64 (verify 1)
   *   3. Update build.gradle to have abiFilter with X86_64 (verify 1)
   *   Verify:
   *   Run App button is present
   *   </pre>
   */

  @Before
  public void setUp() throws Exception {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE); // Default projects are created with androidx dependencies
    guiTest.robot().waitForIdle();
  }

  @Test
  public void runOnEmulator() throws Exception {

    IdeFrameFixture ideFrameFixture = guiTest.ideFrame();
    assertThat(ideFrameFixture.findRunApplicationButton().isEnabled()).isTrue();

    ideFrameFixture.getEditor()
      .open("app/build.gradle")
      .moveBetween("defaultConfig {\n", "")
      .enterText("ndk {\nabiFilters 'arm64-v8a', 'x86_64'\n}\n")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now");
    assertThat(ideFrameFixture.findRunApplicationButton().isEnabled()).isTrue();

    ideFrameFixture.getEditor()
      .open("app/build.gradle")
      .moveBetween("ndk {\n", "")
      .invokeAction(EditorFixture.EditorAction.DELETE_LINE)
      .enterText("abiFilters 'arm64-v8a', 'X86_64'\n")
      .awaitNotification(
        "Gradle files have changed since last project sync. A project sync may be necessary for the IDE to work properly.")
      .performAction("Sync Now");
    assertThat(ideFrameFixture.findRunApplicationButton().isEnabled()).isTrue();
  }

}
