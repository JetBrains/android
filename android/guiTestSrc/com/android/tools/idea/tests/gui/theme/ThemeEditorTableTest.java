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
import com.android.tools.idea.rendering.ResourceHelper;
import com.android.tools.idea.tests.gui.framework.BelongsToTestGroups;
import com.android.tools.idea.tests.gui.framework.GuiTestCase;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.IdeGuiTest;
import com.android.tools.idea.tests.gui.framework.fixture.*;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import org.fest.assertions.Index;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.*;
import org.fest.swing.timing.Condition;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.*;

/**
 * UI tests regarding the attributes table of the theme editor
 */
@BelongsToTestGroups({THEME})
public class ThemeEditorTableTest extends GuiTestCase {
  @Test @IdeGuiTest
  public void testParentValueCell() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);
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
    JComboBoxFixture parentComboBox = new JComboBoxFixture(myRobot, myRobot.finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem(4);
    parentCellFixture.stopEditing();
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    // Selects a new parent
    final String newParent = "Theme.AppCompat.NoActionBar";

    parentCellFixture.startEditing();
    parentComboBox.selectItem(newParent);
    parentCellFixture.stopEditing();
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    myProjectFrame.invokeMenuPathRegex("Edit", "Undo.*");
    assertEquals("android:Theme.Holo.Light.DarkActionBar", themeEditorTable.getComboBoxSelectionAt(parentCell));

    myProjectFrame.invokeMenuPathRegex("Edit", "Redo.*");
    assertEquals(newParent, themeEditorTable.getComboBoxSelectionAt(parentCell));

    pause(new Condition("Wait for potential tooltips to disappear") {
      @Override
      public boolean test() {
        return myRobot.findActivePopupMenu() == null;
      }
    });
    testParentPopup(themeEditorTable.cell(parentCell), newParent, themeEditor);

    myProjectFrame.invokeMenuPath("Window", "Editor Tabs", "Select Previous Tab");
    EditorFixture editor = myProjectFrame.getEditor();
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
    pause(new Condition("Waiting for parent to load") {
      @Override
      public boolean test() {
        // Cannot use themesComboBox.selectedItem() here
        // because the parent theme is not necessarily one of the themes present in the combobox model
        return parentName.equals(themesComboBox.target().getSelectedItem().toString());
      }
    }, GuiTests.SHORT_TIMEOUT);
  }

  @Test @IdeGuiTest
  public void testResourcePickerNameError() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);

    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (1,0) should be some color
    JTableCellFixture colorCell = themeEditorTable.cell(row(1).column(0));

    // click on a color
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(myRobot, (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
    JTextComponentFixture name = dialog.getNameTextField();

    // add mistake into name field
    String badText = "(";
    name.deleteText();
    name.enterText("color" + badText);
    String text = name.text();
    assertNotNull(text);
    assertTrue(text.endsWith(badText));

    final String expectedError = "<html><font color='#ff0000'><left>'" + badText +
                                 "' is not a valid resource name character</left></b></font></html>";
    pause(new Condition("Waiting for error to update") {
      @Override
      public boolean test() {
        return dialog.getError().equals(expectedError);
      }
    }, GuiTests.SHORT_TIMEOUT);

    dialog.clickCancel();
    colorCell.cancelEditing();
  }

  @Test @IdeGuiTest
  public void testSettingColorAttribute() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/holo_light_primary", themeEditorTable.valueAt(cell));

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(myRobot, (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
    Color color = new Color(200, 0, 0, 200);
    dialog.getColorPicker().setColorWithIntegers(color);
    dialog.clickOK();
    colorCell.stopEditing();

    cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));

    EditorFixture editor = myProjectFrame.getEditor();
    editor.open("app/src/main/res/values/colors.xml");
    editor.moveTo(editor.findOffset(null, "holo", true));
    assertEquals("<color name=\"^holo_light_primary\">" + ResourceHelper.colorToString(color) + "</color>",
                 editor.getCurrentLineContents(true, true, 0));
  }

  /**
   * Test that the alpha slider and the textfield are hidden when we are not in ARGB.
   */
  @Test @IdeGuiTest
  public void testColorPickerAlpha() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(myRobot, (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
    ColorPickerFixture colorPicker = dialog.getColorPicker();
    Color color = new Color(200, 0, 0, 200);
    colorPicker.setFormat("ARGB");
    colorPicker.setColorWithIntegers(color);
    JTextComponentFixture alphaLabel = colorPicker.getLabel("A:");
    SlideFixture alphaSlide = colorPicker.getAlphaSlide();
    alphaLabel.requireVisible();
    alphaSlide.requireVisible();
    colorPicker.setFormat("RGB");
    alphaLabel.requireNotVisible();
    alphaSlide.requireNotVisible();
    colorPicker.setFormat("HSB");
    alphaLabel.requireNotVisible();
    alphaSlide.requireNotVisible();

    dialog.clickOK();
    colorCell.stopEditing();
  }

  /**
   * Test creating a new state list and setting it as a style attribute value with the state list picker.
   */
  @Test @IdeGuiTest
  public void testStateListPicker() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell parentCell = row(0).column(0);
    JTableCellFixture parentCellFixture = themeEditorTable.cell(parentCell);

    // Selects AppCompat as parent
    Component parentEditor = parentCellFixture.editor();
    parentCellFixture.startEditing();
    assertTrue(parentEditor instanceof JComponent);
    JComboBoxFixture parentComboBox = new JComboBoxFixture(myRobot, myRobot.finder().findByType((JComponent)parentEditor, JComboBox.class));
    parentComboBox.selectItem("Theme.AppCompat.NoActionBar");
    parentCellFixture.stopEditing();

    TableCell cell = row(8).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/primary_text_material_dark", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(myRobot, (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(myRobot);
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
    ChooseResourceDialogFixture secondDialog = ChooseResourceDialogFixture.find(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return (component.isShowing() && !component.equals(dialog.target()));
      }
    });
    secondDialog.getColorPicker().setColorWithIntegers(new Color(200, 0, 0, 200));
    secondDialog.clickOK();
    pause(new Condition("Waiting for component update") {
      @Override
      public boolean test() {
        return "@color/primary_text_default_material_dark".equals(state0.getValue());
      }
    });

    dialog.focus();
    state0.getAlphaComponent().getSwatchButton().click();
    secondDialog = ChooseResourceDialogFixture.find(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return (component.isShowing() && !component.equals(dialog.target()));
      }
    });
    secondDialog.getList(ChooseResourceDialog.APP_NAMESPACE_LABEL).clickItem("abc_disabled_alpha_material_dark");
    secondDialog.focus();
    secondDialog.clickOK();
    pause(new Condition("Waiting for component update") {
      @Override
      public boolean test() {
        return "@dimen/abc_disabled_alpha_material_dark".equals(state0.getAlphaValue());
      }
    });

    dialog.focus();
    state1.getValueComponent().getSwatchButton().click();
    secondDialog = ChooseResourceDialogFixture.find(myRobot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog component) {
        return (component.isShowing() && !component.equals(dialog.target()));
      }
    });
    secondDialog.getColorPicker().setColorWithIntegers(new Color(0, 200, 0, 255));
    secondDialog.clickOK();
    pause(new Condition("Waiting for component update") {
      @Override
      public boolean test() {
        return "@color/primary_text_default_material_dark".equals(state1.getValue());
      }
    });

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
  @Test @IdeGuiTest
  public void testResourceCompletion() throws IOException {
    myProjectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(myProjectFrame);
    final ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    final TableCell cell = row(3).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireNotBold();
    assertEquals("android:colorBackground", themeEditorTable.attributeNameAt(cell));
    assertEquals("@android:color/background_holo_light", themeEditorTable.valueAt(cell));

    JTableCellFixture tableCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(myRobot, (ResourceComponent)tableCell.editor());
    tableCell.startEditing();
    EditorTextFieldFixture textComponent = resourceComponent.getTextField();
    textComponent.requireText("@android:color/background_holo_light");
    textComponent.enterText("invalid");
    tableCell.stopEditing();
    pause(new Condition("Waiting for warning icon to be loaded") {
      @Override
      public boolean test() {
        return themeEditorTable.hasWarningIconAt(cell);
      }
    }, GuiTests.SHORT_TIMEOUT);

    tableCell.startEditing();
    textComponent = resourceComponent.getTextField();
    String prefix = "@android:color/back";
    textComponent.replaceText(prefix);

    JListFixture completionPopup = ThemeEditorGuiTestUtils.getCompletionPopup(myRobot);
    String[] suggestions = completionPopup.contents();
    assertTrue(suggestions.length > 0);
    for (String suggestion : suggestions) {
      assertTrue(suggestion.startsWith(prefix));
    }

    prefix = "@color/back";
    textComponent.replaceText(prefix);
    completionPopup = ThemeEditorGuiTestUtils.getCompletionPopup(myRobot);
    suggestions = completionPopup.contents();
    assertTrue(suggestions.length > 0);
    for (String suggestion : suggestions) {
      assertTrue(suggestion.startsWith(prefix));
    }

    completionPopup.item(0).doubleClick();
    tableCell.stopEditing();
    assertEquals(suggestions[0], themeEditorTable.valueAt(cell));
  }
}
