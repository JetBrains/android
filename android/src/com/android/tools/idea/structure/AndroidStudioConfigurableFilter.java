/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.structure;

import com.android.tools.idea.gradle.util.Projects;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurableFilter;
import org.jetbrains.annotations.NotNull;

public class AndroidStudioConfigurableFilter extends ProjectStructureConfigurableFilter {
  private static final String SHOW_ALL_CONFIGURABLES = "android.show_all_configurables";

  @Override
  public boolean isAvailable(@NotNull ConfigurableId setting, @NotNull Project project) {
    if (!Projects.isGradleProject(project) || Boolean.getBoolean(SHOW_ALL_CONFIGURABLES)) {
      return true;
    }
    switch(setting) {
      case JDK_LIST:
      case MODULES:
      case PROJECT:
      case GLOBAL_LIBRARIES:
      case ARTIFACTS:
      case FACETS:
      case PROJECT_LIBRARIES:
      default:
        return false;
    }
  }
}
