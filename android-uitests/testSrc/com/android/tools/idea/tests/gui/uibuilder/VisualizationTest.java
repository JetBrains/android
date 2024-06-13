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

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.bleak.UseBleak;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI test for the visualization tool window
 */
@RunWith(GuiTestRemoteRunner.class)
public class VisualizationTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  public void openAndCloseVisualizationToolWithBleak() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication().getEditor();
    guiTest.runWithBleak(() -> openAndCloseVisualizationTool(editor));
  }

  static void openAndCloseVisualizationTool(@NotNull EditorFixture editor) {
    final String file1 = "app/src/main/res/layout/frames.xml";
    final String file2 = "app/src/main/res/layout/activity_my.xml";
    final String file3 = "app/src/main/java/google/simpleapplication/MyActivity.java";

    editor.open(file1);
    assertThat(editor.getVisualizationTool().waitForRenderToFinish().getCurrentFileName()).isEqualTo("frames.xml");

    editor.open(file2);
    assertThat(editor.getVisualizationTool().waitForRenderToFinish().getCurrentFileName()).isEqualTo("activity_my.xml");

    editor.open(file3).waitForVisualizationToolToHide();

    // reset the state, i.e. hide the visualization tool window and close all the files.
    editor.open(file1).getVisualizationTool().hide();
    editor.closeFile(file1).closeFile(file2).closeFile(file3);
  }
}
