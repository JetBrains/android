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
package com.android.tools.profilers.stacktrace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * An abstraction of a Tab UI's functionality.
 */
public interface TabsPanel {
  /**
   * @return the Tab UI component.
   */
  @NotNull
  JComponent getComponent();

  void addTab(@NotNull String label, @NotNull JComponent content);

  void removeTab(@NotNull JComponent content);

  void removeAll();

  /**
   * Return the selected tab component, which can be {@code null} before any tab has been selected.
   */
  @Nullable
  JComponent getSelectedTabComponent();

  List<Component> getTabsComponents();

  void selectTab(@NotNull String label);

  /**
   * Set a callback to be called when tab selection changes.
   */
  void setOnSelectionChange(@Nullable Runnable callback);

  /**
   * Return the selected tab title, which can be {@code null} before any tab has been selected.
   */
  @Nullable
  String getSelectedTab();
}
