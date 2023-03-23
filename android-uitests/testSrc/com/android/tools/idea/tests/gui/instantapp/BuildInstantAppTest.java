/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditConfigurationsDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EnableInstantAppSupportDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class BuildInstantAppTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  /**
   * Verify imported instant apps can be built without error.
   *
   * <p>TT ID: 56be2a70-25a2-4b1f-9887-c19073874aa2
   *
   * <pre>
   *   Test steps:
   *   1. Import an sample app project
   *   2. Refactor > Enable Instant app support > Select app module > OK
   *   3. After gradle sync, Click the Run configuration drop-down,
   *      choose "Edit Configuration" and Enable checkbox for "Deploy as instant app"
   *   Verify:
   *   1. Build to success
   * </pre>
   */
  @Test
  @RunIn(TestGroup.FAST_BAZEL)
  public void buildInstantApp() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(180));

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .invokeMenuPath("Refactor", "Enable Instant Apps Support...");

    ideFrame.actAndWaitForGradleProjectSyncToFinish(
      it -> EnableInstantAppSupportDialogFixture.find(ideFrame).clickOk());

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();

    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrame.robot())
      .selectDeployAsInstantApp(true)
      .clickOk();
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
  }
}
