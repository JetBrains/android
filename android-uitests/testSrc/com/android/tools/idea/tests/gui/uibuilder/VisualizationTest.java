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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.DeleteDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.TextEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.VisualizationFixture;
import com.android.tools.idea.uibuilder.visual.VisualizationManager;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.MouseButton;
import org.fest.swing.timing.Wait;
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
    if (guiTest.ideFrame().getEditor().isVisualizationToolVisible()) {
      guiTest.ideFrame().getEditor().getVisualizationTool().close();
      VisualizationManager.getInstance(guiTest.ideFrame().getProject()).getVisualizationForm().dispose();
    }
    StudioFlags.NELE_VISUALIZATION.clearOverride();
    StudioFlags.NELE_SPLIT_EDITOR.clearOverride();
  }

  @Test
  public void visualizationToolAvailableForLayoutFiles() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication().getEditor();
    VisualizationFixture visualizationTool = editor.open("app/src/main/res/layout/activity_my.xml").getVisualizationTool().waitForRenderToFinish();
    assertTrue(editor.isVisualizationToolVisible());

    editor.open("app/src/main/java/google/simpleapplication/MyActivity.java");
    visualizationTool.waitForRenderToFinish();
    assertFalse(editor.isVisualizationToolVisible());

    editor.open("app/src/main/res/layout/frames.xml").getVisualizationTool().waitForRenderToFinish();
    assertTrue(editor.isVisualizationToolVisible());
  }

  @Test
  public void deletePreviewedFileShouldCloseVisualizationTool() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml")
      .getVisualizationTool()
      .waitForRenderToFinish();
    guiTest.ideFrame()
      .getProjectView()
      .selectAndroidPane()
      .clickPath(MouseButton.RIGHT_BUTTON, "app", "res", "layout", "activity_my.xml")
      .openFromMenu(DeleteDialogFixture::find, "Delete...")
      .unsafeDelete();
    assertFalse(editor.isVisualizationToolVisible());
  }

  @Test
  public void openAnotherLayoutFileShouldChangePreviewedLayoutInVisualizationTool() throws Exception {
    guiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest");
    IdeFrameFixture ideFrame = guiTest.ideFrame();
    EditorFixture editor = ideFrame.getEditor();

    editor.open("app/src/main/res/layout/layout2.xml");
    assertEquals(editor.getCurrentFile(), editor.getVisualizationTool().waitForRenderToFinish().getCurrentFile());

    editor.open("app/src/main/res/layout-sw600dp/layout2.xml");
    assertThat(editor.getCurrentFile().getParent().getName()).isEqualTo("layout-sw600dp");
    assertEquals(editor.getCurrentFile(), editor.getVisualizationTool().waitForRenderToFinish().getCurrentFile());

    editor.open("app/src/main/res/layout/layout2.xml");
    assertThat(editor.getCurrentFile().getParent().getName()).isEqualTo("layout");
    assertEquals(editor.getCurrentFile(), editor.getVisualizationTool().waitForRenderToFinish().getCurrentFile());
  }

  @Test
  public void closeLayoutShouldNotCloseVisualizationForAnotherLayout() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml")
      .open("app/src/main/res/layout/frames.xml");
    editor
      .getVisualizationTool()
      .waitForRenderToFinish();
    int updateCountBeforeClose = editor.getPreviewUpdateCount();
    editor.closeFile("app/src/main/res/layout/frames.xml");

    Wait.seconds(2).expecting("visualization tool to update")
      .until(() -> editor.getVisualizationToolUpdateCount() > updateCountBeforeClose);
    assertTrue(editor.isVisualizationToolShowing());
  }

  @Test
  public void closeSplitLayoutShouldMoveVisualizationToolToCorrectFile() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication()
      .getEditor()
      .open("app/src/main/res/layout/frames.xml")
      .open("app/src/main/res/layout/activity_my.xml")
      .invokeAction(EditorFixture.EditorAction.SPLIT_HORIZONTALLY);

    TextEditorFixture frames = editor.getVisibleTextEditor("frames.xml");
    TextEditorFixture activity = editor.getVisibleTextEditor("activity_my.xml");

    frames.select();
    assertEquals(frames.getEditor().getFile(), editor.getVisualizationTool().getCurrentFile());
    activity.select();
    assertEquals(activity.getEditor().getFile(), editor.getVisualizationTool().getCurrentFile());

    int updateCountBeforeClose = editor.getVisualizationToolUpdateCount();
    activity.closeFile();
    Wait.seconds(3).expecting("visualization tool to update")
      .until(() -> editor.getVisualizationToolUpdateCount() > updateCountBeforeClose);
    assertTrue(editor.isVisualizationToolVisible());
    assertEquals(frames.getEditor().getFile(), editor.getVisualizationTool().getCurrentFile());
  }

  @Test
  public void closeAllFileShouldCloseVisualizationTool() throws Exception {
    EditorFixture editor = guiTest.importSimpleApplication()
      .waitForGradleProjectSyncToFinish()
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml")
      .open("app/src/main/java/google/simpleapplication/MyActivity.java");

    editor.getVisibleTextEditor("activity_my.xml").select();
    editor.getVisualizationTool().waitForRenderToFinish();
    Wait.seconds(3).expecting("visualization tool to open").until(() -> editor.isVisualizationToolVisible());

    editor.invokeAction(EditorFixture.EditorAction.CLOSE_ALL);
    Wait.seconds(3).expecting("visualization tool to close").until(() -> !editor.isVisualizationToolVisible());

    assertNull(editor.getCurrentFile());
    assertFalse(editor.isVisualizationToolVisible());
  }
}
