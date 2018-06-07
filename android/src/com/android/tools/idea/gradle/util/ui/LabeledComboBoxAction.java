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
package com.android.tools.idea.gradle.util.ui;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public abstract class LabeledComboBoxAction extends ComboBoxAction {
  @NotNull private final String myLabel;

  protected LabeledComboBoxAction(@NotNull String label) {
    myLabel = label;
  }

  @Override
  public JComponent createCustomComponent(Presentation presentation) {
    JPanel panel = new JPanel(new BorderLayout());
    JLabel label = new JLabel(myLabel);
    panel.add(label, BorderLayout.WEST);
    ComboBoxButton comboBoxButton = createComboBoxButton(presentation);
    label.setLabelFor(comboBoxButton);
    panel.add(comboBoxButton, BorderLayout.CENTER);
    panel.setBorder(JBUI.Borders.empty(2, 6, 2, 0));
    return panel;
  }
}
