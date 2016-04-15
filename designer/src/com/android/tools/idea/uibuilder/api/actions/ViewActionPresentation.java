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
package com.android.tools.idea.uibuilder.api.actions;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The actual presentation of an action in the designer.
 * This interface is implemented by the IDE and provided to {@link ViewAction#updatePresentation} such
 * that actions can update the icon, label, enabled-state and visibility of an action.
 */
public interface ViewActionPresentation {
  /**
   * Sets the label of the action
   *
   * @param label the label
   */
  void setLabel(@NotNull String label);

  /**
   * Sets whether the action is enabled (default is true)
   *
   * @param enabled whether the action should be enabled
   */
  void setEnabled(boolean enabled);

  /**
   * Sets whether the action is visible (default is true)
   *
   * @param visible whether the action should be visible
   */
  void setVisible(boolean visible);

  /**
   * Sets the icon to use for this action, if any
   *
   * @param icon the icon or null
   */
  void setIcon(@Nullable Icon icon);
}
