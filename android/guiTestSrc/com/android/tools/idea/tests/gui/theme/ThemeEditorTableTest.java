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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.google.common.collect.ImmutableList;
import org.fest.assertions.Index;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JMenuItemFixture;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.JComboBox;
import java.awt.Component;
import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.TestGroup.THEME;
import static com.android.tools.idea.tests.gui.theme.ThemeEditorTest.openThemeEditor;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * UI tests regarding the attributes table of the theme editor
 */
@BelongsToTestGroups({THEME})
public class ThemeEditorTableTest extends GuiTestCase {
  @BeforeClass
  public static void runBeforeClass() {
    System.setProperty("enable.theme.editor", "true");
  }

  @Test @IdeGuiTest
  public void testParentLabelCell() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = openThemeEditor(projectFrame);

    JTableFixture themeEditorTable = themeEditor.getThemeEditorTable();
    assertNotNull(themeEditorTable);

    // Cell (0,0) should be Theme parent
    JTableCellFixture parentLabelCell = themeEditorTable.cell(row(0).column(0));
    final String parentName = themeEditorTable.valueAt(row(0).column(1));
    assertNotNull(parentName);
    parentLabelCell.requireNotEditable();
    parentLabelCell.requireValue("Theme Parent");

    testParentPopup(parentLabelCell, parentName, themeEditor);
  }

  @Test @IdeGuiTest
  public void testParentValueCell() throws IOException {
    IdeFrameFixture projectFrame = importSimpleApplication();
    ThemeEditorFixture themeEditor = openThemeEditor(projectFrame);

    JTableFixture themeEditorTable = themeEditor.getThemeEditorTable();
    assertNotNull(themeEditorTable);

    // Cell (0,1) should be the parent combobox
    JTableCellFixture parentValueCell = themeEditorTable.cell(row(0).column(1));
    parentValueCell.requireEditable();
    Component parentEditor = parentValueCell.editor();
    assertTrue(parentEditor instanceof JComboBox);
    JComboBoxFixture parentComboBox = new JComboBoxFixture(myRobot, (JComboBox)parentEditor);

    List<String> parentsList = ImmutableList.copyOf(parentComboBox.contents());
    // The expected elements are:
    // 0. Holo Light
    // 1. -- Separator
    // 2. AppCompat Light
    // 3. AppCompat
    // 4. -- Separator
    // 5. Show all themes
    assertThat(parentsList)
      .hasSize(6)
      .contains("Theme.Holo.Light.DarkActionBar", Index.atIndex(0))
      .contains("Theme.AppCompat.Light.NoActionBar", Index.atIndex(2))
      .contains("Theme.AppCompat.NoActionBar", Index.atIndex(3))
      .contains("Show all themes", Index.atIndex(5));

    assertThat(parentsList.get(1)).startsWith("javax.swing.JSeparator");
    assertThat(parentsList.get(4)).startsWith("javax.swing.JSeparator");

    // Checks that selecting a separator does nothing
    parentValueCell.click();

    parentValueCell.startEditing();
    parentComboBox.selectItem(4);
    parentValueCell.stopEditing();

    parentComboBox.requireSelection("Theme.Holo.Light.DarkActionBar");
    parentValueCell.requireValue("Theme.Holo.Light.DarkActionBar");

    // Selects a new parent
    final String newParent = "Theme.AppCompat.NoActionBar";
    parentValueCell.click();

    parentValueCell.startEditing();
    parentComboBox.selectItem(newParent);
    parentValueCell.stopEditing();

    parentValueCell.requireValue(newParent);

    pause(new Condition("Wait for potential tooltips to disappear") {
      @Override
      public boolean test() {
        return myRobot.findActivePopupMenu() == null;
      }
    });
    testParentPopup(parentValueCell, newParent, themeEditor);
  }

  private static void testParentPopup(@NotNull JTableCellFixture cell, @NotNull final String parentName,
                                      @NotNull ThemeEditorFixture themeEditor) {
    JPopupMenuFixture popupMenu = cell.showPopupMenu();
    String[] menuLabels = popupMenu.menuLabels();
    assertEquals(2, menuLabels.length);
    JMenuItemFixture definition = popupMenu.menuItemWithPath("Go to definition");
    JMenuItemFixture reset = popupMenu.menuItemWithPath("Reset value");
    reset.requireNotVisible();
    definition.requireVisible();
    definition.click();

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
}
