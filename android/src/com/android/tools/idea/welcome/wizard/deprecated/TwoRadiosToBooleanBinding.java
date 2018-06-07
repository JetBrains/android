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
package com.android.tools.idea.welcome.wizard.deprecated;

import com.android.tools.idea.wizard.dynamic.ScopedDataBinder;
import com.google.common.base.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * Two-way binding for using dual radiobuttons to edit a boolean value.
 */
public class TwoRadiosToBooleanBinding extends ScopedDataBinder.ComponentBinding<Boolean, JComponent> {
  @NotNull private final JRadioButton myButtonTrue;
  @NotNull private final JRadioButton myButtonFalse;

  public TwoRadiosToBooleanBinding(@NotNull JRadioButton buttonTrue, @NotNull JRadioButton buttonFalse) {
    myButtonTrue = buttonTrue;
    myButtonFalse = buttonFalse;
  }

  @Override
  public void setValue(@Nullable Boolean newValue, @NotNull JComponent component) {
    boolean customInstall = Objects.equal(Boolean.TRUE, newValue);
    myButtonTrue.setSelected(customInstall);
    myButtonFalse.setSelected(!customInstall);
  }

  @Nullable
  @Override
  public Boolean getValue(@NotNull JComponent component) {
    return myButtonTrue.isSelected();
  }

  @Override
  public void addActionListener(@NotNull final ActionListener listener, @NotNull final JComponent component) {
    ItemListener itemListener = (ItemEvent e) -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        String actionCommand = ((JRadioButton)e.getItem()).getActionCommand();
        ActionEvent event = new ActionEvent(component, e.getID(), actionCommand);
        listener.actionPerformed(event);
      }
    };
    myButtonTrue.addItemListener(itemListener);
    myButtonFalse.addItemListener(itemListener);
  }
}
