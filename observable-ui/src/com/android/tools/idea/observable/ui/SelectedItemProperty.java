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
package com.android.tools.idea.observable.ui;

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.OptionalProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Optional;

/**
 * {@link AbstractProperty} that wraps a Swing combobox and exposes its selected item.
 */
@SuppressWarnings("unchecked")
public final class SelectedItemProperty<T> extends OptionalProperty<T> implements ActionListener {

  @NotNull private JComboBox myComboBox;

  public SelectedItemProperty(@NotNull JComboBox comboBox) {
    myComboBox = comboBox;
    myComboBox.addActionListener(this);
  }

  @Override
  public void actionPerformed(ActionEvent actionEvent) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return Optional.ofNullable((T)myComboBox.getSelectedItem());
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myComboBox.setSelectedItem(value.orElse(null));
  }
}
