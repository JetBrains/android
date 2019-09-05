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
import org.fest.swing.timing.Wait;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRemoteRunner.class)
public class BaselineConstraintHandlingTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verifies the UI for adding baseline constraints for a ConstraintLayout in the layout editor.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 604b3f71-f831-4236-a30e-725fa0c54193
   * <p>
   *   <pre>
   *   Test Steps:
   *   - Open the layout file which uses constraint layout and switch to design view
   *   - Left click the view to enable show baseline.
   *   - From the palette window, drag and drop couple of widgets to constraint layout. Say, a button and a text view
   *   - Now click on a widget in the design view, you should see a wide and thin rectangle.
   *   - Click on the thin rectangle of one widget and create a constraint with similar rectangle in another widget
   *   - Repeat steps 3 and 4 by aligning widgets in blue print mode (Verify 1)
   *   Verify:
   *   - Should be able to baseline the widgets in both design view and blueprint mode. Verify the same in xml view by checking
   *      for "layout_constraintBaseline_toBaselineOf"
   *   </pre>
   */
  @RunIn(TestGroup.SANITY_BAZEL)
  @Test
  public void baselineConstraintHandling() throws Exception {
    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
                                  .getEditor()
                                  .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    layoutEditor
      .waitForRenderToFinish(Wait.seconds(120))
      .showOnlyDesignView()
      .findView("TextView", 0)
      .rightClick();

    layoutEditor
      .waitForRenderToFinish(Wait.seconds(120))
      .dragComponentToSurface("Buttons", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");

    layoutEditor = editor.select("(<Button[\\s\\S]*/>\\n)")
                         .invokeAction(EditorFixture.EditorAction.BACK_SPACE)
                         .getLayoutEditor(true);

    layoutEditor
      .showOnlyBlueprintView()
      .dragComponentToSurface("Buttons", "Button")
      .findView("Button", 0)
      .createBaselineConstraintWith(layoutEditor.findView("TextView", 0));
    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).contains("app:layout_constraintBaseline_toBaselineOf=\"@+id/textView\"");
  }
}
