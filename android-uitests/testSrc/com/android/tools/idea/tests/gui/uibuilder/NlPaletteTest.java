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
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SearchTextFieldFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.designer.NlPaletteFixture;
import icons.StudioIcons;
import org.fest.swing.fixture.JListFixture;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.event.KeyEvent;

import static com.google.common.truth.Truth.assertThat;

@RunWith(GuiTestRunner.class)
public class NlPaletteTest {

  @Rule
  public final GuiTestRule myGuiTest = new GuiTestRule();

  @Before
  public void setUp() {
    StudioFlags.NELE_NEW_PALETTE.override(true);
  }

  @After
  public void tearDown() {
    StudioFlags.NELE_NEW_PALETTE.clearOverride();
  }

  @Test
  public void testTypingKeepsCategorySelectionIfMatchesFound() throws Exception {
    myGuiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    NlPaletteFixture palette = layout.getPalette();

    // Start typing in the palette brings focus to the SearchTextField on the ToolWindow header
    JListFixture itemList = palette.getItemList("Layouts");
    itemList.pressAndReleaseKeys(KeyEvent.VK_A);

    // Continue typing in search field to select HorizontalLinearLayout & VerticalLinearLayout
    SearchTextFieldFixture searchTextFieldFixture = palette.getSearchTextField();
    searchTextFieldFixture.enterText("r");

    // Test:
    JListFixture categoryList = palette.getCategoryList();
    assertThat(categoryList.selection()).asList().containsExactly("Layouts");
    assertThat(itemList.contents()).isEqualTo(new String[]{"LinearLayout (horizontal)", "LinearLayout (vertical)"});
  }

  @Test
  public void testTypingSwitchesCategorySelectionIfNoMatchesFound() throws Exception {
    myGuiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    NlPaletteFixture palette = layout.getPalette();

    // Start typing in the palette brings focus to the SearchTextField on the ToolWindow header
    JListFixture itemList = palette.getItemList("Layouts");
    itemList.pressAndReleaseKeys(KeyEvent.VK_A);

    // Continue typing in search field to select SearchView
    SearchTextFieldFixture searchTextFieldFixture = palette.getSearchTextField();
    searchTextFieldFixture.enterText("rc");

    // Test:
    JListFixture categoryList = palette.getCategoryList();
    assertThat(categoryList.selection()).asList().containsExactly("All Results");
    assertThat(itemList.contents()).isEqualTo(new String[]{"SearchView"});
  }

  @Test
  public void testEnterInSearchBoxCausesItemListToGainFocus() throws Exception {
    myGuiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    NlPaletteFixture palette = layout.getPalette();

    // Start typing in the palette brings focus to the SearchTextField on the ToolWindow header
    JListFixture itemList = palette.getItemList("Layouts");
    itemList.pressAndReleaseKeys(KeyEvent.VK_C);

    // Continue typing in search field to select ConstraintLayout
    SearchTextFieldFixture searchTextFieldFixture = palette.getSearchTextField();
    searchTextFieldFixture.enterText("onstraintL");

    // Press enter in search field. Expect the focus to move to the item list.
    searchTextFieldFixture.pressAndReleaseKeys(KeyEvent.VK_ENTER);

    // Test:
    assertThat(itemList.selection()).asList().containsExactly("ConstraintLayout");
    assertThat(itemList.target().hasFocus()).isTrue();
  }

  @Test
  public void clickToDownloadMissingDependency() throws Exception {
    myGuiTest.importSimpleApplication();

    // Open file as XML and switch to design tab, wait for successful render
    EditorFixture editor = myGuiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/layout/activity_my.xml", EditorFixture.Tab.DESIGN);

    NlEditorFixture layout = editor.getLayoutEditor(false);
    layout.waitForRenderToFinish();
    NlPaletteFixture palette = layout.getPalette();

    // Click on the download icon next to the "FloatingActionButton"
    palette.clickItem("Design", "FloatingActionButton", StudioIcons.LayoutEditor.Extras.PALETTE_DOWNLOAD.getIconWidth() / 2);

    // Test: Check that the "Add Project Dependency" dialog is presented
    MessagesFixture dependencyDialog = MessagesFixture.findByTitle(myGuiTest.robot(), "Add Project Dependency");
    dependencyDialog.clickCancel();
  }
}
