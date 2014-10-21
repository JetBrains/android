/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.tools.idea.run.ExternalToolRunner;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class EmulatorRunner extends ExternalToolRunner {
  public EmulatorRunner(@NotNull Project project, @NotNull String consoleTitle, @NotNull GeneralCommandLine commandLine) {
    super(project, consoleTitle, commandLine);
  }

  @Override
  protected void fillToolBarActions(DefaultActionGroup toolbarActions) {
    // override default implementation: we don't want to add a stop action since we can't just kill the emulator process
    // without leaving stale lock files around
  }
}
