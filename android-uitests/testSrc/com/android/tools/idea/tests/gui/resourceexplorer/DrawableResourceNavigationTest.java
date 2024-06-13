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
package com.android.tools.idea.tests.gui.resourceexplorer;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceDetailViewFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class DrawableResourceNavigationTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  private IdeFrameFixture myIdeFrameFixture;

  @Before
  public void setUp() throws Exception {
    myIdeFrameFixture =  guiTest
      .importProjectAndWaitForProjectSyncToFinish("DrawableResource");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * To verify user can navigate drawable resources in resource manager
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 0c283faa-5829-43f8-8f8f-41ef483a68ea
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Launch Android Studio.
   *   2. Open an existing project, DrawableResource
   *   3. Open the Resource Manager (View > Tool Windows > Resource Manager).
   *   4. Click on the "Drawable" tab (Verify 1)
   *   5. Double click on the icon_category_entertainment (Verify 2)
   *   6. Double click on "default" (Verify 3)
   *   7. Click on the left arrow "<-" (Verify 4)
   *   Verify:
   *   1. A drawable named icon_category_entertainment should be showing
   *   2. Should show all the different icon with named icon_category_entertainment
   *   3. A new editor with the image "icon_category_entertainment.xml" should open
   *   4. The view from step 4 should appear
   *   </pre>
   * <p>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void testDrawableResource() throws IOException {
    //Open Resource Manager
    myIdeFrameFixture.invokeMenuPath("Tools", "Resource Manager");
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    ResourceExplorerFixture myResourceExplorerFixture = ResourceExplorerFixture.find(guiTest.robot());

    //Go to Drawable tab in the Resource Manager
    int selectedTabIndex = myResourceExplorerFixture.tabbedPane().target().getSelectedIndex();
    assertThat(myResourceExplorerFixture.tabbedPane().target().getTitleAt(selectedTabIndex)).matches("Drawable");
    myResourceExplorerFixture.click();
    myResourceExplorerFixture.clickRefreshButton();
    myIdeFrameFixture.waitUntilProgressBarNotDisplayed();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Verify 1 : A drawable named icon_category_entertainment should be showing
    myResourceExplorerFixture.focus();
    myResourceExplorerFixture.selectResource("icon_category_entertainment");
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Verify 2: Should show all the different icon with named icon_category_entertainment
    ResourceDetailViewFixture myResourceDetailViewFixture = ResourceDetailViewFixture.find(guiTest.robot());
    assertThat(myResourceDetailViewFixture.getResourcesCount()).isEqualTo(3);

    //Verify 3: A new editor with the image ""icon_category_entertainment.xml"" should open
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
    waitForXMLFileToShow(myIdeFrameFixture.getEditor());
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    // Verify 4 : A drawable named icon_category_entertainment should be showing
    myResourceDetailViewFixture.goBackToResourceList().click();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    myResourceExplorerFixture.findResource("icon_category_entertainment");
  }

  public static void waitForXMLFileToShow(@NotNull EditorFixture editor) {
    Wait.seconds(10)
      .expecting("icon_category_entertainment.xml file to open")
      .until(() -> "icon_category_entertainment.xml".equals(editor.getCurrentFileName()));
  }
}
