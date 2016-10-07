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

import com.android.tools.idea.gradle.project.ProxySettingsDialog;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Ref;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;
import static org.junit.Assert.assertNotNull;

public class ProxySettingsDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static ProxySettingsDialogFixture find(@NotNull Robot robot) {
    final Ref<DialogWrapper> wrapperRef = new Ref<>();
    JDialog dialog = GuiTests.waitUntilShowing(robot, new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        DialogWrapper wrapper = getDialogWrapperFrom(dialog, DialogWrapper.class);
        if (wrapper != null && wrapper.getClass() == ProxySettingsDialog.class) {
          wrapperRef.set(wrapper);
          return true;
        }
        return false;
      }
    });

    return new ProxySettingsDialogFixture(robot, dialog, wrapperRef.get());
  }

  private ProxySettingsDialogFixture(@NotNull Robot robot, @NotNull JDialog target, @NotNull DialogWrapper dialogWrapper) {
    super(robot, target, dialogWrapper);
  }

  public void clickOk() {
    findAndClickOkButton(this);
  }

  public void enableHttpsProxy() {
    JCheckBox checkBox = robot().finder().find(this.target(), new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
      @Override
      protected boolean isMatching(@NotNull JCheckBox checkBox) {
        return "Enable HTTPS Proxy".equals(checkBox.getText());
      }
    });

    JCheckBoxFixture checkBoxFixture = new JCheckBoxFixture(robot(), checkBox);
    checkBoxFixture.select();
  }

  public void setDoNotShowThisDialog(boolean selected) {
    JCheckBox checkBox = robot().finder().find(new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
      @Override
      protected boolean isMatching(@NotNull JCheckBox c) {
        return c.isVisible() && c.isShowing() && "Do not show this dialog in the future".equals(c.getText());
      }
    });
    assertNotNull(checkBox);
    JCheckBoxFixture checkBoxFixture = new JCheckBoxFixture(robot(), checkBox);
    checkBoxFixture.setSelected(selected);
  }
}
