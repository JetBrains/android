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
import com.intellij.ide.actions.TemplateKindCombo;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.android.actions.CreateResourceFileDialogBase;
import org.jetbrains.annotations.NotNull;

public class CreateResourceFileDialogFixture extends IdeaDialogFixture<CreateResourceFileDialogBase> {

  private final IdeFrameFixture myIdeFrameFixture;

  @NotNull
  public static CreateResourceFileDialogFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    return new CreateResourceFileDialogFixture(find(ideFrameFixture.robot(), CreateResourceFileDialogBase.class), ideFrameFixture);
  }

  private CreateResourceFileDialogFixture(@NotNull DialogAndWrapper<CreateResourceFileDialogBase> dialogAndWrapper, IdeFrameFixture ideFrameFixture) {
    super(ideFrameFixture.robot(), dialogAndWrapper);
    this.myIdeFrameFixture = ideFrameFixture;
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
  public IdeFrameFixture clickOk() {
    GuiTests.findAndClickOkButton(this);
    return myIdeFrameFixture;
  }

  @NotNull
  public AddProjectDependencyDialogFixture clickOkAndWaitForDependencyDialog() {
    clickOk();
    robot().waitForIdle();
    return AddProjectDependencyDialogFixture.find(myIdeFrameFixture);
  }

  @NotNull
  public CreateResourceFileDialogFixture setType(@NotNull String type) {
    TemplateKindCombo
      resourceType = robot().finder().findByLabel(target(), "Resource type:", TemplateKindCombo.class, true);

    JComboBoxFixture comboBox = new JComboBoxFixture(robot(), robot().finder()
      .findByType(resourceType.getChildComponent(), JComboBox.class));

    comboBox.click();
    comboBox.selectItem(type);
    return this;
  }
}
