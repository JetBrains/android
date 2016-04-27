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
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditReferenceFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.StateListPickerFixture;
import com.android.tools.idea.ui.resourcechooser.ChooseResourceDialog;
import com.android.tools.idea.ui.resourcechooser.ColorPicker;
import com.android.tools.idea.ui.resourcechooser.StateListPicker;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JButtonMatcher;
import org.fest.swing.fixture.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class ChooseResourceDialogFixture extends IdeaDialogFixture<ChooseResourceDialog> {
  JTabbedPaneFixture myTabbedPane;

  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class));
  }

  @NotNull
  public static ChooseResourceDialogFixture find(@NotNull Robot robot, @NotNull final GenericTypeMatcher<JDialog> matcher) {
    return new ChooseResourceDialogFixture(robot, find(robot, ChooseResourceDialog.class, matcher));
  }

  private ChooseResourceDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ChooseResourceDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
    myTabbedPane = new JTabbedPaneFixture(robot, (JTabbedPane)robot.finder().findByName(this.target(), "ResourceTypeTabs"));
  }

  @NotNull
  public JTextComponentFixture getNameTextField() {
    return new JTextComponentFixture(robot(), (JTextComponent)robot().finder().findByLabel("Name"));
  }

  public ChooseResourceDialogFixture clickOnTab(@NotNull String name) {
    myTabbedPane.selectTab(name);
    return this;
  }

  @NotNull
  public String getError() {
    return waitForErrorLabel().getText();
  }

  @NotNull
  public JLabel waitForErrorLabel() {
    return GuiTests.waitUntilShowing(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel component) {
        return !StringUtil.isEmpty(component.getText()) && component.getIcon() == AllIcons.General.Error;
      }
    });
  }

  public void requireNoError() {
    GuiTests.waitUntilGone(robot(), target(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel component) {
        return component.isShowing() && component.getIcon() == AllIcons.General.Error;
      }
    });
  }

  @NotNull
  public ColorPickerFixture getColorPicker() {
    return new ColorPickerFixture(robot(), robot().finder().findByType(this.target(), ColorPicker.class));
  }

  @NotNull
  public StateListPickerFixture getStateListPicker() {
    return new StateListPickerFixture(robot(), robot().finder().findByType(this.target(), StateListPicker.class));
  }

  @NotNull
  public EditReferenceFixture getEditReferencePanel() {
    return new EditReferenceFixture(robot(), (Box)robot().finder().findByName(this.target(), "ReferenceEditor"));
  }

  @NotNull
  public JListFixture getList(@NotNull String appNamespaceLabel) {
    return new JListFixture(robot(), (JList)robot().finder().findByName(target(), appNamespaceLabel));
  }

  public void clickOK() {
    findAndClickOkButton(this);
  }

  @NotNull
  public JPopupMenuFixture clickNewResource() {
    JButtonFixture button = new JButtonFixture(robot(), robot().finder().find(target(), JButtonMatcher.withText("New Resource").andShowing()));
    button.click();
    JPopupMenu contextMenu = robot().findActivePopupMenu();
    assert contextMenu != null;
    return new JPopupMenuFixture(robot(), contextMenu);
  }
}
