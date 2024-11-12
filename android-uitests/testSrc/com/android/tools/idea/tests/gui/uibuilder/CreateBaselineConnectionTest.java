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
public class CreateBaselineConnectionTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(15, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();
  /**
   * To verify vertical constraints are removed from a widget when creating a baseline constraint connection
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: db32c5d3-c282-4e45-8737-a7c06b9892ad
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. In Design view drag and drop two widgets
   *   3. Create horizontal and vertical constraints for two widgets
   *   4. Create a baseline constraint from first widget to second (Verify 1)
   *   Verify:
   *   Vertical constraints for first widget should be removed
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void createBaselineConnection() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    EditorFixture editor = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture design = editor
      .getLayoutEditor()
      .showOnlyDesignView()
      .waitForRenderToFinish()
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    design.findView("Button", 0)
      .createConstraintFromBottomToBottomOfLayout()
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout()
      .createBaselineConstraintWith(design.findView("TextView", 0));

    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).doesNotContainMatch("<Button.*app:layout_constraintTop_toTopOf=\"parent\"");
    assertThat(layoutContents).doesNotContainMatch("<Button.*app:layout_constraintBottom_toBottomOf=\"parent\"");
  }
}
