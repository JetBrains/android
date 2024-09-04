/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.terminal;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.intellij.openapi.project.Project;
import java.util.Map;
import javax.annotation.Nullable;
import org.jetbrains.plugins.terminal.LocalTerminalCustomizer;

/** Set the default terminal path to the workspace root. */
public class DefaultTerminalLocationCustomizer extends LocalTerminalCustomizer {

  @Override
  @Nullable
  protected String getDefaultFolder(Project project) {
    return getWorkspaceRootPath(project);
  }

  @Override
  public String[] customizeCommandAndEnvironment(
      Project project, String[] command, Map<String, String> envs) {
    String workspaceRootPath = getWorkspaceRootPath(project);
    if (workspaceRootPath != null) {
      envs.put("PWD", workspaceRootPath);
    }
    return super.customizeCommandAndEnvironment(project, command, envs);
  }

  @Nullable
  private static String getWorkspaceRootPath(Project project) {
    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    return root != null ? root.toString() : null;
  }
}
