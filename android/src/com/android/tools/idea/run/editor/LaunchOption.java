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
package com.android.tools.idea.run.editor;

import com.intellij.openapi.project.Project;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public abstract class LaunchOption<S extends LaunchOptionState> {
  @NotNull
  public abstract String getId();

  @NotNull
  public abstract String getDisplayName();

  @NotNull
  public abstract S createState();

  @NotNull
  public abstract LaunchOptionConfigurable<S> createConfigurable(@NotNull Project project,
                                                                 @NotNull LaunchOptionConfigurableContext context);

  public static class Renderer extends ColoredListCellRenderer<LaunchOption> {
    @Override
    protected void customizeCellRenderer(@NotNull JList list, LaunchOption value, int index, boolean selected, boolean hasFocus) {
      append(value.getDisplayName());
    }
  }
}
