/*
 * Copyright (C) 2016 The Android Open Source Project
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
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class InferNullityDialogFixture extends IdeaDialogFixture<DialogWrapper> {
  @NotNull
  public static InferNullityDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new InferNullityDialogFixture(
      ideFrameFixture, find(ideFrameFixture.robot(), DialogWrapper.class, Matchers.byTitle(JDialog.class, "Specify Infer Nullity Scope")));
  }

  private InferNullityDialogFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull DialogAndWrapper<DialogWrapper> dialogAndWrapper) {
    super(ideFrameFixture.robot(), dialogAndWrapper);
  }

  public void clickOk() {
    GuiTests.findAndClickOkButton(this);
  }
}
