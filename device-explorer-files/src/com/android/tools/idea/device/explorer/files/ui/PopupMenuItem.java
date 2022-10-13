/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui;

import com.intellij.openapi.actionSystem.Shortcut;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A wrapper for creating popup menu items for a UI component.
 */
public interface PopupMenuItem extends Runnable {
  @NotNull
  String getText();

  @Nullable
  Icon getIcon();

  boolean isEnabled();

  default boolean isVisible() {
    return true;
  }

  @Nullable
  default String getShortcutId() {
    return null;
  }

  @Nullable
  default Shortcut[] getShortcuts() {
    return null;
  }
}
