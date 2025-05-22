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
import java.util.Optional;
import javax.swing.JList;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import org.jetbrains.annotations.NotNull;

/**
 * {@link AbstractProperty} that wraps a Swing list and exposes its selected value.
 */
public class SelectedListValueProperty<T> extends OptionalProperty<T> implements ListSelectionListener {
  private final JList<T> myList;

  public SelectedListValueProperty(@NotNull JList<T> list) {
    myList = list;
    myList.addListSelectionListener(this);
  }

  @Override
  public void valueChanged(@NotNull ListSelectionEvent event) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Optional<T> get() {
    return Optional.ofNullable(myList.getSelectedValue());
  }

  @Override
  protected void setDirectly(@NotNull Optional<T> value) {
    myList.setSelectedValue(value.orElse(null), true);
  }
}
