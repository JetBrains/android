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
package com.android.tools.profilers;

import com.intellij.util.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface UiMessageHandler {
  void displayErrorMessage(@Nullable JComponent parent, @NotNull String title, @NotNull String message);

  /**
   * @param doNotShowSettingSaver if not null, a "do not ask again" option will be shown, and the function will be invoked to save the
   *                              settings of whether the dialog should be shown next time.
   * @return true if ok. false if cancelled.
   */
  boolean displayOkCancelMessage(@NotNull String title, @NotNull String message, @NotNull String okText, @NotNull String cancelText,
                                 @Nullable Icon icon, @Nullable Consumer<Boolean> doNotShowSettingSaver);
}
