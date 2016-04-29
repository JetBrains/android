/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.theme;

import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.RenameRefactoringDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ThemeSelectionDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewStyleDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorTableFixture;
import com.google.common.collect.ImmutableList;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Index.atIndex;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests for the theme selector in the Theme Editor
 */
@BelongsToTestGroups({THEME})
public class ThemeSelectorTest extends GuiTestCase {
  /**
   * Tests the theme renaming functionality of the theme selector
   * and that IntelliJ's Undo works can revert this action
   */
  @Test @IdeGuiTest
  public void testRenameTheme() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);

    final JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    themesComboBox.selectItem("Rename AppTheme");

    RenameRefactoringDialogFixture renameRefactoringDialog = RenameRefactoringDialogFixture.find(myRobot);
    renameRefactoringDialog.setNewName("NewAppTheme").clickRefactor();

    themeEditor.waitForThemeSelection("NewAppTheme");

    themesComboBox.requireSelection("NewAppTheme");

    List<String> themeList = themeEditor.getThemesList();
    assertThat(themeList)
      .hasSize(8)
      .contains("NewAppTheme", atIndex(0))
      .contains("Rename NewAppTheme", atIndex(7));

    themesComboBox.selectItem("Theme.AppCompat.NoActionBar"); // AppCompat is read-only, being a library theme

    themeList = themeEditor.getThemesList();
    assertThat(themeList)
      .hasSize(7)
      .contains("NewAppTheme", atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", atIndex(3))
      .contains("Show all themes", atIndex(4))
      .contains("Create New Theme", atIndex(6));

    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = myProjectFrame.getEditor();
    assertEquals(-1, editor.findOffset(null, "name=\"AppTheme", true));
    editor.moveTo(editor.findOffset(null, "name=\"NewAppTheme", true));
    assertEquals("<style ^name=\"NewAppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">",
                 editor.getCurrentLineContents(true, true, 0));

    // Testing Undo
    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Next Tab");
    themesComboBox.selectItem("NewAppTheme");
    myProjectFrame.invokeMenuPathRegex("Edit", "Undo.*");
    DialogFixture message = new DialogFixture(myRobot, myRobot.finder().findByType(Dialog.class));
    message.focus();
    JButton OkButton = GuiTests.waitUntilFound(myRobot, message.target(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        if (buttonText != null) {
          return "OK".equals(buttonText.trim()) && button.isShowing();
        }
        return false;
      }
    });
    JButtonFixture OkFixture = new JButtonFixture(myRobot, OkButton);
    OkFixture.click();
    themeEditor.waitForThemeSelection("AppTheme");
    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    assertEquals(-1, editor.findOffset(null, "name=\"NewAppTheme", true));
    editor.moveTo(editor.findOffset(null, "name=\"AppTheme", true));
    assertEquals("<style ^name=\"AppTheme\" parent=\"android:Theme.Holo.Light.DarkActionBar\">",
                 editor.getCurrentLineContents(true, true, 0));
  }

  /**
   * Tests the Show all themes dialog from the theme selector
   */
  @Test @IdeGuiTest
  public void testShowAllThemes() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);

    JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    String selectedTheme = themesComboBox.selectedItem();

    themesComboBox.selectItem("Show all themes");
    ThemeSelectionDialogFixture themeSelectionDialog = ThemeSelectionDialogFixture.find(myRobot);
    themeSelectionDialog.clickCancel();
    themesComboBox.requireSelection(selectedTheme);

    themesComboBox.selectItem("Show all themes");
    themeSelectionDialog = ThemeSelectionDialogFixture.find(myRobot);
    JTreeFixture categoriesTree = themeSelectionDialog.getCategoriesTree();
    JListFixture themeList = themeSelectionDialog.getThemeList();

    categoriesTree.focus(); // Acquire focus if the window manager did not give it
    categoriesTree.clickRow(1);
    assertEquals("Manifest Themes", categoriesTree.node(1).value());
    assertThat(ImmutableList.copyOf(themeList.contents())).hasSize(1)
      .contains("AppTheme", atIndex(0));

    categoriesTree.clickRow(2);
    assertEquals("Project Themes", categoriesTree.node(2).value());
    assertThat(ImmutableList.copyOf(themeList.contents())).hasSize(1)
      .contains("AppTheme", atIndex(0));

    categoriesTree.clickRow(10);
    assertEquals("All", categoriesTree.node(10).value());
    assertThat(ImmutableList.copyOf(themeList.contents())).contains("android:Theme", atIndex(0));
    themeList.selectItem("Theme.AppCompat.NoActionBar");
    themeSelectionDialog.clickOk();

    themeEditor.waitForThemeSelection("Theme.AppCompat.NoActionBar");
  }

  /**
   * Tests the theme creation functionality of the theme selector
   * and that IntelliJ's Undo can revert this action
   */
  @Test @IdeGuiTest
  public void testCreateNewTheme() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);

    JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    String selectedTheme = themesComboBox.selectedItem();
    assertNotNull(selectedTheme);

    themesComboBox.selectItem("Create New Theme");
    NewStyleDialogFixture newStyleDialog = NewStyleDialogFixture.find(myRobot);
    newStyleDialog.clickCancel();
    themeEditor.waitForThemeSelection(selectedTheme);

    themesComboBox.selectItem("Create New Theme");
    newStyleDialog = NewStyleDialogFixture.find(myRobot);
    JTextComponentFixture newNameTextField = newStyleDialog.getNewNameTextField();
    JComboBoxFixture parentComboBox = newStyleDialog.getParentComboBox();

    parentComboBox.requireSelection("AppTheme");
    ImmutableList<String> parentsList = ImmutableList.copyOf(parentComboBox.contents());
    // The expected elements are:
    // 0. AppTheme
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. -- Separator
    // 5. Show all themes
    assertThat(parentsList).hasSize(6)
      .contains("AppTheme", atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", atIndex(3))
      .contains("Show all themes", atIndex(5));
    assertThat(parentsList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(4)).startsWith("javax.swing.JSeparator");

    parentComboBox.selectItem("Theme.AppCompat.NoActionBar");
    newNameTextField.requireText("Theme.AppTheme.NoActionBar");

    parentComboBox.selectItem("Show all themes");
    ThemeSelectionDialogFixture themeSelectionDialog = ThemeSelectionDialogFixture.find(myRobot);
    JTreeFixture categoriesTree = themeSelectionDialog.getCategoriesTree();
    JListFixture themeList = themeSelectionDialog.getThemeList();

    categoriesTree.clickRow(5);
    themeList.clickItem(0);
    themeList.requireSelection("android:Theme.Holo");
    themeSelectionDialog.clickOk();

    parentComboBox.requireSelection("android:Theme.Holo");
    newNameTextField.requireText("Theme.AppTheme");
    newNameTextField.deleteText().enterText("NewTheme");

    newStyleDialog.clickOk();
    themeEditor.waitForThemeSelection("NewTheme");
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();
    TableCell parentCell = row(0).column(0);
    assertEquals("android:Theme.Holo", themeEditorTable.getComboBoxSelectionAt(parentCell));

    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = myProjectFrame.getEditor();
    assertNotEquals(-1, editor.findOffset(null, "name=\"AppTheme", true));
    editor.moveTo(editor.findOffset(null, "name=\"NewTheme", true));
    assertEquals("<style ^name=\"NewTheme\" parent=\"android:Theme.Holo\" />",
                 editor.getCurrentLineContents(true, true, 0));

    // Tests Undo
    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Next Tab");
    myProjectFrame.invokeMenuPathRegex("Edit", "Undo.*");
    themeEditor.waitForThemeSelection("AppTheme");
    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    assertEquals(-1, editor.findOffset(null, "name=\"NewTheme", true));
  }


  /**
   * Tests that we can remove AppCompat and the themes update correctly.
   * Test that we can open the simple application and the theme editor opens correctly.
   */
  @Ignore("failed in http://go/aj/job/studio-ui-test/326 and from IDEA")
  @Test @IdeGuiTest
  public void testRemoveAppCompat() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(projectFrame);
    List<String> themeList = themeEditor.getThemesList();
    assertThat(themeList).contains("Theme.AppCompat.Light.NoActionBar");

    EditorFixture editor = projectFrame.getEditor();

    // TODO: Make the test work without having to close the theme editor
    editor.close();

    editor.open("app/build.gradle");

    editor.moveTo(editor.findOffset("compile 'com.android.support:app", null, true));
    editor.invokeAction(EditorFixture.EditorAction.DELETE_LINE);
    editor.invokeAction(EditorFixture.EditorAction.SAVE);

    projectFrame.requireEditorNotification("Gradle files have changed since last project sync").performAction("Sync Now");
    projectFrame.waitForGradleProjectSyncToFinish();

    // Check AppCompat themes are gone
    themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(projectFrame);
    themeList = themeEditor.getThemesList();
    assertThat(themeList)
      .excludes("Theme.AppCompat.Light.NoActionBar")
      .contains("android:Theme.Material.NoActionBar")
      .contains("android:Theme.Material.Light.NoActionBar");
  }
}
