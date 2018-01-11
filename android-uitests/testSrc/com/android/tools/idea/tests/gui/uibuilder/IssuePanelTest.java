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

import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseClassDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlPropertyInspectorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.layout.IssuePanelFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(GuiTestRunner.class)
public class IssuePanelTest {

  @Rule public final GuiTestRule myGuiTest = new GuiTestRule();

  /**
   * Scenario:
   * - Open layout with TextView
   * - Open the IssuePanel
   * - Check that it has no issue by checking the title
   * - Select the textView
   * - Focus on the "text" property in the propertyPanel
   * - Enter the text "b"
   * - Ensure that a lint warning has been created for the hardcoded text
   * - select the issue in the IssuePanel
   * - click the fix button
   * - check that the issue is gone by checking the title and the JLabel of the issue
   */
  @RunIn(TestGroup.UNRELIABLE)
  @Test
  public void openIssuePanel() throws IOException {

    EditorFixture editor = myGuiTest.importProjectAndWaitForProjectSyncToFinish("LayoutTest")
      .getEditor()
      .open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);
    NlEditorFixture layoutEditor = editor.getLayoutEditor(true);

    NlComponentFixture textView = layoutEditor
      .showOnlyDesignView()
      .findView("TextView", 0);
    textView.click();

    layoutEditor.getRhsConfigToolbar().openIssuePanel();
    IssuePanelFixture panelFixture = layoutEditor.getIssuePanel();
    assertEquals("No issues", panelFixture.getTitle());

    NlPropertyInspectorFixture propertyPanel = layoutEditor.getPropertiesPanel();
    propertyPanel.focusAndWaitForFocusGainInProperty("text", null);
    propertyPanel.pressKeyInUnknownProperty(KeyEvent.VK_B);
    textView.click();

    layoutEditor.waitForRenderToFinish();
    layoutEditor.getRhsConfigToolbar().openIssuePanel();
    assertEquals("1 Warning", panelFixture.getTitle().trim());

    String hardcodedTextErrorTitle = "Hardcoded text";
    GuiTests.waitUntilShowing(myGuiTest.robot(), panelFixture.target(),
                              Matchers.byText(JLabel.class, hardcodedTextErrorTitle));
    panelFixture.findIssueWithTitle(hardcodedTextErrorTitle).doubleClick();
    panelFixture.clickFixButton();

    panelFixture.dialog().button(Matchers.byText(JButton.class, "OK")).click();
    GuiTests.waitUntilGone(myGuiTest.robot(), panelFixture.target(),
                           Matchers.byText(JLabel.class, hardcodedTextErrorTitle));
    assertEquals("No issues", panelFixture.getTitle());
  }

  @Test
  public void testFixMissingFragmentNameWithoutCustomFragmentsAvailable() throws Exception {
    myGuiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.EDITOR);
    editor.moveBetween(">\n", "\n</RelativeLayout");
    editor.enterText("<fragment android:layout_width=\"match_parent\" android:layout_height=\"match_parent\"/>\n");
    editor.switchToTab("Design");

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    layout.getRhsConfigToolbar().openIssuePanel();
    IssuePanelFixture panelFixture = layout.getIssuePanel();
    layout.enlargeBottomComponentSplitter();
    String errorTitle = "Unknown fragments";
    GuiTests.waitUntilShowing(myGuiTest.robot(), panelFixture.target(), Matchers.byText(JLabel.class, errorTitle));
    panelFixture.findIssueWithTitle(errorTitle).doubleClick();
    panelFixture.clickOnLink("Choose Fragment Class...");

    MessagesFixture classesDialog = MessagesFixture.findByTitle(myGuiTest.robot(), "No Fragments Found");
    classesDialog.requireMessageContains("You must first create one or more Fragments in code");
    classesDialog.clickOk();
  }

  @Test
  @RunIn(TestGroup.UNRELIABLE)
  public void testFixMissingFragmentNameWithCustomFragmentsAvailable() throws Exception {
    myGuiTest.importProjectAndWaitForProjectSyncToFinish("FragmentApplication");

    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/fragment_empty.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    layout.getRhsConfigToolbar().openIssuePanel();
    IssuePanelFixture panelFixture = layout.getIssuePanel();
    layout.enlargeBottomComponentSplitter();
    String errorTitle = "Unknown fragments";
    GuiTests.waitUntilShowing(myGuiTest.robot(), panelFixture.target(), Matchers.byText(JLabel.class, errorTitle));
    panelFixture.findIssueWithTitle(errorTitle).doubleClick();
    panelFixture.clickOnLink("Choose Fragment Class...");

    ChooseClassDialogFixture dialog = ChooseClassDialogFixture.find(myGuiTest.ideFrame());
    assertThat(dialog.getTitle()).isEqualTo("Fragments");
    assertThat(dialog.getList().contents().length).isEqualTo(2);

    dialog.getList().selectItem("PsiClass:YourCustomFragment");
    dialog.clickOk();

    editor.switchToTab("Text");
    String content = editor.getCurrentFileContents();
    assertThat(content).contains("<fragment android:layout_width=\"match_parent\"\n" +
                                 "            android:layout_height=\"match_parent\"\n" +
                                 "      android:name=\"google.fragmentapplication.YourCustomFragment\" />");
  }
}
