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
package com.android.tools.idea.gradle.structure.configurables.ui;

import com.android.tools.idea.gradle.structure.model.PsdModel;
import com.intellij.ui.ColoredTableCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class BaseTableCellRenderer<T extends PsdModel> extends ColoredTableCellRenderer {
  @NotNull private final T myModel;

  protected BaseTableCellRenderer(@NotNull T model) {
    myModel = model;
  }

  @Override
  protected void customizeCellRenderer(JTable table, @Nullable Object value, boolean selected, boolean hasFocus, int row, int column) {
    setIcon(myModel.getIcon());
    setIconOpaque(true);
    setFocusBorderAroundIcon(true);
    append(getText());
  }

  @NotNull
  protected abstract String getText();

  @NotNull
  protected T getModel() {
    return myModel;
  }
}
