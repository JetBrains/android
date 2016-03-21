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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewXmlValueWizardFixture extends NewAndroidComponentWizardFixture {
  @NotNull
  public static NewXmlValueWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = robot.finder().find(new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "New Android Component".equals(dialog.getTitle()) && dialog.isShowing();
      }
    });
    return new NewXmlValueWizardFixture(robot, dialog);
  }

  private NewXmlValueWizardFixture(@NotNull Robot robot,
                                   @NotNull JDialog target) {
    super(robot, target);
  }

  public JTextComponentFixture getFileNameField() {
    return findTextField("Values File Name:");
  }
}
