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
package com.android.tools.idea.tests.gui.naveditor;

import com.android.sdklib.SdkVersionInfo;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertTrue;

@RunWith(GuiTestRemoteRunner.class)
public class NavGraphSanityTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  protected static final String BASIC_ACTIVITY_TEMPLATE = "Basic Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = SdkVersionInfo.HIGHEST_SUPPORTED_API;

  NlEditorFixture myNlEditorFixture;
  private EditorFixture myEditorFixture;

  @Before
  public void setUp() {
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    WizardUtils.createNewProject(guiTest, BASIC_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Kotlin);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    IdeFrameFixture ideFrame = guiTest.ideFrame();
    ideFrame.clearNotificationsPresentOnIdeFrame();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/res/navigation/nav_graph.xml"
    );

    myEditorFixture = ideFrame.getEditor();

    myEditorFixture.open("app/src/main/res/navigation/nav_graph.xml", EditorFixture.Tab.DESIGN);
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myNlEditorFixture = myEditorFixture
      .getLayoutEditor()
      .waitForSurfaceToLoad();

    assertThat(myNlEditorFixture.canInteractWithSurface()).isTrue();
  }

  /**
   * Verifies Sections display when Root navigation is selected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 5eff210e-711d-49f7-9760-01f325c83826
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project using the Basic Views Activity template.
   *   2. Under the navigation resource directory, open the nav_graph.xml file.
   *
   *   Verify:
   *   1. Within the Attributes panel, the following sections should be shown:
   *      Summary line (graph icon, id name, 'navigation'),
   *      'id' property,
   *      'label' property,
   *      'startDestination' property,
   *      'Argument Default Values' list,
   *      'Global Actions' list,
   *      'Deep Links' list.
   *   </pre>
   *
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void verifyAttibutePanel(){
    myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel()
      .waitForId("nav_graph")
      .findSectionByName("navigation");

    //Asserting label in default section
    List<String> mainSectionsNames = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("nav_graph").listAllLabels();
    assertTrue(mainSectionsNames.contains("id"));
    assertTrue(mainSectionsNames.contains("label"));
    assertTrue(mainSectionsNames.contains("startDestination"));

    //Asserting scrollable section header names
    List<String> scrollableSectionsNames = myNlEditorFixture.waitForRenderToFinish()
      .getAttributesPanel().waitForId("nav_graph").listSectionNames();
    assertTrue(scrollableSectionsNames.contains("Argument Default Values"));
    assertTrue(scrollableSectionsNames.contains("Global Actions"));
    assertTrue(scrollableSectionsNames.contains("Deep Links"));
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * Verifies Sections display when Root navigation is selected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: f9ccf2e2-0cec-46c6-88bb-defb9267f1d3
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project using the Basic Views Activity template.
   *   2. Under the navigation resource directory, open the nav_graph.xml file.
   *   3. Click the "Design" mode button from the top-right corner (right most button).
   *
   *   Verify:
   *   1. Zoom and Pan controls located on the bottom right.
   *   </pre>
   *   Note:
   *   As Split View cannot be accessed as of today, Zoom functionality is validated only in design view.
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void verifyZoomPanButtons() {
    myNlEditorFixture.waitForRenderToFinish();

    double originalScale = myNlEditorFixture.getScale();
    myNlEditorFixture.zoomIn();
    myNlEditorFixture.zoomIn(); //Doing it twice to reduce flakiness
    double zoomInScale = myNlEditorFixture.getScale();
    myNlEditorFixture.zoomOut();
    myNlEditorFixture.zoomOut(); //Doing it twice to reduce flakiness
    double zoomOutScale = myNlEditorFixture.getScale();

    System.out.println("***** Original scale: " + originalScale);
    System.out.println("***** Scale after Zoom In click: " + zoomInScale);
    System.out.println("***** Scale after Zoom Out click: " + zoomOutScale);

    assertThat(zoomInScale).isGreaterThan(originalScale);
    assertThat(zoomOutScale).isLessThan(zoomInScale);

    assertTrue(myNlEditorFixture.panButtonPresent());
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }

  /**
   * Verifies Sections display when Root navigation is selected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 1485c170-c1cf-477c-beee-6730637f432a
   * TT ID: b2fd3688-1b9b-4cb6-98b7-af928019b526
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a new project using the Basic Views Activity template.
   *   2. Under the navigation resource directory, open the nav_graph.xml file.
   *   3. Click the "Design" mode button from the top-right corner (right most button).
   *   4. Use the keyboard keys (Verify 1)
   *      Zoom in : Ctrl + Plus or  Cmd + Plus for mac
   *      Zoom Out: Ctrl + Minus or  Cmd + Minus for mac
   *      Zoom to 100% : Ctrl + . or  Cmd + . for mac
   *      Zoom to Fit : Ctrl + / or  Cmd + / for mac
   *
   *   Verify:
   *   1. Zoom and Pan controls located on the bottom right.
   *   </pre>
   *   Note:
   *   As Split View cannot be accessed as of today, Zoom functionality is validated only in design view.
   */
  @Test
  @RunIn(TestGroup.SANITY_BAZEL)
  public void verifyZoomShortcutKeysFunctionality() {
    myNlEditorFixture.waitForRenderToFinish();
    assertTrue(myNlEditorFixture.panButtonPresent());
    double originalScale = myNlEditorFixture.getScale();
    System.out.println("***** Original scale: " + originalScale);

    myNlEditorFixture.zoomInByShortcutKeys();
    myNlEditorFixture.zoomInByShortcutKeys(); //Doing it twice to reduce flakiness
    double zoomInScale =  myNlEditorFixture.getScale();
    System.out.println("***** Scale after Zoom In shortcutKeys: " + zoomInScale);
    assertThat(zoomInScale).isGreaterThan(originalScale);

    myNlEditorFixture.zoomto100PercentByShortcutKeys();
    myNlEditorFixture.zoomto100PercentByShortcutKeys(); //Doing it twice to reduce flakiness
    double zoom100PercentScale = myNlEditorFixture.getScale();
    System.out.println("***** Scale after Zoom to 100% shortcutKeys: " + zoom100PercentScale);
    assertThat(zoom100PercentScale).isEqualTo(1.0);

    myNlEditorFixture.zoomOutByShortcutKeys();
    myNlEditorFixture.zoomOutByShortcutKeys();
    myNlEditorFixture.zoomOutByShortcutKeys();
    myNlEditorFixture.zoomOutByShortcutKeys();
    double zoomOutScale = myNlEditorFixture.getScale();
    System.out.println("***** Scale after Zoom out shortcutKeys: " + zoomOutScale);
    assertThat(zoomOutScale).isLessThan(zoom100PercentScale);

    myNlEditorFixture.zoomtoFitByShortcutKeys();
    myNlEditorFixture.zoomtoFitByShortcutKeys(); //Doing it twice to reduce flakiness
    double zoomToFitScale = myNlEditorFixture.getScale();
    System.out.println("***** Scale after Zoom to fit shortcutKeys: " + zoomToFitScale);
    assertThat(zoomToFitScale).isGreaterThan(zoomOutScale);

    assertTrue(myNlEditorFixture.panButtonPresent());
    guiTest.waitForAllBackgroundTasksToBeCompleted();
  }
}
