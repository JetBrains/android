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
package com.android.tools.idea.tests.gui.instantapp;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(GuiTestRemoteRunner.class)
public class AddInstantModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that user is able to add a instant app module through the
   * new module wizard.
   *
   * <p>TT ID: 6da70326-4b89-4f9b-9e08-573939bebfe5
   *
   * <pre>
   *   Test steps:
   *   1. Import simple local application project
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new instant module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new instant module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void addInstantModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importSimpleLocalApplication();

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleWizardFixture.find(ideFrame)
      .chooseModuleType("Instant App")
      .clickNextToStep("Configure your module")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("instantapp");
  }
}
