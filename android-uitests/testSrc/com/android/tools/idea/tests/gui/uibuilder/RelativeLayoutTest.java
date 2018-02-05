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

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import org.fest.swing.timing.Wait;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class RelativeLayoutTest {

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Before
  public void setUp() {
    StudioFlags.NELE_TARGET_RELATIVE.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_TARGET_RELATIVE.clearOverride();
  }

  @Test
  public void testDragFragmentFromPalette() throws Exception {
    myGuiTest.importProjectAndWaitForProjectSyncToFinish("FragmentApplication");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    layout.dragComponentToSurface("Containers", "<fragment>");

    ChooseClassDialogFixture dialog = ChooseClassDialogFixture.find(myGuiTest.ideFrame());
    assertThat(dialog.getTitle()).isEqualTo("Fragments");
    assertThat(dialog.getList().contents().length).isEqualTo(2);

    dialog.getList().selectItem("PsiClass:YourCustomFragment");
    dialog.clickOk();

    editor.switchToTab("Text");
    String content = editor.getCurrentFileContents();
    assertThat(content).contains("    <fragment\n" +
                                 "        android:id=\"@+id/fragment\"\n" +
                                 "        android:name=\"google.fragmentapplication.YourCustomFragment\"\n");
  }

  @Test
  public void testDragImageViewFromPalette() throws Exception {
    myGuiTest.importProjectAndWaitForProjectSyncToFinish("FragmentApplication");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();

    // Before dragging the component, make sure there is no other task running.
    myGuiTest.robot().waitForIdle();
    layout.dragComponentToSurface("Widgets", "ImageView");

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myGuiTest.robot());
    assertThat(dialog.getTitle()).isEqualTo("Resources");
    dialog.expandList("Project").getList("Project").selectItem("ic_launcher");

    dialog.clickOK();

    editor.switchToTab("Text");

    String content = editor.getCurrentFileContents();
    assertThat(content).contains("    <ImageView\n" +
                                 "        android:id=\"@+id/imageView\"\n" +
                                 "        android:layout_width=\"wrap_content\"\n" +
                                 "        android:layout_height=\"wrap_content\"\n");
    // There may be some align and/or margin attributes between android:layout_height and android:src. Ignore them.
    assertThat(content).contains("        android:src=\"@drawable/ic_launcher\"");
  }
}
