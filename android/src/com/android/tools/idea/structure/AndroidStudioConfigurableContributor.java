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

import com.android.tools.idea.gradle.parser.GradleSettingsFile;
import com.android.tools.idea.gradle.util.Projects;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectStructureConfigurableContributor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.StructureConfigurableContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AndroidStudioConfigurableContributor extends ProjectStructureConfigurableContributor {

  private static final List<? extends Configurable> EMPTY_PROJECT_CONFIGURABLES = ImmutableList.of();

  @NotNull
  @Override
  public List<? extends Configurable> getExtraPlatformConfigurables(@NotNull Project project,
                                                                    @NotNull StructureConfigurableContext context) {
    return ImmutableList.of(new AndroidHomeConfigurable());
  }

  @NotNull
  @Override
  public List<? extends Configurable> getExtraProjectConfigurables(@NotNull Project project,
                                                                   @NotNull StructureConfigurableContext context) {
    if (Projects.isGradleProject(project) && GradleSettingsFile.get(project) != null) {
      AndroidModuleStructureConfigurable androidModuleStructureConfigurable = AndroidModuleStructureConfigurable.getInstance(project);
      androidModuleStructureConfigurable.init(context);
      return ImmutableList.of(androidModuleStructureConfigurable);
    } else {
      return EMPTY_PROJECT_CONFIGURABLES;
    }
  }
}
