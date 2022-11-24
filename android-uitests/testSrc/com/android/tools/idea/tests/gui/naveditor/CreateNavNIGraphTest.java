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

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.timing.Pause.pause;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.CreateResourceFileDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SceneComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.naveditor.NavDesignSurfaceFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.fest.swing.core.ComponentDragAndDrop;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class CreateNavNIGraphTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);
  private IdeFrameFixture ideFrame = null;
  private NlEditorFixture editorFixture = null;

  private static final Pattern SET_AS_DESTINATION = Pattern.compile(
    "app.*startDestination.*fragment2", Pattern.DOTALL);
  private static final Pattern ACTION_TO_SELF_FRAGMENT = Pattern.compile(
    "action.*action_fragment2_self", Pattern.DOTALL);
  private static final Pattern ACTION_TO_FROM_FRAGMENT =Pattern.compile(
    "action.*action_fragment2_to_fragment1", Pattern.DOTALL);

  @Before
  public void setUp() throws Exception{
    guiTest.importSimpleApplication()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app")
      .openFromContextualMenu(CreateResourceFileDialogFixture::find, "New", "Android Resource File")
      .setFilename("nav_demo")
      .setType("navigation")
      .clickOkAndWaitForDependencyDialog()
      .clickOk();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    ideFrame = guiTest.ideFrame();

    ideFrame.getProjectView().assertFilesExist(
      "app/src/main/res/navigation/nav_demo.xml"
    );
  }

  /**
   * Create a new Graph in Navigation Editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 97993278-c41c-406f-8a25-c3af3811377b
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Create a Default Studio Project.
   *   2. Expand Project Explorer and Right click on res folder.
   *   3. Select New > Android Resource File
   *   4. Choose "Navigation" for the type, put the file name as "nav_demo" and click OK.
   *   5. Click the button in the toolbar > New Destination ->  Create new destination > Create a BlankFragment > OK (Expectation 1, 2 & 3)
   *   6. Create another destination as above, and move it so there's some space between them. (Expectation 4)
   *   7. Click and drag the "action handle" (The Circle at right side) and drag to the other destination. (Expectation 5)
   *   8. Click and drag the action handle of the first destination again, this time dragging back onto the first destination itself. (Expectation 6)
   *   9. Select the second destination, and in the right panel click "set start destination" (Expectation 7)
   *
   *   Verify:
   *   1. New destination is shown, and is selected (has a blue border).
   *   2. The new destination should be the "start destination". It will have the house icon above it.
   *   3. The new destination should be listed in the left panel.
   *   4. The new destination should be shown in the design surface and in the left panel.
   *   5. An action (arrow) should be created between the two, and it should be selected (drawn in blue).
   *   6. A "self action" should be created (an arrow pointing from the first destination back to itself).
   *   7. The house icon is removed from the first destination and shows on the second.
   *
   *   </pre>
   *
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void createNavNIGraphTest() throws Exception{
    //Scene components for fragments needed for drag and drop
    SceneComponentFixture sceneComponentFragment1 = null;
    SceneComponentFixture sceneComponentFragment2 = null;

    //Loading the navigation layout Design View
    editorFixture  = guiTest.ideFrame()
      .getEditor()
      .open("app/src/main/res/navigation/nav_demo.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForSurfaceToLoad()
      .waitForRenderToFinish();
    guiTest.waitForAllBackgroundTasksToBeCompleted();
    assertThat(editorFixture.canInteractWithSurface()).isTrue();

    // Create two fragments
    createNewDestinationFragment("Fragment1", "Java");
    createNewDestinationFragment("Fragment2", "Java");

    // Modify "Set Start Destination" in right click menu
    ((NavDesignSurfaceFixture)editorFixture.getSurface()).findDestination("fragment2")
      .invokeContextMenuAction("Set as Start Destination");

    sceneComponentFragment1 = ((NavDesignSurfaceFixture)editorFixture.getSurface()).findDestination("fragment1");
    sceneComponentFragment2 = ((NavDesignSurfaceFixture)editorFixture.getSurface()).findDestination("fragment2");


    ComponentDragAndDrop myDragAndDrop = new ComponentDragAndDrop(guiTest.robot());
    DesignSurface<?> mySurface = editorFixture.getSurface().target();

    // Create a self action on fragment2
    myDragAndDrop.drag(mySurface, sceneComponentFragment2.getRightCenterPoint());
    myDragAndDrop.drop(mySurface, sceneComponentFragment2.getMidPoint());
    pause(SceneComponent.ANIMATION_DURATION);
    Wait.seconds(5);

    // Create an action from fragment 2 to fragment1
    sceneComponentFragment2.click();
    myDragAndDrop.drag(mySurface, sceneComponentFragment2.getRightCenterPoint());
    myDragAndDrop.drop(mySurface, sceneComponentFragment1.getBottomCenterPoint());
    pause(SceneComponent.ANIMATION_DURATION);
    Wait.seconds(5);

    //Verify the "Set As Destination" and newly added actions are updated in the XML file.
    String layoutText = ideFrame.getEditor()
      .open("app/src/main/res/navigation/nav_demo.xml", EditorFixture.Tab.EDITOR)
      .getCurrentFileContents();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    assertThat(layoutText).containsMatch(SET_AS_DESTINATION);
    assertThat(layoutText).containsMatch(ACTION_TO_SELF_FRAGMENT);
    assertThat(layoutText).containsMatch(ACTION_TO_FROM_FRAGMENT);
  }

  private void createNewDestinationFragment(String fragmentName, String language) throws Exception{
    editorFixture
      .waitForRenderToFinish()
      .getNavSurface()
      .openAddDestinationMenu()
      .waitForContents()
      .clickCreateNewFragment()
      .chooseFragment("Fragment (Blank)")
      .clickNextFragment()
      .getConfigureTemplateParametersStep()
      .enterTextFieldValue("Fragment Name", fragmentName)
      .selectComboBoxItem("Source Language", language)
      .wizard()
      .clickFinishAndWaitForSyncToFinish();
  }
}
