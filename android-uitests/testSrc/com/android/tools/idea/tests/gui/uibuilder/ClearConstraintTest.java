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
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ClearConstraintTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * To verify that all the constraints of a widget can be cleared at the click of a button with out affecting constraints of other widgets
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 89d7cd9d-f01e-4407-9d35-31a4309f9804
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, one button and one text view.
   *   3. Create constraints such that one widget refers to other
   *   4. Now click on any widget (Verify 1)
   *   5. Click on the button shown in the above step(verify 2)
   *   Verify:
   *   1. A button should appear with cross symbol and two little arrows.
   *   2. Constraints for that widget should get cleared.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void clearConstraint() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutLocalTest");

    EditorFixture editor = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture design = editor.getLayoutEditor(false)
      .showOnlyDesignView()
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    design.findView("Button", 0)
      .createBaselineConstraintWith(design.findView("TextView", 0));
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");

    editor.open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .waitForRenderToFinish();
    JComponent killButton = GuiTests.waitUntilShowing(guiTest.robot(),
                                                      Matchers.byTooltip(JComponent.class, "Delete Baseline Constraint"));
    guiTest.robot().click(killButton);
    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).doesNotContain("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");
  }
}
