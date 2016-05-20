/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property.editors;

import com.android.tools.idea.uibuilder.property.NlProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Common interface for various inspector editors.
 */
public interface NlComponentEditor {

  @NotNull
  JComponent getComponent();

  @Nullable
  Object getValue();

  void activate();

  void setEnabled(boolean enabled);

  void setVisible(boolean visible);

  @Nullable
  NlProperty getProperty();

  void setProperty(@NotNull NlProperty property);

  void refresh();

  void requestFocus();

  @Nullable
  JLabel getLabel();

  void setLabel(@NotNull JLabel label);
}
