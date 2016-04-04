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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.fixture.MessagesFixture;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import com.google.common.collect.ImmutableList;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.table.TableView;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JPopupMenuFixture;
import org.fest.swing.fixture.JTableCellFixture;
import org.fest.swing.fixture.JTableFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static org.fest.swing.core.MouseButton.RIGHT_BUTTON;
import static org.fest.swing.core.matcher.JButtonMatcher.withText;
import static org.junit.Assert.assertNotNull;

public class ChooseDeviceDefinitionStepFixture extends AbstractWizardStepFixture<ChooseDeviceDefinitionStepFixture> {

  /** The index of the Name column in the device-definition table. */
  private static final int NAME_COLUMN = 0;

  public ChooseDeviceDefinitionStepFixture(@NotNull Robot robot, @NotNull JRootPane rootPane) {
    super(ChooseDeviceDefinitionStepFixture.class, robot, rootPane);
  }

  @NotNull
  public ChooseDeviceDefinitionStepFixture enterSearchTerm(@NotNull String searchTerm) {
    SearchTextField searchField = robot().finder().findByType(target(), SearchTextField.class);
    replaceText(searchField.getTextEditor(), searchTerm);
    return this;
  }

  @NotNull
  public ChooseDeviceDefinitionStepFixture selectHardwareProfile(@NotNull final String deviceName) {
    JTableFixture deviceListFixture = getTableFixture();
    JTableCellFixture cell = deviceListFixture.cell(deviceName);
    cell.select();
    return this;
  }

  @NotNull
  public ChooseDeviceDefinitionStepFixture deleteHardwareProfile(@NotNull final String deviceName) {
    JTableFixture deviceListFixture = getTableFixture();

    deviceListFixture.cell(deviceName).click(RIGHT_BUTTON);

    JPopupMenu popupMenu = robot().findActivePopupMenu();
    assertNotNull(popupMenu);
    JPopupMenuFixture contextMenuFixture = new JPopupMenuFixture(robot(), popupMenu);
    contextMenuFixture.menuItem(new GenericTypeMatcher<JMenuItem>(JMenuItem.class) {
      @Override
      protected boolean isMatching(@NotNull JMenuItem component) {
        return "Delete".equals(component.getText());
      }
    }).click();

    MessagesFixture.findByTitle(robot(), target(), "Confirm Deletion").clickYes();
    return this;
  }

  @NotNull
  public HardwareProfileWizardFixture newHardwareProfile() {
    JButton newDeviceButton = robot().finder().find(target(), withText("New Hardware Profile").andShowing());
    robot().click(newDeviceButton);
    return HardwareProfileWizardFixture.find(robot());
  }

  @NotNull
  private JTableFixture getTableFixture() {
    final TableView deviceList = robot().finder().find(target(), new GenericTypeMatcher<TableView>(TableView.class) {
      @Override
      protected boolean isMatching(@NotNull TableView component) {
        return component.getRowCount() > 0 && component.getColumnCount() > 1; // There are two tables on this step, but the category table only has 1 column
      }
    });
    return new JTableFixture(robot(), deviceList);
  }

  public ImmutableList<String> deviceNames() {
    ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
    for (String[] row : getTableFixture().contents()) {
      listBuilder.add(row[NAME_COLUMN]);
    }
    return listBuilder.build();
  }
}
