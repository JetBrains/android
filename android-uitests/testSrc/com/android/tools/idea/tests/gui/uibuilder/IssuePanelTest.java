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
import com.android.tools.idea.tests.gui.framework.fixture.layout.IssuePanelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.exception.ComponentLookupException;
import org.jetbrains.android.util.AndroidBundle;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.io.IOException;

import static org.junit.Assert.fail;

@RunWith(GuiTestRunner.class)
public class IssuePanelTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testOpenErrorPanel() throws IOException {

    EditorFixture editor = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    NlComponentFixture button = layoutEditor
      .showOnlyDesignView()
      .dragComponentToSurface("Widgets", "Button")
      .findView("Button", 0);

    layoutEditor.getRhsToolbar().openErrorPanel();

    IssuePanelFixture panelFixture = layoutEditor.getErrorPanel().extandToHalf();
    String constraintErrorMessage = AndroidBundle.message("android.lint.inspections.missing.constraints");
    panelFixture.issueLabel(constraintErrorMessage).doubleClick();


    //noinspection EmptyCatchBlock
    try {
      panelFixture.addConstraints(layoutEditor, button);
      panelFixture.issueLabel(constraintErrorMessage);
      fail();
    }
    catch (NullPointerException ex) {

    }
    catch (Throwable throwable) {
      throwable.printStackTrace();
    }

    String hardcodedTextErrorTitle = "Hardcoded text";
    panelFixture.issueLabel(hardcodedTextErrorTitle).doubleClick();
    panelFixture.clickFixButton();

    //noinspection EmptyCatchBlock
    try {
      panelFixture.dialog().button(Matchers.byText(JButton.class, "OK")).click();
      panelFixture.label(hardcodedTextErrorTitle);
      fail();
    }
    catch (NullPointerException | ComponentLookupException ex) {

    }
  }
}
