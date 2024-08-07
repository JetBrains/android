/*
 * Copyright 2024 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.execution;

import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.settings.BlazeImportSettings.ProjectType;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;

/** Interface for checking if tools execution is allowed for the imported project. */
public interface BazelGuard {

  ExtensionPointName<BazelGuard> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.BlazeGuard");

  void checkIsExecutionAllowed(Project project) throws ExecutionDeniedException;

  static void checkExtensionsIsExecutionAllowed(Project project) throws ExecutionDeniedException {
    if (Blaze.getProjectType(project) != ProjectType.UNKNOWN
        && Blaze.getBuildSystemName(project) == BuildSystemName.Blaze) {
      return; // Tools for Blaze projects are always trusted
    }
    for (var extension : EP_NAME.getExtensionList()) {
      extension.checkIsExecutionAllowed(project);
    }
  }
}
