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
package com.google.idea.blaze.base.projectview.section;

import com.google.idea.blaze.base.projectview.ProjectView;
import com.google.idea.blaze.base.projectview.ProjectViewSet;
import com.google.idea.blaze.base.settings.BuildSystemName;
import com.intellij.openapi.extensions.ExtensionPointName;

/** Allows the adding default values to sections. Used during the wizard. */
public interface ProjectViewDefaultValueProvider {
  ExtensionPointName<ProjectViewDefaultValueProvider> EP_NAME =
      ExtensionPointName.create("com.google.idea.blaze.ProjectViewDefaultValueProvider");

  ProjectView addProjectViewDefaultValue(
      BuildSystemName buildSystemName,
      ProjectViewSet projectViewSet,
      ProjectView topLevelProjectView);

  SectionKey<?, ?> getSectionKey();
}
