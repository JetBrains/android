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
package com.android.tools.profilers.common;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface StackTraceView {
  void clearStackFrames();

  void setStackFrames(@NotNull String stackString);

  void setStackFrames(@NotNull List<CodeLocation> stackFrames);

  @NotNull
  JComponent getComponent();

  /**
   * @return selected {@link CodeLocation} in the view or null if nothing is selected
   */
  @Nullable
  CodeLocation getSelectedLocation();

  /**
   * Selects the specified {@link CodeLocation}.
   *
   * @param location  the CodeLocation to select, if null clears the selection
   * @return true, if the given CodeLocation can be selected
   */
  boolean selectCodeLocation(@Nullable CodeLocation location);

  @NotNull
  List<CodeLocation> getCodeLocations();
}
