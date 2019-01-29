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

import com.intellij.openapi.ui.ComboBox;

/**
 * TODO deprecate in preference of {@link com.android.tools.adtui.stdui.CommonComboBox}
 */
public class FlatComboBox<E> extends ComboBox<E> {

  public FlatComboBox() {
    setOpaque(false);
  }

  @Override
  public void updateUI() {
    setUI(new FlatComboBoxUI());
  }

  /**
   * Do not support keyboard accessibility until it is supported product-wide in Studio.
   */
  @Override
  public boolean isFocusable() {
    return false;
  }
}
