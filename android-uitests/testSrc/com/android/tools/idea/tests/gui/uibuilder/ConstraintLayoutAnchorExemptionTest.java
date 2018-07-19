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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class ConstraintLayoutAnchorExemptionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * To verify that anchors on different axis, such as left and top anchor cannot be connected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: ffa37b5f-d71a-4b29-bcdd-2b73865e1496
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   3. Now click on a widget in the design view, select left/right anchor and try to connect it to top and bottom anchor
   *   4. Repeat step 3 with top/bottom anchor and try connect to left and right anchors
   *   Verify:
   *   Anchors on different axis, such as left and top anchor shall not get connected and no abnormal behavior is observed
   *   </pre>
   */
  @RunIn(TestGroup.QA_BAZEL)
  @Test
  public void constraintLayoutAnchorExemption() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutLocalTest");

    EditorFixture editor = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture design = editor.getLayoutEditor(false)
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();

    editor.open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false)
      .waitForRenderToFinish();
    design.findView("Button", 0)
      .createConstraintFromBottomToLeftOf(design.findView("TextView", 0));
    String layoutContentsAfter = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).isEqualTo(layoutContentsAfter);
  }
}
