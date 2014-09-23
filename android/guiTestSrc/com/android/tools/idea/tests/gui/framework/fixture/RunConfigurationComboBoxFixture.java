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

import com.intellij.execution.actions.RunConfigurationsComboBoxAction;
import com.intellij.openapi.util.Key;
import org.fest.reflect.reference.TypeRef;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.fixture.ComponentFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.reflect.core.Reflection.staticField;

public class RunConfigurationComboBoxFixture extends ComponentFixture<JButton> {
  @NotNull
  static RunConfigurationComboBoxFixture find(@NotNull final IdeFrameFixture parent) {
    final Key<?> key = staticField("BUTTON_KEY").ofType(new TypeRef<Key<?>>() {})
                                                .in(RunConfigurationsComboBoxAction.class)
                                                .get();
    JButton button = GuiActionRunner.execute(new GuiQuery<JButton>() {
      @Override
      protected JButton executeInEDT() throws Throwable {
        Object runConfigurationComboBox = parent.target.getComponent().getRootPane().getClientProperty(key);
        assertThat(runConfigurationComboBox).isInstanceOf(JButton.class);
        return (JButton)runConfigurationComboBox;
      }
    });
    return new RunConfigurationComboBoxFixture(parent.robot, button);
  }

  private RunConfigurationComboBoxFixture(@NotNull Robot robot, @NotNull JButton target) {
    super(robot, target);
  }

  @Nullable
  public String getText() {
    return GuiActionRunner.execute(new GuiQuery<String>() {
      @Override
      protected String executeInEDT() throws Throwable {
        return target.getText();
      }
    });
  }
}
