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
import com.android.tools.idea.ui.properties.core.IntProperty;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;


/**
 * {@link AbstractProperty} that wraps a slider and exposes its value.
 */
public final class SliderValueProperty extends IntProperty implements ChangeListener {
  @NotNull private final JSlider mySlider;

  public SliderValueProperty(@NotNull JSlider slider) {
    mySlider = slider;
    mySlider.addChangeListener(this);
  }

  @Override
  protected void setDirectly(@NotNull Integer value) {
    mySlider.setValue(value);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    notifyInvalidated();
  }

  @NotNull
  @Override
  public Integer get() {
    return mySlider.getValue();
  }
}
