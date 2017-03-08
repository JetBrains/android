/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.layout;

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

/**
 * UI tests for the constraint layout
 */
@RunWith(GuiTestRunner.class)
public class ConstraintLayoutTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final ScreenshotsDuringTest screenshotRule = new ScreenshotsDuringTest();

  /**
   * Verifies the UI for adding side constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603450
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
  @RunIn(TestGroup.QA)
  @Test
  public void testSideConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .showOnlyDesignView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(layoutContents).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintLeft_toLeftOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintRight_toRightOf=\"parent\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(layoutEditor.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBottom_toTopOf=\"@+id/textView\"");
    assertThat(layoutContents).contains("app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintLeft_toLeftOf=\"parent\"");
    assertThat(layoutContents).contains("app:layout_constraintRight_toRightOf=\"parent\"");
  }

  /**
   * Verifies the UI for adding baseline constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TR ID: C14603451
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, you should see a wide and thin rectangle.
   *   4. Click on the thin rectangle of one widget and create a constraint with similar rectangle in another widget
   *   5. Repeat steps 3 and 4 by aligning widgets in blue print mode (Verify 1)
   *   Verify:
   *   1. Should be able to baseline the widgets in both design view and blueprint mode. Verfiy the same in xml view by checking
   *      for "layout_constraintBaseline_toBaselineOf"
   *   </pre>
   */
  @RunIn(TestGroup.QA)
  @Test
  public void testBaselineConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .showOnlyDesignView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
      .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
      .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");
  }
}
