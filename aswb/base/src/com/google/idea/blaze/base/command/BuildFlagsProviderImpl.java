/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.base.command;

import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.List;

/** Flags added to blaze/bazel build commands. */
public class BuildFlagsProviderImpl implements BuildFlagsProvider {

  @Override
  public void addBuildFlags(
      Project project,
      ProjectViewSet projectViewSet,
      BlazeCommandName command,
      BlazeInvocationContext invocationContext,
      List<String> flags) {
    flags.add("--curses=no");
    // Colorer output is not readable in test logs.
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      flags.add("--color=yes");
    }
    flags.add("--progress_in_terminal_title=no");
  }
}
