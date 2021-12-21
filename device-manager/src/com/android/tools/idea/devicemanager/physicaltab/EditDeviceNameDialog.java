/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.physicaltab;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBTextField;
import java.awt.Component;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import javax.swing.AbstractButton;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.GroupLayout.Group;
import javax.swing.JComponent;
import javax.swing.text.JTextComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class EditDeviceNameDialog extends DialogWrapper {
  private @Nullable JBTextField myTextField;
  private @Nullable AbstractButton myLink;

  EditDeviceNameDialog(@Nullable Project project, @NotNull String nameOverride, @NotNull String name) {
    super(project);

    initTextField(nameOverride, name);
    initLink();

    init();
    setTitle("Edit Device Name");
    setOKButtonText("Save");
  }

  private void initTextField(@NotNull String nameOverride, @NotNull String name) {
    myTextField = new JBTextField(nameOverride, 16);
    myTextField.getEmptyText().setText(name);

    myTextField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(@NotNull FocusEvent event) {
        myTextField.selectAll();
      }
    });
  }

  private void initLink() {
    myLink = new ActionLink("Reset to default", event -> {
      assert myTextField != null;
      myTextField.setText("");
    });
  }

  @NotNull String getNameOverride() {
    assert myTextField != null;
    return myTextField.getText();
  }

  @VisibleForTesting
  @NotNull JTextComponent getTextField() {
    assert myTextField != null;
    return myTextField;
  }

  @VisibleForTesting
  @NotNull AbstractButton getLink() {
    assert myLink != null;
    return myLink;
  }

  @Override
  protected @NotNull JComponent createCenterPanel() {
    Component label = new JBLabel("Name:");
    JComponent panel = new JBPanel<>(null);

    GroupLayout layout = new GroupLayout(panel);
    layout.setAutoCreateGaps(true);

    Group horizontalGroup = layout.createSequentialGroup()
      .addComponent(label)
      .addComponent(myTextField)
      .addComponent(myLink);

    Group verticalGroup = layout.createParallelGroup(Alignment.CENTER)
      .addComponent(label)
      .addComponent(myTextField)
      .addComponent(myLink);

    layout.setHorizontalGroup(horizontalGroup);
    layout.setVerticalGroup(verticalGroup);

    panel.setLayout(layout);
    return panel;
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myTextField;
  }
}
