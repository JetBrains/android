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
package com.android.tools.idea.tests.gui.framework.fixture;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.icons.AllIcons;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.android.uipreview.ChooseResourceDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.JLabel;
import javax.swing.text.JTextComponent;


public class ChooseResourceDialogFixture extends IdeaDialogFixture<ChooseResourceDialog> {

  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class));
  }

  private ChooseResourceDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ChooseResourceDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public JTextComponentFixture getNameTextField() {
    return new JTextComponentFixture(robot(), (JTextComponent)robot().finder().findByLabel("Name"));
  }

  @NotNull
  public String getError() {
    final JLabel error =
      GuiTests.waitUntilFound(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JLabel component) {
          return component.isShowing() && !"".equals(component.getText()) && component.getIcon() == AllIcons.Actions.Lightning;
        }
      });
    return error.getText();
  }
}
