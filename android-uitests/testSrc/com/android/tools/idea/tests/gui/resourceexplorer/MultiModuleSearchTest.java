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
package com.android.tools.idea.tests.gui.resourceexplorer;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class MultiModuleSearchTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  private static String RESOURCE_MATCH_IN_LIBRARY = "1 resource found in 'MultiAndroidModule.library.main'";

  /**
   * To verify search results displays resources correctly while searching in resource manager
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ebd8b557-17fa-4785-be2a-131e172d53f3
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Launch Android Studio.
   *   2. Open an existing project, select the IO Schedule 2019 app (iosched-master).
   *          To improve the execution time to load the app, replaced the large iosched-master
   *          app with a lighter MultiAndroidModule app import.
   *   3. Open the Resource Manager (View > Tool Windows > Resource Manager).
   *   4. In the Search Box, partially type the name of the resource from the other module (i.e. "event").(Verify 1)
   *   5. Click on the text link at the bottom of the resources list. (Verify 2)
   *   Verify:
   *   1. A text link should appear at the bottom of the resources list with the text "1 resource found in 'module_name'."
   *   2. The Resource manager should show the resource from the other module, without changing the selected
   *      resource type (e.g. "Drawable"). The Module combo box should display the name of the other module (e.g. "shared").
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testMultiModuleSearch() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("MultiAndroidModule");

    // Testcase failing on buildbot but passes locally. Adding an extra step "make project" to make sure the
    // project is not only imported and synced, but also complied and build.
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful()).isTrue();
    GuiTests.takeScreenshot(guiTest.robot(), "VerifyProjectLoaded");

    guiTest.ideFrame().invokeMenuPath("Tools", "Resource Manager");
    ResourceExplorerFixture resourceExplorerFixture = ResourceExplorerFixture.find(guiTest.robot());
    GuiTests.takeScreenshot(guiTest.robot(), "VerifyResourceManagerLoaded");
    assertThat(resourceExplorerFixture.comboBox().selectedItem()).containsMatch("app.main");
    int selectedTabIndex = resourceExplorerFixture.tabbedPane().target().getSelectedIndex();
    assertThat(resourceExplorerFixture.tabbedPane().target().getTitleAt(selectedTabIndex)).matches("Drawable");
    resourceExplorerFixture.click();

    // Verify 1 : Verify Text link with asserts found in the other module.
    resourceExplorerFixture.getSearchField().enterText("event");
    GuiTests.takeScreenshot(guiTest.robot(), "VerifyLinkForResourceInOtherModule");
    resourceExplorerFixture.click();
    // Add extra step to refresh files system to remove the flakiness on RBE instances in go/ab
    GuiTests.refreshFiles();
    Wait.seconds(30).expecting("Wait for search link to show up").until(() ->
      resourceExplorerFixture.findLinklabelByTextContains(RESOURCE_MATCH_IN_LIBRARY).isEnabled()
      );
    GuiTests.takeScreenshot(guiTest.robot(), "VerifyResourceInOtherModuleDisplayed");
    resourceExplorerFixture.findLinklabelByTextContains(RESOURCE_MATCH_IN_LIBRARY)
      .clickLink();

    // Verify 2: Verify clicking on the link update the module in the dropdown.
    // Verify 2: Verify assert shows up in the resource manager. Drawable tab is still selected.
    assertThat(resourceExplorerFixture.comboBox().selectedItem()).containsMatch("library.main");
    resourceExplorerFixture.click();
    Wait.seconds(30).expecting("wait for event resource to show up").until(() ->
      resourceExplorerFixture.findResource("event").index() >=0 );

    assertThat(resourceExplorerFixture.tabbedPane().target().getTitleAt(selectedTabIndex)).matches("Drawable");
  }
}

