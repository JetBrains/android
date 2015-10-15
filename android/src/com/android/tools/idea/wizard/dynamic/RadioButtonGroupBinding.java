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
package com.android.tools.idea.wizard.dynamic;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * Class for binding radio buttons to {@link ScopedStateStore}
 */
public class RadioButtonGroupBinding<E> extends ScopedDataBinder.ComponentBinding<E, JRadioButton> {
  private final Map<JRadioButton, E> myValueToButtonBinding;

  public RadioButtonGroupBinding(Map<JRadioButton, E> buttonToValue) {
    myValueToButtonBinding = ImmutableMap.copyOf(buttonToValue);
    ButtonGroup buttonGroup = new ButtonGroup();
    for (JRadioButton button : buttonToValue.keySet()) {
      buttonGroup.add(button);
    }
  }

  @Override
  public void setValue(@Nullable E newValue, @NotNull JRadioButton component) {
    component.setSelected(Objects.equal(newValue, myValueToButtonBinding.get(component)));
  }

  @Nullable
  @Override
  public E getValue(@NotNull JRadioButton component) {
    for (Map.Entry<JRadioButton, E> buttonToValue : myValueToButtonBinding.entrySet()) {
      if (buttonToValue.getKey().isSelected()) {
        return buttonToValue.getValue();
      }
    }
    return null;
  }

  @Override
  public void addActionListener(@NotNull ActionListener listener, @NotNull JRadioButton component) {
    component.addActionListener(listener);
  }
}
