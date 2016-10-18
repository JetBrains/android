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
package com.android.tools.adtui.workbench;

import com.android.annotations.Nullable;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * Specifies the content of {@link ToolWindowDefinition}.
 *
 * @param <T> Specifies the type of data controlled by a {@link WorkBench}.
 */
public interface ToolContent<T> extends Disposable {
  /**
   * Set the context of a newly created {@link ToolContent}.
   * This value may be <code>null</code> for a floating tool window if there is no suitable content to show.
   */
  void setToolContext(@Nullable T toolContext);

  /**
   * @return the visual component for this tool window.
   */
  @NotNull
  JComponent getComponent();

  /**
   * Request the component that should receive focus initially.
   */
  @NotNull
  JComponent getFocusedComponent();

  /**
   * @return the actions to be added to the top of the gear dropdown.
   */
  @NotNull
  List<AnAction> getGearActions();

  /**
   * @return the actions to be added to the left of the gear dropdown.
   */
  @NotNull
  List<AnAction> getAdditionalActions();

  /**
   * The tool window system will register a callback for closing a tool window in auto hide mode.
   */
  void registerCloseAutoHideWindow(@NotNull Runnable runnable);
}
