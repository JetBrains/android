/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.layout;

import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.propertyTable.AttributeProperty;
import com.intellij.designer.model.Property;
import com.intellij.designer.propertyTable.RadPropertyTable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import org.fest.swing.core.MouseButton;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.driver.ComponentDriver;
import org.fest.swing.driver.JTableDriver;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.table.TableModel;
import javax.swing.text.JTextComponent;
import java.awt.event.KeyEvent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilGone;
import static com.google.common.truth.Truth.assertThat;
import static javax.swing.SwingUtilities.windowForComponent;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.junit.Assert.*;

/**
 * Fixture representing a property in the property sheet
 */
public class PropertyFixture {
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final Robot myRobot;
  @SuppressWarnings({"FieldCanBeLocal", "UnusedDeclaration"})
  private final PropertySheetFixture myPropertySheetFixture;
  private final Property myProperty;
  private final LayoutEditorComponentFixture myComponent;

  public PropertyFixture(@NotNull Robot robot, @NotNull PropertySheetFixture propertySheetFixture,
                         @NotNull LayoutEditorComponentFixture component, @NotNull Property property) {
    myRobot = robot;
    myPropertySheetFixture = propertySheetFixture;
    myComponent = component;
    myProperty = property;
  }

  /**
   * Requires the property display name to be the given name
   */
  public PropertyFixture requireDisplayName(@NotNull String name) throws Exception {
    assertEquals(name, myProperty.getName());
    return this;
  }

  /**
   * Requires the property value to be the given value
   */
  public PropertyFixture requireValue(@NotNull String value) throws Exception {
    assertEquals("Property '" + myProperty.getName() + "'", value, getValue());
    return this;
  }

  /**
   * Reads the current value from the property
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public String getValue() throws Exception {
    /*
    TODO: Read via the UI property widget instead
    This should do the trick, but still needs some timing debugging:

    TableCell cell = findTableCell(true);
    assertNotNull(cell);

    JTableDriver tableDriver = new JTableDriver(myRobot);
    String value = tableDriver.value(getTable(), cell);
    return value == null ? "<null>" : value;
     */
    return ApplicationManager.getApplication().runReadAction(
      (ThrowableComputable<String, Exception>)() -> {
        Object value = myProperty.getValue(myComponent.getComponent());
        return value == null ? "<null>" : value.toString();
      });
  }

  public void requireXmlValue(@Nullable String expectedValue) {
    assertThat(myProperty).isInstanceOf(AttributeProperty.class);
    AttributeProperty attributeProperty = (AttributeProperty)myProperty;
    RadViewComponent component = myComponent.getComponent();
    String namespace = attributeProperty.getNamespace(component, false);
    String value = component.getTag().getAttributeValue(attributeProperty.getName(), namespace);
    assertEquals(expectedValue, value);
  }

  @Nullable
  private TableCell findTableCell(boolean requireExists) {
    RadPropertyTable table = getTable();
    TableModel model = table.getModel();
    int rowCount = model.getRowCount();
    int columnCount = model.getColumnCount();
    for (int row = 0; row < rowCount; row++) {
      for (int column = 0; column < columnCount; column++) {
        Object valueAt = model.getValueAt(row, column);
        if (valueAt == myProperty) {
          return TableCell.row(row).column(1);
        }
      }
    }

    if (requireExists) {
      fail("Could not find property " + myProperty + " in the property table!");
    }

    return null;
  }

  private RadPropertyTable getTable() {
    return myPropertySheetFixture.getPropertyTablePanel().getPropertyTable();
  }

  /** Types in the given value into the property */
  @SuppressWarnings("unchecked")
  public void enterValue(@NotNull String value) {
    enterValue(value, null);
  }

  /** Types in the given value into the property, and checks that the value is rejected */
  @SuppressWarnings("unchecked")
  public void enterInvalidValue(@NotNull String value, @NotNull String expectedError) {
    enterValue(value, expectedError);
  }

  private void enterValue(@NotNull final String value, @Nullable String expectedError) {
    RadPropertyTable table = getTable();
    TableCell cell = findTableCell(true);
    assertNotNull(cell);
    final ComponentDriver componentDriver = new ComponentDriver(myRobot);
    JTableDriver tableDriver = new JTableDriver(myRobot);

    // Can't use startCellEditing; doesn't support the subclasses in the designer property sheet
    //tableDriver.startCellEditing(table, cell);
    tableDriver.click(table, cell, MouseButton.LEFT_BUTTON, 1);

    final JTextComponent field = waitUntilFound(myRobot, table, JTextComponentMatcher.any());
    componentDriver.focusAndWaitForFocusGain(field);
    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        field.selectAll(); // workaround: when mouse clicking the focus listener doesn't kick in on some Linux window managers
      }
    });
    myRobot.waitForIdle();
    myRobot.enterText(value);
    componentDriver.pressAndReleaseKeys(field, KeyEvent.VK_ENTER);

    if (expectedError == null) {
      // Ensure that after entering the text, the property is committed and exists text editing
      waitUntilGone(myRobot, table, JTextComponentMatcher.any());
    } else {
      MessagesFixture messages = MessagesFixture.findByTitle(myRobot, windowForComponent(table), "Invalid Input");
      messages.requireMessageContains(expectedError)
              .clickOk();
      componentDriver.pressAndReleaseKeys(field, KeyEvent.VK_ESCAPE);
    }
  }
}
