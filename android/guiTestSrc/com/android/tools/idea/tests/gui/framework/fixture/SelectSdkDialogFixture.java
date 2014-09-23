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

import com.android.tools.idea.sdk.SelectSdkDialog;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.edt.GuiActionRunner;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static org.fest.assertions.Assertions.assertThat;

public class SelectSdkDialogFixture extends IdeaDialogFixture<SelectSdkDialog> {
  @NotNull
  public static SelectSdkDialogFixture find(@NotNull Robot robot) {
    final Ref<SelectSdkDialog> wrapperRef = new Ref<SelectSdkDialog>();
    JDialog dialog = waitUntilFound(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(JDialog dialog) {
        if (dialog.isShowing()) {
          SelectSdkDialog wrapper = getDialogWrapperFrom(dialog, SelectSdkDialog.class);
          if (wrapper != null) {
            wrapperRef.set(wrapper);
            return true;
          }
        }
        return false;
      }
    });
    return new SelectSdkDialogFixture(robot, dialog, wrapperRef.get());
  }

  private SelectSdkDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull SelectSdkDialog dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  @NotNull
  public SelectSdkDialogFixture setJdkPath(@NotNull final File path) {
    final JLabel label = robot.finder().find(target, JLabelMatcher.withText("Select Java JDK:").andShowing());
    GuiActionRunner.execute(new GuiTask() {
      @Override
      protected void executeInEDT() throws Throwable {
        Component textField = label.getLabelFor();
        assertThat(textField).isInstanceOf(JTextField.class);
        ((JTextField)textField).setText(path.getPath());
      }
    });
    return this;
  }

  @NotNull
  public SelectSdkDialogFixture clickOk() {
    findAndClickOkButton(this);
    return this;
  }

}
