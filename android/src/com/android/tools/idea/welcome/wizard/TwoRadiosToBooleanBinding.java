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
package com.android.tools.idea.welcome.wizard;

import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Two-way binding for using dual radiobuttons to edit a boolean value.
 */
public class TwoRadiosToBooleanBinding extends ScopedDataBinder.ComponentBinding<Boolean, JPanel> {
  @NotNull private final JRadioButton myButtonTrue;
  @NotNull private final JRadioButton myButtonFalse;

  public TwoRadiosToBooleanBinding(@NotNull JRadioButton buttonTrue, @NotNull JRadioButton buttonFalse) {
    myButtonTrue = buttonTrue;
    myButtonFalse = buttonFalse;
  }

  @Override
  public void setValue(@Nullable Boolean newValue, @NotNull JPanel component) {
    boolean customInstall = Objects.equal(Boolean.TRUE, newValue);
    myButtonTrue.setSelected(customInstall);
    myButtonFalse.setSelected(!customInstall);
  }

  @Nullable
  @Override
  public Boolean getValue(@NotNull JPanel component) {
    return myButtonTrue.isSelected();
  }

  @Override
  public void addActionListener(@NotNull final ActionListener listener, @NotNull final JPanel component) {
    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        ActionEvent event = new ActionEvent(component, e.getID(), e.getActionCommand(), e.getWhen(), e.getModifiers());
        listener.actionPerformed(event);
      }
    };
    myButtonTrue.addActionListener(actionListener);
    myButtonFalse.addActionListener(actionListener);
  }
}
