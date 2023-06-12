/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class AddInstantModuleTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Verify a feature module can be created within a project.
   *
   * <p>TT ID: d239df75-a7fc-4327-a5af-d6b2f6caba11
   *
   * <pre>
   *   Test steps:
   *   1. Import a project with min API level at least 23
   *   2. Click app module, click Refactor > Enable Instant app support > Select app module > OK
   *      Verify 1
   *   3. Select Instant Dynamic Feature Module > Next > Finish, verify 2
   *   4. Build the app, verify 3
   *   Verify:
   *   1. Open app manifest and check for
   *      xmlns:dist="http://schemas.android.com/apk/distribution"
   *      dist:module dist:instant="true"
   *   2. Verify dynamic module is created and manifest file has below code:
   *      dist:instant="true"
   *      dist:fusing dist:include="false"
   *   3. The project build to success
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void addInstantModuleTest() throws Exception {
    IdeFrameFixture ideFrame =
      guiTest.importProjectAndWaitForProjectSyncToFinish("MinSdk24App", Wait.seconds(180));

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath("app")
      .invokeMenuPath("Refactor", "Enable Instant Apps Support...");

    ideFrame.actAndWaitForGradleProjectSyncToFinish(
      it -> EnableInstantAppSupportDialogFixture.find(ideFrame).clickOk());

    String fileText = guiTest.getProjectFileText("app/src/main/AndroidManifest.xml");
    assertThat(fileText.contains("xmlns:dist=\"http://schemas.android.com/apk/distribution\"")).isTrue();
    assertThat(fileText.contains("dist:module dist:instant=\"true\"")).isTrue();

    // TODO: double check with tester whether it needs it or not.
    // The project is not deployed as an instant app by default anymore.
    // Enable deploying the project as an instant app.
    ideFrame.invokeMenuPath("Run", "Edit Configurations...");
    EditConfigurationsDialogFixture.find(ideFrame.robot())
      .selectDeployAsInstantApp(true)
      .clickOk();

    ideFrame.getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromContextualMenu(NewModuleWizardFixture::find, "New", "Module")
      .clickNextToInstantDynamicFeature()
      .wizard()
      .clickFinishAndWaitForSyncToFinish();

    fileText = guiTest.getProjectFileText("app/dynamicfeature/src/main/AndroidManifest.xml");
    assertThat(fileText).contains("dist:instant=\"true\"");
    assertThat(fileText).contains("dist:fusing dist:include=\"false\"");

    ideFrame.focus();
    Wait.seconds(5)
      .expecting("IDE Frame to be active again")
      .until(() ->
               GuiQuery.getNonNull(() -> ideFrame.target().hasFocus())
      );
    ideFrame.invokeAndWaitForBuildAction("Build", "Make Project");
  }
}
