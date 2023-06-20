/*
 * Copyright (C) 2020 The Android Open Source Project
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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class OpenCloseVisualizationToolTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);
  @Rule public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

  /**
   * Verifies that the Visualization Tool can be open and closed correctly.
   * <p>
   *   This is run to qualify releases. Please involve the test team in substantial changes.
   * </p>
   *
   * TT ID: 1aa49fe7-0b4c-4eb7-84ca-a1e4c1ba18ff
   * <p>
   *   <pre>
   *   This feature is for Android Studio 3.2 and above.
   *   Test Steps:
   *   1. Import SimpleApplication project.
   *   2. Open xml files and verify the files are opened in Visualization Tool window.
   *   3. Resize the validation window and check that the display has rearranged.
   *   4. Open Java file, and verify the Visualization Tool window is closed.
   *   5. Close all opened xml files and java file.
   *   </pre>
   * </p>
   */
  @Test
  public void visualizationToolAvailableForLayoutFile() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication().getEditor();
    final String file1 = "app/src/main/res/layout/frames.xml";
    final String file2 = "app/src/main/res/layout/activity_my.xml";
    final String file3 = "app/src/main/java/google/simpleapplication/MyActivity.java";

    editor.open(file1);
    assertThat(editor.getVisualizationTool().getCurrentFileName()).isEqualTo("frames.xml");

    editor.open(file2);
    assertThat(editor.getVisualizationTool().getCurrentFileName()).isEqualTo("activity_my.xml");
    editor.getVisualizationTool().zoomToFit();
    assertThat(editor.getVisualizationTool().getRowNumber()).isEqualTo(3);

    editor.getVisualizationTool().expandWindow();
    assertThat(editor.getVisualizationTool().getRowNumber()).isEqualTo(2);

    editor.open(file3).waitForVisualizationToolToHide();

    // reset the state, i.e. hide the visualization tool window and close all the files.
    editor.open(file1).getVisualizationTool().hide();
    editor.closeFile(file1).closeFile(file2).closeFile(file3);
  }

}
