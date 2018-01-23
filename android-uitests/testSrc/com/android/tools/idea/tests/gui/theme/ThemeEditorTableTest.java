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
import com.android.tools.idea.tests.gui.framework.*;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests regarding the attributes table of the theme editor
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ThemeEditorTableTest {

  @Rule public final RenderTimeoutRule timeout = new RenderTimeoutRule(60, TimeUnit.SECONDS);
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
    // 2. Material Light
    // 3. Material
    // 4. AppCompat Light
    // 5. AppCompat
    // 6. -- Separator
    // 7. Show all themes
    assertThat(parentsList).hasSize(8);
    assertThat(parentsList.get(0)).isEqualTo("android:Theme.Holo.Light.DarkActionBar");
    assertThat(parentsList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(2)).isEqualTo("android:Theme.Material.Light.NoActionBar");
    assertThat(parentsList.get(3)).isEqualTo("android:Theme.Material.NoActionBar");
    assertThat(parentsList.get(4)).isEqualTo("Theme.AppCompat.Light.NoActionBar");
    assertThat(parentsList.get(5)).isEqualTo("Theme.AppCompat.NoActionBar");
    assertThat(parentsList.get(6)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(7)).isEqualTo("Show all themes");

    JTableCellFixture parentCellFixture = themeEditorTable.cell(parentCell);
    parentCellFixture.requireEditable();

    // Checks that selecting a separator does nothing
    Component parentEditor = parentCellFixture.editor();
    parentCellFixture.startEditing();
    JComboBoxFixture parentComboBox = new JComboBoxFixture(guiTest.robot(), guiTest
      .robot().finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem(6);
    parentCellFixture.stopEditing();
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    // Selects a new parent
    final String newParent = "Theme.AppCompat.NoActionBar";

    parentCellFixture.startEditing();
    parentComboBox.selectItem(newParent);
    parentCellFixture.stopEditing();
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    guiTest.ideFrame().invokeMenuPath("Edit", "Undo Updating Parent to Theme.AppCo...");
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    guiTest.ideFrame().invokeMenuPath("Edit", "Redo Updating Parent to Theme.AppCo...");
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    Wait.seconds(1).expecting("potential tooltips to disappear").until(() -> guiTest.robot().findActivePopupMenu() == null);
    testParentPopup(themeEditorTable.cell(parentCell), newParent, themeEditor);

    guiTest.ideFrame().invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.moveBetween("", "AppTheme");
    assertThat(editor.getCurrentLine().trim()).isEqualTo("<style name=\"AppTheme\" parent=\"Theme.AppCompat.NoActionBar\">");
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
    Wait.seconds(1).expecting("parent to load")
      // Cannot use themesComboBox.selectedItem() here
      // because the parent theme is not necessarily one of the themes present in the combobox model
      .until(() -> parentName.equals(themesComboBox.target().getSelectedItem().toString()));
  }

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
    editor.moveBetween("", "holo");
    assertThat(editor.getCurrentLine().trim())
      .isEqualTo("<color name=\"holo_light_primary\">" + ResourceHelper.colorToString(color) + "</color>");
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
    JComboBoxFixture parentComboBox = new JComboBoxFixture(guiTest.robot(), guiTest
      .robot().finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem("Theme.AppCompat.NoActionBar");
    parentCellFixture.stopEditing();

    TableCell cell = row(8).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/text_color_primary", themeEditorTable.valueAt(cell));

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
    assertEquals("?android:attr/colorForeground", state0.getValue());
    assertTrue(state0.isAlphaVisible());
    assertEquals("?android:attr/disabledAlpha", state0.getAlphaValue());

    final StateListComponentFixture state1 = states.get(1);
    assertEquals("Default", state1.getStateName());
    assertEquals("?android:attr/colorForeground", state1.getValue());
    assertTrue(state1.isAlphaVisible());
    assertEquals("?android:attr/primaryContentAlpha", state1.getAlphaValue());

    state0.getValueComponent().getSwatchButton().click();
    ChooseResourceDialogFixture secondDialog = ChooseResourceDialogFixture.find(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return !component.equals(dialog.target());
      }
    });
    secondDialog.clickNewResource().menuItemWithPath("New color Value...").click();
    secondDialog.getColorPicker().setColorWithIntegers(new Color(200, 0, 0, 200));
    secondDialog.getNameTextField().enterText("myColor");
    secondDialog.clickOK();
    Wait.seconds(1).expecting("component update").until(() -> "@color/myColor".equals(state0.getValue()));

    state1.getAlphaComponent().getSwatchButton().click();
    secondDialog = ChooseResourceDialogFixture.find(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return !component.equals(dialog.target());
      }
    });
    secondDialog.getResourceNameTable().cell("android:disabledAlpha").click();
    secondDialog.clickOK();
    Wait.seconds(1).expecting("component update").until(() -> "?android:attr/disabledAlpha".equals(state1.getAlphaValue()));

    dialog.requireNoError();

    dialog.clickOK();
    stateListCell.stopEditing();

    cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/text_color_primary", themeEditorTable.valueAt(cell));
  }

  /**
   * @see com.android.tools.idea.editors.theme.ThemeEditorTable#getPopupMenuAtCell(int, int)
   */
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

    popupMenu.menuItemWithPath("Reset value").click();

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

    popupMenu.menuItemWithPath("Show documentation").click();

    JWindow docWindow = GuiTests.waitUntilShowing(guiTest.robot(), Matchers.byType(JWindow.class));

    JEditorPane docComp = guiTest.robot().finder().findByType(docWindow, JEditorPane.class);
    JTextComponentFixture quickDoc = new JTextComponentFixture(guiTest.robot(), docComp);

    String expected = "<html>\n" +
                      "  <head>\n" +
                      "    \n" +
                      "  </head>\n" +
                      "  <body>\n" +
                      "    <b>android:colorPrimary</b> (Added in API level 21)<br>The primary \n" +
                      "    branding color for the app. By default, this is the color applied to the \n" +
                      "    action bar background.<br><hr>\n" +
                      "\n" +
                      "    <table border=\"0\" align=\"center\" style=\"background-color: rgb(230,230,230); width: 200px\">\n" +
                      "      <tr height=\"100\">\n" +
                      "        <td align=\"center\" valign=\"middle\" height=\"100\">\n" +
                      "          #e6e6e6\n" +
                      "        </td>\n" +
                      "      </tr>\n" +
                      "    </table>\n" +
                      "    <br>\n" +
                      "    <br>\n" +
                      "    ?android:attr/colorPrimary =&gt; @color/holo_light_primary =&gt; #ffe6e6e6<br><br>\n" +
                      "  </body>\n" +
                      "</html>\n";

    quickDoc.requireText(expected);
  }

  @Test
  public void testTabSwitchingWhileEditing() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    final ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    final TableCell cell = row(3).column(0);
    JTableCellFixture tableCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)tableCell.editor());
    tableCell.startEditing();
    EditorTextFieldFixture textComponent = resourceComponent.getTextField();
    textComponent.enterText("text");

    EditorFixture editor = guiTest.ideFrame().getEditor();
    editor.switchToTab("styles.xml");
    editor.switchToTab("Theme Editor");
  }
}
