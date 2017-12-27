/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertFalse;

/**
 * UI test for the component assistant in the properties panel
 */
@RunWith(GuiTestRunner.class)
public class ComponentAssistantTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Before
  public void setUp() {
    StudioFlags.NELE_WIDGET_ASSISTANT.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_WIDGET_ASSISTANT.clearOverride();
  }

  @Test
  public void testRecyclerViewAssistantAvailable() throws Exception {
    NlEditorFixture layout = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR)
      .getLayoutEditor(true);

    layout.findView("TextView", 0).click();
    assertFalse(layout.getPropertiesPanel()
                  .openAsInspector()
                  .hasComponentAssistantPanel());

    layout.dragComponentToSurface("AppCompat", "android.support.v7.widget.RecyclerView");
    MessagesFixture.findByTitle(guiTest.robot(), "Add Project Dependency").clickOk();
    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()
      .getEditor();

    layout.findView("android.support.v7.widget.RecyclerView", 0).click();
    layout.getPropertiesPanel()
      .openAsInspector()
      .getComponentAssistantPanel();
  }
}
