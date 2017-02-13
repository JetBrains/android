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

import com.android.tools.idea.editors.strings.MultilineStringEditorDialog;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.intellij.ui.EditorTextField;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

public class MultilineStringEditorDialogFixture extends IdeaDialogFixture<MultilineStringEditorDialog> {

  private MultilineStringEditorDialogFixture(@NotNull Robot robot,
                                               @NotNull DialogAndWrapper<MultilineStringEditorDialog> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public static MultilineStringEditorDialogFixture find(@NotNull Robot robot) {
    return new MultilineStringEditorDialogFixture(robot, find(robot, MultilineStringEditorDialog.class));
  }

  @NotNull
  public EditorTextFieldFixture getTranslationEditorTextField() {
    return EditorTextFieldFixture.findByName(robot(), target(), "translationEditorTextField");
  }

  public void clickOk() {
    findAndClickOkButton(this);
    waitUntilNotShowing();
  }
}
