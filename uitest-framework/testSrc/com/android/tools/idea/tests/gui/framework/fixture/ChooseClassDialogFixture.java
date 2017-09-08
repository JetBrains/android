/*
 * Copyright (C) 2017 The Android Open Source Project
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
import org.fest.swing.fixture.JListFixture;
import org.jetbrains.android.uipreview.ChooseClassDialog;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ChooseClassDialogFixture extends IdeaDialogFixture<ChooseClassDialog> {
  @NotNull
  public static ChooseClassDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new ChooseClassDialogFixture(
      ideFrameFixture,
      find(ideFrameFixture.robot(), ChooseClassDialog.class));
  }

  private ChooseClassDialogFixture(@NotNull IdeFrameFixture ideFrameFixture,
                                   @NotNull DialogAndWrapper<ChooseClassDialog> dialogAndWrapper) {
    super(ideFrameFixture.robot(), dialogAndWrapper);
  }

  @NotNull
  public ChooseClassDialogFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return this;
  }

  @NotNull
  public JListFixture getList() {
    return new JListFixture(robot(), robot().finder().findByType(target(), JList.class));
  }
}
