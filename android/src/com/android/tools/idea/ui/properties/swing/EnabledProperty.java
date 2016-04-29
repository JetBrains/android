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
import com.android.tools.idea.ui.properties.core.BoolProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * {@link ObservableProperty} that wraps a Swing component and exposes its enabled state.
 */
public final class EnabledProperty extends BoolProperty implements PropertyChangeListener {
  @NotNull private final JComponent myComponent;

  public EnabledProperty(@NotNull JComponent component) {
    myComponent = component;
    myComponent.addPropertyChangeListener("enabled", this);
  }

  @Override
  public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Boolean get() {
    return myComponent.isEnabled();
  }

  @Override
  protected void setDirectly(@NotNull Boolean value) {
    myComponent.setEnabled(value);
  }
}
