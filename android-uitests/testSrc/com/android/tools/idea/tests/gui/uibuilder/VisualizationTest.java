/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI test for the visualization tool window
 */
@RunIn(TestGroup.UNRELIABLE)
@RunWith(GuiTestRemoteRunner.class)
public class VisualizationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @Before
  public void setUp() {
    StudioFlags.NELE_VISUALIZATION.override(true);
    StudioFlags.NELE_SPLIT_EDITOR.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_VISUALIZATION.clearOverride();
    StudioFlags.NELE_SPLIT_EDITOR.clearOverride();
  }

  @Test
  public void visualizationToolAvailableForLayoutFile() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication().getEditor();
    editor.open("app/src/main/res/layout/frames.xml");
    assertThat(editor.getVisualizationTool().getCurrentFileName()).isEqualTo("frames.xml");

    editor.open("app/src/main/res/layout/activity_my.xml");
    assertThat(editor.getVisualizationTool().getCurrentFileName()).isEqualTo("activity_my.xml");

    editor.open("app/src/main/java/google/simpleapplication/MyActivity.java")
      .waitForVisualizationToolToHide()
      .invokeAction(EditorFixture.EditorAction.CLOSE_ALL);
  }
}
