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
package com.android.tools.idea.tests.gui.performance;

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.bleak.UseBleak;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class LayoutEditorMemoryUseTest {
  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * Shows the layout and editor tabs of three different layout files.
   */
  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  public void navigateAndEditWithBLeak() throws Exception {
    IdeFrameFixture fixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    String[] layoutFilePaths = {
      "app/src/main/res/layout/layout2.xml",
      "app/src/main/res/layout/widgets.xml",
      "app/src/main/res/layout/textstyles.xml",
    };

    guiTest.runWithBleak(
      () -> {
        // First file on design mode
        fixture.getEditor().open(layoutFilePaths[0], EditorFixture.Tab.DESIGN).getLayoutEditor().waitForRenderToFinish();
        // Second file on design mode, then switch to text mode
        fixture.getEditor().open(layoutFilePaths[1], EditorFixture.Tab.DESIGN).getLayoutEditor().waitForRenderToFinish();
        fixture.getEditor().selectEditorTab(EditorFixture.Tab.EDITOR);
        // Third file on text mode
        fixture.getEditor().open(layoutFilePaths[2], EditorFixture.Tab.EDITOR);
      }
    );
  }

  /**
   * Adds a button from the palette then removes it again.
   */
  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  public void addControl() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importSimpleApplication();

    NlEditorFixture design = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .waitForRenderToFinish();

    guiTest.runWithBleak(
      () -> {
        design.dragComponentToSurface("Buttons", "Button");
        assertThat(design.getSurface().hasRenderErrors()).isFalse();
        guiTest.robot().pressAndReleaseKey(KeyEvent.VK_DELETE);
      }
    );
  }

  /**
   * Resizes a control on the design surface.
   */
  @Test
  @UseBleak
  @RunIn(TestGroup.PERFORMANCE)
  public void resizeControl() throws Exception {
    IdeFrameFixture ideFrameFixture = guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");

    NlEditorFixture design = ideFrameFixture.getEditor()
      .open("app/src/main/res/layout/constraint.xml", EditorFixture.Tab.DESIGN)
      .getLayoutEditor()
      .dragComponentToSurface("Buttons", "Button")
      .waitForRenderToFinish();

    NlComponentFixture textView = design.findView("Button", 0);

    guiTest.runWithBleak(
      () -> {
        textView.resizeBy(10, 10);
        design.waitForRenderToFinish();
      }
    );
  }
}
