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

import com.android.tools.idea.configurations.ThemeSelectionDialog;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.intellij.ui.components.JBList;
import com.intellij.ui.treeStructure.Tree;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTreeFixture;
import org.jetbrains.annotations.NotNull;

public class ThemeSelectionDialogFixture extends IdeaDialogFixture<ThemeSelectionDialog> {
  @NotNull
  public static ThemeSelectionDialogFixture find(@NotNull Robot robot) {
    return new ThemeSelectionDialogFixture(robot, find(robot, ThemeSelectionDialog.class));
  }

  private ThemeSelectionDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<ThemeSelectionDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  public JTreeFixture getCategoriesTree() {
    return new JTreeFixture(robot(), robot().finder().findByType(this.target(), Tree.class));
  }

  public JListFixture getThemeList() {
    return new JListFixture(robot(), robot().finder().findByType(this.target(), JBList.class));
  }

  @NotNull
  public ThemeSelectionDialogFixture selectsTheme(@NotNull String category, @NotNull String theme) {
    getCategoriesTree().clickPath(category);
    robot().waitForIdle();
    getThemeList().clickItem(theme);
    robot().waitForIdle();
    return this;
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
  }
}
