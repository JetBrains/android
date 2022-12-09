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
package com.android.tools.idea.tests.gui.uibuilder;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.common.editor.SplitEditor;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.SplitEditorFixture;
import com.google.common.base.Preconditions;
import com.android.tools.idea.tests.util.WizardUtils;
import com.android.tools.idea.wizard.template.Language;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.util.concurrent.TimeUnit;
import org.fest.swing.core.MouseButton;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ComponentSelectionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(7, TimeUnit.MINUTES);

  protected static final String EMPTY_ACTIVITY_TEMPLATE = "Empty Views Activity";
  protected static final String APP_NAME = "App";
  protected static final String PACKAGE_NAME = "android.com.app";
  protected static final int MIN_SDK_API = 30;

  private IdeFrameFixture myIdeFrameFixture;
  private EditorFixture myEditorFixture;
  NlEditorFixture myNlEditorFixture;
  private DesignSurface<?> myDesignSurface;

  @Before
  public void setUp() {
    WizardUtils.createNewProject(guiTest, EMPTY_ACTIVITY_TEMPLATE, APP_NAME, PACKAGE_NAME, MIN_SDK_API, Language.Java);
    guiTest.robot().waitForIdle();
    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myIdeFrameFixture = guiTest.ideFrame();
    myIdeFrameFixture.clearNotificationsPresentOnIdeFrame();

    myIdeFrameFixture.getProjectView().assertFilesExist(
      "app/src/main/res/layout/activity_main.xml"
    );
  }

  /**
   * To verify component selection reflects from design view to xml in split view
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial
   * changes.
   * <p>
   * TT ID: a35f03d0-5763-40f9-a129-5023ad8a21b2
   * <pre>
   *   Test Steps:
   *   1. Launch Android Studio.
   *   2. Create a new project using the Empty Activity template.
   *   3. Under the layout resource directory, open the activity_main.xml file and wait for sync to finish.
   *   4. Click the "Split" mode button from the top-right corner (middle button).
   *   5. In the Palette panel, select Buttons and drag a Button to the surface, so the layout contains a TextView and a Button.
   *   6. On the Text view of the editor, select &lt;TextView /&gt; tag. (Verify 1)
   *   7. On the Text view of the editor, select &lt;Button /&gt; tag. (Verify 2)
   *   8. On the Visual view of the editor, click the TextView. (Verify 3)
   *   9. On the Visual view of the editor, click the Button. (Verify 4)
   *   Verification:
   *   1. The TextView is selected in the Visual view editor.
   *   2. The Button is selected in the Visual view editor.
   *   3. The "TextView" tag gets highlighted in the Text view editor.
   *   4. The  "Button" tag gets highlighted in the Text view editor.
   * </pre>
   *
   */

  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testComponentSelectionInLayoutEditor() {

    myEditorFixture = myIdeFrameFixture.getEditor();

    //Opening the activity_main.xml in design view.
    myEditorFixture.open("app/src/main/res/layout/activity_main.xml", EditorFixture.Tab.DESIGN);

    myIdeFrameFixture.getEditor();

    myNlEditorFixture = myEditorFixture
      .getLayoutEditor()
      .waitForSurfaceToLoad();
    assertThat(myNlEditorFixture.canInteractWithSurface()).isTrue();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    myDesignSurface = myNlEditorFixture.getSurface().target();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Dragging a new button.
    Dimension screenViewSize = myNlEditorFixture.getSurface()
      .target()
      .getFocusedSceneView()
      .getScaledContentSize();
    int widthOffset = screenViewSize.width / 4;
    int heightOffset = screenViewSize.height / 4;

    myNlEditorFixture.dragComponentToSurface("Buttons", "Button", screenViewSize.width / 2 - widthOffset, screenViewSize.height / 2 - heightOffset)
      .waitForRenderToFinish();
    myNlEditorFixture.waitForSurfaceToLoad();

    guiTest.waitForAllBackgroundTasksToBeCompleted();

    //Switching to split view
    FileEditor @NotNull [] editors = FileEditorManager.getInstance(myIdeFrameFixture.getProject()).getSelectedEditors();
    Preconditions.checkState(editors.length != 0, "no selected editors");
    FileEditor selected = editors[0];
    Preconditions.checkState(selected.toString().contains("SplitEditor"), "invalid editor selected");
    SplitEditorFixture mySplitEditor = new SplitEditorFixture(myIdeFrameFixture.robot(), (SplitEditor<? extends FileEditor>)selected);

    mySplitEditor.setSplitMode();
    mySplitEditor.getDesignSurface().waitForRenderToFinish();

    //Verifications for test steps 6 and 7
    //Checking for textView
    myEditorFixture.moveBetween("<Text", "View");

    Point pointerLocation = MouseInfo.getPointerInfo().getLocation();
    myIdeFrameFixture.robot().click(pointerLocation,
                                    MouseButton.LEFT_BUTTON,
                                    1);

    mySplitEditor.getDesignSurface()
        .waitForRenderToFinish();

    NlComponent selectedComponentText = mySplitEditor
      .getDesignSurface()
      .getSelection()
      .get(0);

    assertThat(mySplitEditor.getDesignSurface().getSelection())
      .isNotEmpty();
    assertThat(mySplitEditor.getDesignSurface().getSelection())
      .hasSize(1);
    assertThat(selectedComponentText.toString())
      .contains("TextView");
    assertThat(selectedComponentText.getTagName())
      .contains("TextView");

    //Checking for button
    myEditorFixture.moveBetween("<But", "ton");

    pointerLocation = MouseInfo.getPointerInfo().getLocation();
    myIdeFrameFixture.robot().click(pointerLocation,
                                    MouseButton.LEFT_BUTTON,
                                    1);

    mySplitEditor.getDesignSurface()
      .waitForRenderToFinish();

    selectedComponentText = mySplitEditor
      .getDesignSurface()
      .getSelection()
      .get(0);

    assertThat(mySplitEditor.getDesignSurface().getSelection())
      .isNotEmpty();
    assertThat(mySplitEditor.getDesignSurface().getSelection())
      .hasSize(1);
    assertThat(selectedComponentText.toString())
      .contains("Button");
    assertThat(selectedComponentText.getTagName())
      .contains("Button");

    //Verifications for test steps 8 and 9.
    mySplitEditor.getDesignSurface()
      .findView("TextView", 0)
      .getSceneComponent()
      .click();

    mySplitEditor.getDesignSurface()
        .waitForRenderToFinish();

    assertThat(myEditorFixture.getCurrentLine())
      .contains("<TextView");

    mySplitEditor.getDesignSurface()
      .findView("Button", 0)
      .getSceneComponent()
      .click();

    mySplitEditor.getDesignSurface()
      .waitForRenderToFinish();

    assertThat(myEditorFixture.getCurrentLine())
      .contains("<Button");
  }
}