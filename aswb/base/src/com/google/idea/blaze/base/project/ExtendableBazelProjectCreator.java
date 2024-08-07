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
package com.google.idea.blaze.base.project;

import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.ide.util.projectWizard.ProjectBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import java.util.Optional;
import javax.annotation.Nullable;

/** Interface for creating a project with additional configuration. */
public interface ExtendableBazelProjectCreator {
  /**
   * Creates a project with additional configuration.
   *
   * @param builder the project builder
   * @param name the name of the project
   * @param path the path to the project
   * @return the created project, can be null if the project cannot be created
   */
  public Optional<Project> createProject(ProjectBuilder builder, String name, String path);

  /** Returns true if the project can be created. */
  public boolean canCreateProject(@Nullable BuildSystemName buildSystemName);

  static ExtendableBazelProjectCreator getInstance() {
    return ApplicationManager.getApplication().getService(ExtendableBazelProjectCreator.class);
  }
}
