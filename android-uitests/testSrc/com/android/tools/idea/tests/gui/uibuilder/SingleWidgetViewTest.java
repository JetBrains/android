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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.ConstraintLayoutViewInspectorFixture;
import com.intellij.openapi.application.ApplicationManager;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class SingleWidgetViewTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testScrollMargin() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    EditorFixture editor = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture design = editor
      .getLayoutEditor(false);

    design
      .showOnlyDesignView()
      .waitForRenderToFinish()
      .dragComponentToSurface("Buttons", "Button")
      .findView("Button", 0)
      .createConstraintFromBottomToTopOf(design.findView("TextView", 0))
      .createConstraintFromTopToTopOfLayout()
      .createConstraintFromLeftToLeftOfLayout()
      .createConstraintFromRightToRightOfLayout();

    // Make sure the Button is been selected.
    design.findView("Button", 0).click();

    ConstraintLayoutViewInspectorFixture view = design.getPropertiesPanel().waitForPanelLoading().getConstraintLayoutViewInspector();
    view.setAllMargins(8);
    waitForScout();
    view.scrollAllMargins(-5);
    design.waitForRenderToFinish();
    String layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginBottom=\"13dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginEnd=\"13dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginStart=\"13dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginTop=\"13dp\"");
    editor.selectEditorTab(EditorFixture.Tab.DESIGN);
    view.scrollAllMargins(3);
    design.waitForRenderToFinish();
    layoutContents = editor.selectEditorTab(EditorFixture.Tab.EDITOR).getCurrentFileContents();
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginBottom=\"10dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginEnd=\"10dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginStart=\"10dp\"");
    assertThat(layoutContents).containsMatch("<Button(?s).*android:layout_marginTop=\"10dp\"");
  }

  private void waitForScout() {
    ApplicationManager.getApplication().invokeLater(() -> {
    });

    guiTest.robot().waitForIdle();
  }
}