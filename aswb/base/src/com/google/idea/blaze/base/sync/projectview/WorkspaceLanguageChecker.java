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
package com.google.idea.blaze.base.sync.projectview;

import com.google.idea.blaze.base.model.primitives.LanguageClass;
import com.intellij.openapi.project.Project;
import java.util.Optional;

/** Checks if a language is active in a project. */
public interface WorkspaceLanguageChecker {

  /**
   * Returns true/false if a bazel/blaze project has the language active/inactive, or Optional if we
   * cannot tell (error).
   */
  Optional<Boolean> isLanguageActiveInProject(LanguageClass language);

  static WorkspaceLanguageChecker getInstance(Project project) {
    return project.getService(WorkspaceLanguageChecker.class);
  }
}
