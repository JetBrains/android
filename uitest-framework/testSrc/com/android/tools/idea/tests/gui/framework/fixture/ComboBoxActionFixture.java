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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.ui.components.JBList;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import javax.swing.*;
import java.awt.*;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.*;

public class ComboBoxActionFixture {
  @NotNull private Robot myRobot;
  @NotNull private JButton myTarget;
  private static final Class<?> ourComboBoxButtonClass;
  static {
    Class<?> temp = null;
    try {
      temp = ComboBoxActionFixture.class.getClassLoader().loadClass(ComboBoxAction.class.getCanonicalName() + "$ComboBoxButton");
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    ourComboBoxButtonClass = temp;
  }

  public static ComboBoxActionFixture findComboBox(@NotNull Robot robot, @NotNull Container root) {
    JButton comboBoxButton = robot.finder().find(root, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return ourComboBoxButtonClass.isInstance(component);
      }
    });
    return new ComboBoxActionFixture(robot, comboBoxButton);
  }

  public static ComboBoxActionFixture findComboBoxByText(@NotNull Robot robot, @NotNull Container root, @NotNull final String text) {
    JButton comboBoxButton = robot.finder().find(root, Matchers.byText(JButton.class, text));
    return new ComboBoxActionFixture(robot, comboBoxButton);
  }

  public ComboBoxActionFixture(@NotNull Robot robot, @NotNull JButton target) {
    myRobot = robot;
    myTarget = target;
  }

  public void selectItem(@NotNull String itemName) {
    click();
    selectItemByText(itemName);
  }

  public List<String> items() {
    click();
    JBList list = getList();
    final JListFixture listFixture = new JListFixture(myRobot, list);
    final ImmutableList<String> result = ImmutableList.copyOf(listFixture.contents());
    click();
    return result;
  }

  @NotNull
  public String getSelectedItemText() {
    return GuiQuery.getNonNull(myTarget::getText);
  }

  private void click() {
    final JButtonFixture comboBoxButtonFixture = new JButtonFixture(myRobot, myTarget);
    Wait.seconds(1).expecting("comboBoxButton to be enabled")
      .until(() -> GuiQuery.getNonNull(() -> comboBoxButtonFixture.target().isEnabled()));
    comboBoxButtonFixture.click();
  }

  @NotNull
  private JBList getList() {
    return GuiTests.waitUntilShowingAndEnabled(myRobot, null, new GenericTypeMatcher<JBList>(JBList.class) {
      @Override
      protected boolean isMatching(@NotNull JBList list) {
        return list.getClass().getName().equals("com.intellij.ui.popup.list.ListPopupImpl$MyList");
      }
    });
  }

  private void selectItemByText(@NotNull final String text) {
    JList list = getList();
    Wait.seconds(1).expecting("the list to be populated")
      .until(() -> {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          if (text.equals(actionItem.getText())) {
            return true;
          }
        }
        return false;
      });

    int appIndex = GuiQuery.getNonNull(
      () -> {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          if (text.equals(actionItem.getText())) {
            return i;
          }
        }
        return -1;
      });
    assertThat(appIndex).isAtLeast(0);

    GuiTask.execute(() -> list.setSelectedIndex(appIndex));
    assertEquals(text, ((PopupFactoryImpl.ActionItem)list.getSelectedValue()).getText());
  }
}
