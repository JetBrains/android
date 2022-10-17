/*
 * Copyright (C) 2018 The Android Open Source Project
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

import org.jetbrains.annotations.NotNull;

public interface ToolWindowCallback {
  /**
   * Restore this tool window if it is currently hidden.
   */
  default void restore() {}

  /**
   * Hide this tool window if this is in auto hide mode.
   */
  default void autoHide() {}

  /**
   * Start filtering with the specified initial search string.
   *
   * The content of a tool window can decide to start a search
   * e.g. when the user types a character. That character could
   * be the beginning of the search string.
   */
  default void startFiltering(@NotNull String initialSearchString) {}

  /**
   * Hides the search box and stops an existing search.
   */
  default void stopFiltering() {}

  /**
   * Update all actions on the toolbar immediately.
   */
  default void updateActions() {}
}
