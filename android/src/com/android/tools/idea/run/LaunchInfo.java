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
package com.android.tools.idea.run;

import com.intellij.execution.Executor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import org.jetbrains.annotations.NotNull;

public class LaunchInfo {
  @NotNull public final Executor executor;
  @NotNull public final ProgramRunner runner;
  @NotNull public final ExecutionEnvironment env;
  @NotNull public final ConsoleProvider consoleProvider;

  public LaunchInfo(@NotNull Executor executor,
                    @NotNull ProgramRunner runner,
                    @NotNull ExecutionEnvironment env,
                    @NotNull ConsoleProvider consoleProvider) {
    this.executor = executor;
    this.runner = runner;
    this.env = env;
    this.consoleProvider = consoleProvider;
  }
}
