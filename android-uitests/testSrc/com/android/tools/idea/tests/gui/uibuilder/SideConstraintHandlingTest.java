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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class SideConstraintHandlingTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verifies the UI for adding side constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7f7d5b54-a3df-41cc-a472-fa6f50296b0b
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, select any of the little circles on it and create constraints for all left, right,
   *      top and bottom. Create them such that your refer both other widgets and the parent layout (Verify 1)
   *   4. Repeat steps 3 and 4 by aligning widgets in blue print mode (Verify 1)
   *   Verify:
   *   1. Should be able to position the widgets in both Deign view and blueprint mode. Verify the constraints (app:layout_constraint***)
   *      in xml view of layout editor.
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void testSideConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
                                  .getEditor()
                                  .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    guiTest.ideFrame().closeProjectPanel();

    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .showOnlyDesignView()
      .dragComponentToSurface("Buttons", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    int openButtonTagIndex = layoutContents.indexOf("<Button");
    int closeButtonTagIndex = layoutContents.indexOf("/>", openButtonTagIndex);
    String buttonTag = layoutContents.substring(openButtonTagIndex, closeButtonTagIndex);
    assertThat(buttonTag).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(buttonTag).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(buttonTag).contains("app:layout_constraintStart_toStartOf=\"parent\"");
    assertThat(buttonTag).contains("app:layout_constraintEnd_toEndOf=\"parent\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
                         .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
                         .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Buttons", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    openButtonTagIndex = layoutContents.indexOf("<Button");
    closeButtonTagIndex = layoutContents.indexOf("/>", openButtonTagIndex);
    buttonTag = layoutContents.substring(openButtonTagIndex, closeButtonTagIndex);
    assertThat(buttonTag).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(buttonTag).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(buttonTag).contains("app:layout_constraintStart_toStartOf=\"parent\"");
    assertThat(buttonTag).contains("app:layout_constraintEnd_toEndOf=\"parent\"");
  }
}
