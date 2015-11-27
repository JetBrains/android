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

import com.android.tools.idea.ui.properties.ObservableProperty;
import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.google.common.base.Optional;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * {@link ObservableProperty} that wraps a label and exposes its icon value.
 */
public final class IconProperty extends OptionalProperty<Icon> implements PropertyChangeListener {
  @NotNull private final JComponent myComponent;

  public IconProperty(@NotNull JLabel label) {
    myComponent = label;
    label.addPropertyChangeListener("icon", this);
  }

  public IconProperty(@NotNull AbstractButton button) {
    myComponent = button;
    button.addPropertyChangeListener("icon", this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
    notifyInvalidated();
  }

  @Override
  protected void setDirectly(@NotNull Optional<Icon> value) {
    if (myComponent instanceof JLabel) {
      ((JLabel)myComponent).setIcon(value.orNull());
    }
    else if (myComponent instanceof AbstractButton) {
      ((AbstractButton)myComponent).setIcon(value.orNull());
    }
    else {
      throw new IllegalStateException("Unexpected icon component type: " + myComponent.getClass().getSimpleName());
    }
  }

  @NotNull
  @Override
  public Optional<Icon> get() {
    if (myComponent instanceof JLabel) {
      return Optional.fromNullable(((JLabel)myComponent).getIcon());
    }
    else if (myComponent instanceof AbstractButton) {
      return Optional.fromNullable(((AbstractButton)myComponent).getIcon());
    }
    else {
      throw new IllegalStateException("Unexpected icon component type: " + myComponent.getClass().getSimpleName());
    }
  }
}
