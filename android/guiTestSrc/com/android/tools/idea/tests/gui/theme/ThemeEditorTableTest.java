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

import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import org.fest.assertions.Index;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests regarding the attributes table of the theme editor
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ThemeEditorTableTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testParentValueCell() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (0,0) should be the parent editor
    TableCell parentCell = row(0).column(0);


    List<String> parentsList = themeEditorTable.getComboBoxContentsAt(parentCell);
    // The expected elements are:
    // 0. Holo Light
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. -- Separator
    // 5. Show all themes
    assertNotNull(parentsList);
    assertThat(parentsList)
      .hasSize(6)
      .contains("android:Theme.Holo.Light.DarkActionBar", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(5));

    assertThat(parentsList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(4)).startsWith("javax.swing.JSeparator");

    JTableCellFixture parentCellFixture = themeEditorTable.cell(parentCell);
    parentCellFixture.requireEditable();

    // Checks that selecting a separator does nothing
    Component parentEditor = parentCellFixture.editor();
    parentCellFixture.startEditing();
    assertTrue(parentEditor instanceof JComponent);
    JComboBoxFixture parentComboBox = new JComboBoxFixture(guiTest.robot(), guiTest
      .robot().finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem(4);
    parentCellFixture.stopEditing();
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    // Selects a new parent
    final String newParent = "Theme.AppCompat.NoActionBar";

    parentCellFixture.startEditing();
    parentComboBox.selectItem(newParent);
    parentCellFixture.stopEditing();
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    guiTest.ideFrame().invokeMenuPathRegex("Edit", "Undo.*");
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    guiTest.ideFrame().invokeMenuPathRegex("Edit", "Redo.*");
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    Wait.seconds(30).expecting("potential tooltips to disappear").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return guiTest.robot().findActivePopupMenu() == null;
      }
    });
    testParentPopup(themeEditorTable.cell(parentCell), newParent, themeEditor);

    guiTest.ideFrame().invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.moveTo(editor.findOffset(null, "AppTheme", true));
    assertEquals("<style name=\"^AppTheme\" parent=\"Theme.AppCompat.NoActionBar\">",
                        editor.getCurrentLineContents(true, true, 0));
  }

  private static void testParentPopup(@NotNull JTableCellFixture cell, @NotNull final String parentName,
                                      @NotNull ThemeEditorFixture themeEditor) {
    JPopupMenuFixture popupMenu = cell.showPopupMenu();
    String[] menuLabels = popupMenu.menuLabels();
    assertEquals(1, menuLabels.length);
    JMenuItemFixture edit = popupMenu.menuItemWithPath("Go To Declaration");
    edit.requireVisible();
    edit.click();

    final JComboBoxFixture themesComboBox = themeEditor.getThemesComboBox();
    Wait.minutes(2).expecting("parent to load").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        // Cannot use themesComboBox.selectedItem() here
        // because the parent theme is not necessarily one of the themes present in the combobox model
        return parentName.equals(themesComboBox.target().getSelectedItem().toString());
      }
    });
  }

  @Ignore("Too flaky")
  @Test
  public void testSettingColorAttribute() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/holo_light_primary", themeEditorTable.valueAt(cell));

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    Color color = new Color(200, 0, 0, 200);
    dialog.getColorPicker().setColorWithIntegers(color);
    dialog.clickOK();
    colorCell.stopEditing();

    themeEditorTable.requireValueAt(cell, "@color/holo_light_primary");
    cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.open("app/src/main/res/values/colors.xml");
    editor.moveTo(editor.findOffset(null, "holo", true));
    assertEquals("<color name=\"^holo_light_primary\">" + ResourceHelper.colorToString(color) + "</color>",
                 editor.getCurrentLineContents(true, true, 0));
  }

  /**
   * Test creating a new state list and setting it as a style attribute value with the state list picker.
   */
  @Test
  public void testStateListPicker() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell parentCell = row(0).column(0);
    JTableCellFixture parentCellFixture = themeEditorTable.cell(parentCell);

    // Selects AppCompat as parent
    Component parentEditor = parentCellFixture.editor();
    parentCellFixture.startEditing();
    assertTrue(parentEditor instanceof JComponent);
    JComboBoxFixture parentComboBox = new JComboBoxFixture(guiTest.robot(), guiTest
      .robot().finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem("Theme.AppCompat.NoActionBar");
    parentCellFixture.stopEditing();

    TableCell cell = row(8).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/primary_text_material_dark", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    StateListPickerFixture stateListPicker = dialog.getStateListPicker();
    List<StateListComponentFixture> states = stateListPicker.getStateComponents();
    assertThat(states).hasSize(2);

    final StateListComponentFixture state0 = states.get(0);
    assertEquals("Not enabled", state0.getStateName());
    assertEquals("@android:color/primary_text_default_material_dark", state0.getValue());
    assertTrue(state0.isAlphaVisible());
    assertEquals("@android:dimen/disabled_alpha_material_dark", state0.getAlphaValue());

    final StateListComponentFixture state1 = states.get(1);
    assertEquals("Default", state1.getStateName());
    assertEquals("@android:color/primary_text_default_material_dark", state1.getValue());
    assertFalse(state1.isAlphaVisible());

    dialog.focus();
    state0.getValueComponent().getSwatchButton().click();
    ChooseResourceDialogFixture secondDialog = ChooseResourceDialogFixture.find(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return !component.equals(dialog.target());
      }
    });
    secondDialog.getColorPicker().setColorWithIntegers(new Color(200, 0, 0, 200));
    secondDialog.clickOK();
    Wait.seconds(30).expecting("component update").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return "@color/primary_text_default_material_dark".equals(state0.getValue());
      }
    });

    dialog.focus();
    state0.getAlphaComponent().getSwatchButton().click();
    secondDialog = ChooseResourceDialogFixture.find(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return !component.equals(dialog.target());
      }
    });
    secondDialog.getList(ChooseResourceDialog.APP_NAMESPACE_LABEL).clickItem("abc_disabled_alpha_material_dark");
    secondDialog.focus();
    secondDialog.clickOK();
    Wait.seconds(30).expecting("component update").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return "@dimen/abc_disabled_alpha_material_dark".equals(state0.getAlphaValue());
      }
    });

    dialog.focus();
    state1.getValueComponent().getSwatchButton().click();
    secondDialog = ChooseResourceDialogFixture.find(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return !component.equals(dialog.target());
      }
    });
    secondDialog.getColorPicker().setColorWithIntegers(new Color(0, 200, 0, 255));
    secondDialog.clickOK();
    Wait.seconds(30).expecting("component update").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return "@color/primary_text_default_material_dark".equals(state1.getValue());
      }
    });

    dialog.requireNoError();

    dialog.focus();
    dialog.clickOK();
    stateListCell.stopEditing();

    cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/primary_text_material_dark", themeEditorTable.valueAt(cell));
  }

  /**
   * Test the text completion for attribute values
   */
  @Ignore("Too flaky")
  @Test
  public void testResourceCompletion() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    final ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    final TableCell cell = row(3).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:colorBackground", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/background_holo_light", themeEditorTable.valueAt(cell));

    JTableCellFixture tableCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)tableCell.editor());
    tableCell.startEditing();
    EditorTextFieldFixture textComponent = resourceComponent.getTextField();
    textComponent.requireText("@android:color/background_holo_light");
    textComponent.enterText("invalid");
    tableCell.stopEditing();
    Wait.minutes(2).expecting("warning icon to be loaded").until(new Wait.Objective() {
      @Override
      public boolean isMet() {
        return themeEditorTable.hasWarningIconAt(cell);
      }
    });

    tableCell.startEditing();
    textComponent = resourceComponent.getTextField();
    String prefix = "@android:color/back";
    textComponent.replaceText(prefix);

    JListFixture completionPopup = ThemeEditorGuiTestUtils.getCompletionPopup(guiTest.robot());
    String[] suggestions = completionPopup.contents();
    assertTrue(suggestions.length > 0);
    for (String suggestion : suggestions) {
      assertTrue(suggestion.startsWith(prefix));
    }

    prefix = "@color/back";
    textComponent.replaceText(prefix);
    completionPopup = ThemeEditorGuiTestUtils.getCompletionPopup(guiTest.robot());
    suggestions = completionPopup.contents();
    assertTrue(suggestions.length > 0);
    for (String suggestion : suggestions) {
      assertTrue(suggestion.startsWith(prefix));
    }

    completionPopup.item(0).doubleClick();
    tableCell.stopEditing();
    assertEquals(suggestions[0], themeEditorTable.valueAt(cell));
  }

  /**
   * @see com.android.tools.idea.editors.theme.ThemeEditorTable#getPopupMenuAtCell(int, int)
   */
  @Ignore("Too flaky")
  @Test
  public void testResettingColorAttribute() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    final ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    final TableCell cell = row(1).column(0);
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/holo_light_primary", themeEditorTable.valueAt(cell));

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    Color color = new Color(200, 0, 0, 200);
    dialog.getColorPicker().setColorWithIntegers(color);
    dialog.clickOK();

    themeEditorTable.requireValueAt(cell, "@color/holo_light_primary");

    colorCell.startEditing();
    JPopupMenuFixture popupMenu = resourceComponent.showPopupMenu();

    popupMenu.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Reset value".equals(component.getText());
      }
    }).click();

    themeEditorTable.requireValueAt(cell, "@android:color/holo_light_primary");
  }

  /**
   * @see com.android.tools.idea.editors.theme.attributes.ShowJavadocAction
   */
  @Test
  public void testShowDocumentation() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/holo_light_primary", themeEditorTable.valueAt(cell));

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    JPopupMenuFixture popupMenu = resourceComponent.showPopupMenu();

    popupMenu.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Show documentation".equals(component.getText());
      }
    }).click();

    JWindow docWindow = GuiTests.waitUntilShowing(guiTest.robot(), GuiTests.matcherForType(JWindow.class));

    JEditorPane docComp = guiTest.robot().finder().findByType(docWindow, JEditorPane.class);
    JTextComponentFixture quickDoc = new JTextComponentFixture(guiTest.robot(), docComp);

    String expected = "<html>\n" +
                      "  <head>\n" +
                      "    <font size=\"3\">\n" +
                      "</font>  </head>\n" +
                      "  <body>\n" +
                      "    <b><font size=\"3\">android:colorPrimary</font></b><font size=\"3\"> (Added in \n" +
                      "    API level 21)<br>The primary branding color for the app. By default, this \n" +
                      "    is the color applied to the action bar background.<br><hr>\n" +
                      "</font>\n" +
                      "    <table border=\"0\" align=\"center\" style=\"background-color: rgb(230,230,230); width: 200px\">\n" +
                      "      <tr height=\"100\">\n" +
                      "        <td align=\"center\" valign=\"middle\" height=\"100\">\n" +
                      "          <font size=\"3\">#e6e6e6\n" +
                      "</font>        </td>\n" +
                      "      </tr>\n" +
                      "    </table>\n" +
                      "    <font size=\"3\"><br>\n" +
                      "    <br>\n" +
                      "    ?android:attr/colorPrimary =&gt; @color/holo_light_primary =&gt; #ffe6e6e6<br><br></font>\n" +
                      "  </body>\n" +
                      "</html>\n";

    quickDoc.requireText(expected);
  }
}
