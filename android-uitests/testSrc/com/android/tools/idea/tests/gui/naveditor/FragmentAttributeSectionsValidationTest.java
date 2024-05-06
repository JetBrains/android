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

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertTrue;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.gradle.project.build.BuildStatus;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class FragmentAttributeSectionsValidationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);

  protected static final String BASIC_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = SdkVersionInfo.RECOMMENDED_MIN_SDK_VERSION;

  private static String NAVGRAPH_FILE_PATH = "app/src/main/res/navigation/nav_graph.xml";

  private IdeFrameFixture ideFrame;
  private EditorFixture myEditorFixture;
  private NlEditorFixture myNlEditorFixture;
  @Before
  public void setUp() throws Exception {
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    WizardUtils.createNewProject(guiTest, BASIC_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getProjectView().assertFilesExist(NAVGRAPH_FILE_PATH);
    myEditorFixture = ideFrame.getEditor();

    myEditorFixture.open(NAVGRAPH_FILE_PATH, EditorFixture.Tab.DESIGN);
    BuildStatus result = ideFrame.invokeProjectMake(Wait.seconds(240));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    TestCase.assertTrue(result.isBuildSuccessful());
  }

  /**
   * Verifies Attributes are displayed after selecting Fragments from design and Code section
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 914b3683-cfa7-4128-834c-994a550d8634
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a project with 'Basic Views Activity' activity
   *   2. Under the navigation resource directory, open the nav_graph.xml file.
   *   3. Click on the split view icon to display both Text and Design panels.
   *   4. Click on FirstFragment fragment in design view (Verify 1)
   *   5. Click on the fragment xml tag in code View "android:id="@ied/SecondFragment />" (Verify 1)
   *
   *   Verify:
   *   1. Within the Attributes panel, the following sections should be shown:
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
  public void fragmentAttributeVerificationTest() {
    ideFrame.requestFocusIfLost();
    //Loading the navigation layout from Design View
    myNlEditorFixture  = myEditorFixture
      .getLayoutEditor()
      .waitForSurfaceToLoad()
      .waitForRenderToFinish();

    myEditorFixture.selectEditorTab(EditorFixture.Tab.SPLIT);

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(myNlEditorFixture.canInteractWithSurface()).isTrue();

    //Select 'FirstFragment'
    ((NavDesignSurfaceFixture)myNlEditorFixture.getSurface()).findDestination("FirstFragment").click();

    guiTest.waitForAllBackgroundTasksToBeCompleted();
    //Asserting label in default section
    List<String> mainSectionsNames = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("FirstFragment").listAllLabels();
    assertTrue(mainSectionsNames.contains("id"));
    assertTrue(mainSectionsNames.contains("label"));
    assertTrue(mainSectionsNames.contains("name"));

    //Asserting scrollable section header names
    List<String> scrollableSectionsNames = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("FirstFragment").listSectionNames();
    assertTrue(scrollableSectionsNames.contains("Actions"));
    assertTrue(scrollableSectionsNames.contains("Arguments"));
    assertTrue(scrollableSectionsNames.contains("Deep Links"));

    //Select SecondFragment from the editor and verify Attributes panel is updated
    myEditorFixture.select("(@layout/fragment_second)"); // Select newly created activity in 'Code' tab
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    //Asserting label in default section
    List<String> mainSectionsNamesFromEditor = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("SecondFragment").listAllLabels();
    assertTrue(mainSectionsNamesFromEditor.contains("id"));
    assertTrue(mainSectionsNamesFromEditor.contains("label"));
    assertTrue(mainSectionsNamesFromEditor.contains("name"));

    //Asserting scrollable section header names
    List<String> scrollableSectionsNamesFromEditor = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("SecondFragment").listSectionNames();
    assertTrue(scrollableSectionsNamesFromEditor.contains("Actions"));
    assertTrue(scrollableSectionsNamesFromEditor.contains("Arguments"));
    assertTrue(scrollableSectionsNamesFromEditor.contains("Deep Links"));
  }
}
