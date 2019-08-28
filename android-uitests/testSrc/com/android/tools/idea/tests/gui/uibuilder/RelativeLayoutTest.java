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

import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ResourceExplorerDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(GuiTestRemoteRunner.class)
public class RelativeLayoutTest {

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();
  @Rule
  public final RenderTaskLeakCheckRule renderTaskLeakCheckRule = new RenderTaskLeakCheckRule();

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
    layout.dragComponentToSurface("Widgets", "ImageView");

    ResourceExplorerDialogFixture dialog = ResourceExplorerDialogFixture.find(myGuiTest.robot());
    assertThat(dialog.getTitle()).isEqualTo("Pick a Resource");
    dialog.getResourceExplorer().selectResource("ic_launcher");
    dialog.clickOk();

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
