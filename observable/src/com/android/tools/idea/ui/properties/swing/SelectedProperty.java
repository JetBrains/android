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
import com.android.tools.idea.ui.properties.core.BoolProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * {@link AbstractProperty} that wraps a Swing button and exposes its selected state (useful for
 * checkboxes).
 */
public final class SelectedProperty extends BoolProperty implements ItemListener {
  @NotNull private final AbstractButton myButton;

  public SelectedProperty(@NotNull AbstractButton button) {
    myButton = button;
    myButton.addItemListener(this);
  }

  @Override
  public void itemStateChanged(ItemEvent itemEvent) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Boolean get() {
    return myButton.isSelected();
  }

  @Override
  protected void setDirectly(@NotNull Boolean value) {
    myButton.setSelected(value);
  }
}
