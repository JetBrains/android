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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.layout.NlEditorFixture;
import com.android.tools.idea.uibuilder.surface.PanZoomPanel;
import com.intellij.openapi.wm.impl.IdeFrameImpl;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;

import static org.junit.Assert.assertEquals;

/**
 * UI tests for moving the application window when the panel is open
 */
@RunWith(GuiTestRunner.class)
public class PanZoomPanelTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  @Ignore
  public void openPanAndZoom() throws Exception {
    IdeFrameFixture application = guiTest.importSimpleApplication();

    NlEditorFixture layoutEditor = application
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false);

    layoutEditor
      .getRhsToolbar()
      .openPanZoomWindow();

    PanZoomPanel panel;

    // Check panel is open
    panel = guiTest.robot().finder().findByType(PanZoomPanel.class, true);

    // Check panel moves with windows
    IdeFrameImpl frame = guiTest.ideFrame().target();
    Point panelLocationOnScreen = panel.getLocationOnScreen();
    Point panelExpectedLocationOnScreen = new Point(panelLocationOnScreen.x - 10, panelLocationOnScreen.y - 10);
    guiTest.robot().pressMouse(frame, new Point());
    guiTest.robot().moveMouse(frame, new Point(-10, -10));
    guiTest.robot().releaseMouseButtons();
    assertEquals(panelExpectedLocationOnScreen, panel.getLocationOnScreen());

    // Check panel is still visible when switching editor
    application.getEditor()
      .open("app/src/main/res/layout/absolute.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor(false);
    panel = guiTest.robot().finder().findByType(PanZoomPanel.class, true);

    guiTest.robot().click(panel); // Ensure that the focus is on the application otherwise the tear down fails
  }
}
