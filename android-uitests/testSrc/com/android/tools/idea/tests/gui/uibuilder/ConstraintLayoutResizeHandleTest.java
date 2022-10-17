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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class ConstraintLayoutResizeHandleTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * To verify that a widget in a constraint layout can be resized in the design view.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 25161e82-9e99-48b5-9c50-59ac06e79eb1
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open the layout file which uses constraint layout and switch to design view
   *   2. From the palette window, drag and drop a widget to constraint layout. Say, a button
   *   3. Now click on the widget in the design view, point your mouse to the little squares at any corner of the widget,
   *   select it and resize (Verify 1)
   *   Verify:
   *   Should be able to resize.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void constraintLayoutResizeHandle() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    NlEditorFixture design = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForSurfaceToLoad()
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    NlComponentFixture textView = design.findView("Button", 0);
    int width = textView.getSceneComponent().getWidth();
    int height = textView.getSceneComponent().getHeight();
    textView.resizeBy(10, 10);
    design.waitForRenderToFinish();
    assertThat(textView.getSceneComponent().getWidth()).isGreaterThan(width);
    assertThat(textView.getSceneComponent().getHeight()).isGreaterThan(height);
  }
}
