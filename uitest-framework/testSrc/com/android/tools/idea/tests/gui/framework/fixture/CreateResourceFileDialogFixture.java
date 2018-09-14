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

import static org.junit.Assert.assertEquals;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.theme.EditorTextFieldFixture;
import com.intellij.ide.actions.TemplateKindCombo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import javax.swing.JTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.android.actions.CreateResourceFileDialogBase;
import org.jetbrains.annotations.NotNull;

public class CreateResourceFileDialogFixture extends IdeaDialogFixture<CreateResourceFileDialogBase> {
  @NotNull
  public static CreateResourceFileDialogFixture find(@NotNull Robot robot) {
    return new CreateResourceFileDialogFixture(robot, find(robot, CreateResourceFileDialogBase.class));
  }

  @NotNull
  public static CreateResourceFileDialogFixture find(@NotNull IdeFrameFixture frame) {
    return find(frame.robot());
  }

  private CreateResourceFileDialogFixture(@NotNull Robot robot, @NotNull DialogAndWrapper<CreateResourceFileDialogBase> dialogAndWrapper) {
    super(robot, dialogAndWrapper);
  }

  @NotNull
  public CreateResourceFileDialogFixture requireName(@NotNull String name) {
    assertEquals(name, getDialogWrapper().getFileName());
    return this;
  }

  @NotNull
  public CreateResourceFileDialogFixture setFilename(@NotNull String name) {
    JTextField textField = robot().finder().findByLabel(target(), "File name:", JTextField.class, true);
    new JTextComponentFixture(robot(), textField).deleteText().enterText(name);
    return this;
  }

  @NotNull
  public CreateResourceFileDialogFixture setRootElement(@NotNull String viewGroup) {
    EditorTextFieldFixture fixture = EditorTextFieldFixture.findByLabel(robot(), target(), "Root element:");
    fixture.replaceText(viewGroup);
    return this;
  }

  @NotNull
  public CreateResourceFileDialogFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return this;
  }

  @NotNull
  public CreateResourceFileDialogFixture setType(@NotNull String type) {
    ApplicationManager.getApplication().invokeAndWait(
      () -> robot().finder()
        .findByLabel(target(), "Resource type:", TemplateKindCombo.class, true)
        .setSelectedName(type),
      ModalityState.any());
    return this;
  }
}
