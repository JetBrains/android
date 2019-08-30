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

import com.android.tools.idea.observable.AbstractProperty;
import com.android.tools.idea.observable.core.IntProperty;
import com.intellij.ui.JBIntSpinner;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.jetbrains.annotations.NotNull;


/**
 * {@link AbstractProperty} that wraps a {@link JBIntSpinner} and exposes its value.
 */
public final class SpinnerValueProperty extends IntProperty implements ChangeListener {
  @NotNull private final JBIntSpinner mySpinner;

  public SpinnerValueProperty(@NotNull JBIntSpinner spinner) {
    mySpinner = spinner;
    mySpinner.addChangeListener(this);
  }

  @Override
  protected void setDirectly(@NotNull Integer value) {
    mySpinner.setNumber(value);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Integer get() {
    return mySpinner.getNumber();
  }
}
