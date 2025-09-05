/*
 * Copyright 2025 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.bazel;

import com.google.idea.blaze.base.projectview.ProjectViewManager;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.projectview.section.sections.BazelBinarySection;
import com.google.idea.blaze.base.settings.BlazeUserSettings;
import com.intellij.openapi.project.Project;
import java.io.File;
import java.util.function.Supplier;

/** A provider for the path to the bazel binary. */
public class BazelBinaryPathProvider implements Supplier<String> {

  private final Project project;

  public BazelBinaryPathProvider(Project project) {
    this.project = project;
  }

  @Override
  public String get() {
    ProjectViewSet projectViewSet = ProjectViewManager.getInstance(project).getProjectViewSet();
    if (projectViewSet != null) {
      File projectSpecificBinary = projectViewSet.getScalarValue(BazelBinarySection.KEY).orElse(null);
      if (projectSpecificBinary != null) {
        return projectSpecificBinary.getPath();
      }
    }
    return BlazeUserSettings.getInstance().getBazelBinaryPath();
  }
}