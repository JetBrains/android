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

/**
 * A wrapper for creating context menu items for a UI component.
 */
public interface ContextMenuItem extends Runnable {
  ContextMenuItem SEPARATOR = new ContextMenuItem() {
    @NotNull
    @Override
    public String getText() {
      return "SEPARATOR";
    }

    @Nullable
    @Override
    public Icon getIcon() {
      return null;
    }

    @Override
    public boolean isEnabled() {
      return false;
    }

    @Override
    public void run() {

    }
  };

  @NotNull
  String getText();

  @Nullable
  Icon getIcon();

  boolean isEnabled();

  @NotNull
  default KeyStroke[] getKeyStrokes() {
    return new KeyStroke[0];
  }
}
