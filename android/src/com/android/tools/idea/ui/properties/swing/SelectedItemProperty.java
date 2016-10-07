/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.ui.properties.swing;

import com.android.tools.idea.ui.properties.AbstractProperty;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

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
    return Optional.fromNullable((T)myComboBox.getSelectedItem());
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myComboBox.setSelectedItem(value.orNull());
  }
}
