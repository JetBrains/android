/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.project.sync.issues.SdkInManifestIssuesReporter.SdkProperty;
import com.android.tools.idea.gradle.project.sync.issues.processor.RemoveSdkFromManifestProcessor;
import com.android.tools.idea.project.hyperlink.NotificationHyperlink;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.ValueType.NONE;

public class RemoveSdkFromManifestHyperlink extends NotificationHyperlink {
  @NotNull private final Collection<Module> myModules;
  @NotNull private final SdkProperty mySdkProperty;

  public RemoveSdkFromManifestHyperlink(@NotNull Collection<Module> modules, @NotNull SdkProperty property) {
    super("remove.sdk.from.manifest", getMessage(modules, property));
    myModules = modules;
    mySdkProperty = property;
  }

  @NotNull
  private static String getMessage(@NotNull Collection<Module> modules, @NotNull SdkProperty property) {
    assert !modules.isEmpty();
    ProjectBuildModel projectBuildModel = ProjectBuildModel.get(modules.iterator().next().getProject());
    for (Module module : modules) {
      GradleBuildModel buildModel = projectBuildModel.getModuleBuildModel(module);
      if (buildModel != null) {
        ResolvedPropertyModel propertyInBuildFile = property.getBuildFileFunction().apply(buildModel.android().defaultConfig());
        if (propertyInBuildFile.getValueType() == NONE) {
          // property is not in build file.
          return String.format("Move %s to build file%s and sync project", property.getPropertyName(), modules.size() > 1 ? "s" : "");
        }
      }
    }
    // Build file doesn't exist, or property has been defined in build file.
    return String.format("Remove %s and sync project", property.getPropertyName());
  }

  @Override
  protected void execute(@NotNull Project project) {
    RemoveSdkFromManifestProcessor processor = new RemoveSdkFromManifestProcessor(project, myModules, mySdkProperty);
    processor.setPreviewUsages(true);
    processor.run();
  }
}
