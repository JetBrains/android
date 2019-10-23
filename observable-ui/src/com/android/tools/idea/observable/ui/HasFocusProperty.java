/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.observable.AbstractObservableValue;
import com.android.tools.idea.observable.core.ObservableBool;
import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import org.jetbrains.annotations.NotNull;

/**
 * A {@link ObservableBool} that reflects whether an AWT component has focus or not.
 */
public final class HasFocusProperty extends AbstractObservableValue<Boolean> implements ObservableBool, FocusListener {
  @NotNull private final Component myComponent;

  public HasFocusProperty(@NotNull Component component) {
    myComponent = component;
    myComponent.addFocusListener(this);
  }

  @Override
  @NotNull
  public Boolean get() {
    return myComponent.hasFocus();
  }

  @Override
  public void focusGained(@NotNull FocusEvent e) {
    notifyInvalidated();
  }

  @Override
  public void focusLost(@NotNull FocusEvent e) {
    notifyInvalidated();
  }
}
