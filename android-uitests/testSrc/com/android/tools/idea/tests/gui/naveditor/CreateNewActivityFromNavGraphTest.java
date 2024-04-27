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
package com.android.tools.idea.tests.gui.naveditor;

import static com.android.tools.idea.wizard.template.Language.Java;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertTrue;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNewActivityFromNavGraphTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  protected static final String BASIC_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION;

  private static String NavFilePath = "app/src/main/res/navigation/nav_graph.xml";


  private EditorFixture myEditorFixture;
  private NlEditorFixture editorFixture;
  @Before
  public void setUp() throws Exception {
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    WizardUtils.createNewProject(guiTest, BASIC_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Java);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/res/navigation/nav_graph.xml"
    );
    myEditorFixture = ideFrame.getEditor();
  }

  /**
   * Verifies Creating Navigation Graph
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 43f7b908-5748-411e-b0b3-8b72fa6368f0
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project with 'Basic Views Activity' activity
   *   2. Under the navigation resource directory, open the nav_graph.xml file.
   *   3. Click on the split view icon to display both Text and Design panels.
   *   4. Within the Design panel, click on the New Destination icon to create a new activity.
   *   5. Select 'activity_main' Activity.
   *
   *   Verify:
   *   1. Should see a new activity appear in the Design panel.
   *   2. Should see new activity xml appear in Text panel.
   *   3. Within the Attributes panel, the following sections should be shown:
   *      Summary line (activity icon, id name, 'activity'),
   *      'id' property,
   *      'label' property,
   *      'name' property,
   *      'Activity' list (containing properties: action, data, dataPattern),
   *      'Arguments' list,
   *      'Deep Links' list.
   *   </pre>
   */
  @Test
  public void createNewActivityFromNavGraphTest() {

    //Loading the navigation layout Design View
    editorFixture = myEditorFixture.open(NavFilePath, EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForSurfaceToLoad()
      .waitForRenderToFinish();

    myEditorFixture.selectEditorTab(EditorFixture.Tab.SPLIT);

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.canInteractWithSurface()).isTrue();

    // Create a new destination
    createNewDestination("activity_main");
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Verify the newly added destination is updated in the XML file.
    String layoutText = myEditorFixture.getCurrentFileContents();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(layoutText).containsMatch("activity_main");

    myEditorFixture.select("(@layout/activity_main)"); // Select newly created activity in 'Code' tab
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    //Asserting label in default section
    List<String> mainSectionsNames = editorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("mainActivity").listAllLabels();
    assertTrue(mainSectionsNames.contains("id"));
    assertTrue(mainSectionsNames.contains("label"));
    assertTrue(mainSectionsNames.contains("name"));

    assertTrue(mainSectionsNames.contains("action"));
    assertTrue(mainSectionsNames.contains("data"));
    assertTrue(mainSectionsNames.contains("dataPattern"));

    //Asserting scrollable section header names
    List<String> scrollableSectionsNames = editorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("mainActivity").listSectionNames();
    assertTrue(scrollableSectionsNames.contains("Activity"));
    assertTrue(scrollableSectionsNames.contains("Arguments"));
    assertTrue(scrollableSectionsNames.contains("Deep Links"));
  }

  private void createNewDestination(String destinationName){
    editorFixture
      .waitForRenderToFinish()
      .getNavSurface()
      .openAddDestinationMenu()
      .waitForContents()
      .selectDestination(destinationName);
    guiTest.robot().pressAndReleaseKey(KeyEvent.VK_ENTER);
  }
}
