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
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTestRunner;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.Wait;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ColorPickerFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SlideFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.*;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.FontFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.awt.*;
import java.io.IOException;

import static com.google.common.truth.Truth.assertThat;
import static org.fest.swing.data.TableCell.row;
import static org.junit.Assert.*;

/**
 * UI tests regarding the ChooseResourceDialog
 */
@RunIn(TestGroup.THEME)
@RunWith(GuiTestRunner.class)
public class ChooseResourceDialogTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule();

  @Test
  public void testColorStateList() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(7).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:textColorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/text_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());

    StateListPickerFixture stateListPicker = dialog.getStateListPicker();
    java.util.List<StateListComponentFixture> states = stateListPicker.getStateComponents();
    assertThat(states).hasSize(4);

    final StateListComponentFixture state0 = states.get(0);
    assertEquals("Not enabled", state0.getStateName());
    assertEquals("?android:attr/colorForeground", state0.getValue());
    assertFalse(state0.getValueComponent().hasWarningIcon());
    assertTrue(state0.isAlphaVisible());
    assertEquals("@dimen/text_alpha", state0.getAlphaValue());
    assertFalse(state0.getAlphaComponent().hasWarningIcon());

    final StateListComponentFixture state1 = states.get(1);
    assertEquals("Checked", state1.getStateName());
    assertEquals("#5034FAB2", state1.getValue());
    assertFalse(state1.getValueComponent().hasWarningIcon());
    assertFalse(state1.isAlphaVisible());

    final StateListComponentFixture state2 = states.get(2);
    assertEquals("Pressed", state2.getStateName());
    assertEquals("@color/invalidColor", state2.getValue());
    assertTrue(state2.getValueComponent().hasWarningIcon());
    assertFalse(state2.isAlphaVisible());

    final StateListComponentFixture state3 = states.get(3);
    assertEquals("Default", state3.getStateName());
    assertEquals("?attr/myColorAttribute", state3.getValue());
    assertFalse(state3.getValueComponent().hasWarningIcon());
    assertFalse(state3.isAlphaVisible());

    dialog.clickCancel();
    stateListCell.stopEditing();
  }

  @Test
  public void testEditColorReference() throws IOException {
    guiTest.importProjectAndWaitForProjectSyncToFinish("StateListApplication");
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    FontFixture cellFont = themeEditorTable.fontAt(cell);
    cellFont.requireBold();
    assertEquals("android:colorPrimary", themeEditorTable.attributeNameAt(cell));
    assertEquals("@color/ref_color", themeEditorTable.valueAt(cell));

    JTableCellFixture stateListCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)stateListCell.editor());
    stateListCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());

    SwatchComponentFixture state1 = dialog.getEditReferencePanel().getSwatchComponent();
    assertEquals("@color/myColor", state1.getText());
    assertFalse(state1.hasWarningIcon());

    dialog.clickCancel();
    stateListCell.stopEditing();
  }

  @Test
  public void testResourcePickerNameError() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());

    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    // Cell (1,0) should be some color
    JTableCellFixture colorCell = themeEditorTable.cell(row(1).column(0));

    // click on a color
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    final ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
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
    Wait.minutes(2).expecting("error to update").until(() -> dialog.getError().equals(expectedError));

    dialog.clickCancel();
    colorCell.cancelEditing();
  }

  /**
   * Test that the alpha slider and the textfield are hidden when we are not in ARGB.
   */
  @Test
  public void testColorPickerAlpha() throws IOException {
    guiTest.importSimpleApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    TableCell cell = row(1).column(0);

    JTableCellFixture colorCell = themeEditorTable.cell(cell);
    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();

    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
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
}
