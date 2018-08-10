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
import com.android.tools.idea.tests.gui.framework.fixture.ProjectViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(GuiTestRemoteRunner.class)
public class AddFeatureModuleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verifies that user is able to add a Instant App Feature module through the
   * new module wizard.
   *
   * <p>TT ID: d239df75-a7fc-4327-a5af-d6b2f6caba11
   *
   * <pre>
   *   Test steps:
   *   1. Import instant local application project (Created by Studio 3.3)
   *   2. Go to File -> New module to open the new module dialog wizard.
   *   3. Follow through the wizard to add a new Instant App Feature module, accepting defaults.
   *   4. Complete the wizard and wait for the build to complete.
   *   Verify:
   *   1. The new Instant App Feature module's library is shown in the project explorer pane.
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void addFeatureModule() throws Exception {
    IdeFrameFixture ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("InstantLocalApplication");

    ideFrame.invokeMenuPath("File", "New", "New Module...");

    NewModuleWizardFixture newModDialog = NewModuleWizardFixture.find(ideFrame);

    newModDialog.chooseModuleType("Instant App Feature Module")
      .clickNextToStep("Creates a new Android Instant App Feature module.")
      .clickNextToStep("Add an Activity to Mobile")
      .clickNextToStep("Configure Activity")
      .clickFinish();

    ideFrame.waitForGradleProjectSyncToFinish();

    ProjectViewFixture.PaneFixture androidPane = ideFrame.getProjectView().selectAndroidPane();
    // In InstantLocalApplication project, feature exists. Additional default name will be feature2.
    androidPane.clickPath("feature2");
  }
}
