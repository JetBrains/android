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
package com.android.tools.adtui.flat;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Borderless button with hover effect used by studio profilers.
 */
public class FlatButton extends JButton {

  public FlatButton() {
    this(null, null);
  }

  public FlatButton(@NotNull String text) {
    this(text, null);
  }

  public FlatButton(@NotNull Icon icon) {
    this(null, icon);
  }

  public FlatButton(@Nullable String text, @Nullable Icon icon) {
    super(text, icon);
  }

  @Override
  public void updateUI() {
    setUI(new FlatButtonUI());
  }

  /**
   * Do not support keyboard accessibility until it is supported product-wide in Studio.
   */
  @Override
  public boolean isFocusable() {
    return false;
  }
}
