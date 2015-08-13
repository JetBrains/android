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

import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.ui.JBListWithHintProvider;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.timing.Condition;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.SHORT_TIMEOUT;
import static org.fest.assertions.Assertions.assertThat;
import static org.fest.swing.edt.GuiActionRunner.execute;
import static org.fest.swing.timing.Pause.pause;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ComboBoxActionFixture {
  @NotNull private Robot robot;
  @NotNull private IdeFrameFixture projectFrame;

  public ComboBoxActionFixture(@NotNull Robot robot, @NotNull IdeFrameFixture projectFrame) {
    this.robot = robot;
    this.projectFrame = projectFrame;
  }

  public void selectApp(@NotNull String appName) throws ClassNotFoundException {
    click();
    selectItemByText(getRunConfigList(), appName);
  }

  private void click() throws ClassNotFoundException {
    final Class<?> comboBoxButtonClass = getClass().getClassLoader().loadClass(ComboBoxAction.class.getCanonicalName() + "$ComboBoxButton");
    final ActionButtonFixture runButton = projectFrame.findRunApplicationButton();

    Container actionToolbarContainer = execute(new GuiQuery<Container>() {
      @Override
      protected Container executeInEDT() throws Throwable {
        return runButton.target().getParent();
      }
    });
    assertNotNull(actionToolbarContainer);

    JButton comboBoxButton = robot.finder().find(actionToolbarContainer, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton component) {
        return comboBoxButtonClass.isInstance(component);
      }
    });

    final JButtonFixture comboBoxButtonFixture = new JButtonFixture(robot, comboBoxButton);
    pause(new Condition("Wait until comboBoxButton is enabled") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return comboBoxButtonFixture.target().isEnabled();
          }
        });
      }
    }, SHORT_TIMEOUT);
    comboBoxButtonFixture.click();
  }

  @NotNull
  private JList getRunConfigList() {
    final JList runConfigList = robot.finder().findByType(JBListWithHintProvider.class);

    pause(new Condition("Wait until the list is populated.") {
      @Override
      public boolean test() {
        //noinspection ConstantConditions
        return execute(new GuiQuery<Boolean>() {
          @Override
          protected Boolean executeInEDT() throws Throwable {
            return runConfigList.getComponentCount() >= 2; // At least 2, since there is always one option present (the option to edit).
          }
        });
      }
    }, SHORT_TIMEOUT);

    Object selectedValue = execute(new GuiQuery<Object>() {
      @Override
      protected Object executeInEDT() throws Throwable {
        return runConfigList.getSelectedValue();
      }
    });
    assertThat(selectedValue).isInstanceOf(PopupFactoryImpl.ActionItem.class);

    return runConfigList;
  }

  private static void selectItemByText(@NotNull final JList list, @NotNull final String text) {
    final Integer appIndex = execute(new GuiQuery<Integer>() {
      @Override
      protected Integer executeInEDT() throws Throwable {
        ListPopupModel popupModel = (ListPopupModel)list.getModel();
        for (int i = 0; i < popupModel.getSize(); ++i) {
          PopupFactoryImpl.ActionItem actionItem = (PopupFactoryImpl.ActionItem)popupModel.get(i);
          assertNotNull(actionItem);
          if (text.equals(actionItem.getText())) {
            return i;
          }
        }
        return -1;
      }
    });
    //noinspection ConstantConditions
    assertTrue(appIndex >= 0);

    execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        list.setSelectedIndex(appIndex);
      }
    });
    assertEquals(text, ((PopupFactoryImpl.ActionItem)list.getSelectedValue()).getText());
  }
}
