/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.inspectors.common.ui.stacktrace;

import com.android.tools.inspectors.common.api.stacktrace.StackTraceModel;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

/**
 * A component that shows an interactive callstack, where interacting with it can jump the user to
 * the relevant location in code.
 */
public interface StackTraceView {
  @NotNull
  StackTraceModel getModel();

  @NotNull
  JComponent getComponent();
}
