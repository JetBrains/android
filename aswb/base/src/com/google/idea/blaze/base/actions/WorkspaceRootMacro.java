/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.actions;

import com.google.idea.blaze.base.model.primitives.WorkspaceRoot;
import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.ide.macro.Macro;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * An {@linkplain Macro external tool macro} which provides the workspace root of the current
 * project.
 */
public final class WorkspaceRootMacro extends Macro {

  @Override
  public String getName() {
    return "WorkspaceRoot";
  }

  @Override
  public String getDescription() {
    return String.format(
        "The %s workspace root path of the project", Blaze.defaultBuildSystemName());
  }

  @Nullable
  @Override
  public String expand(DataContext dataContext) {
    Project project = dataContext.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      return null;
    }

    WorkspaceRoot root = WorkspaceRoot.fromProjectSafe(project);
    return root != null ? root.toString() : null;
  }
}
