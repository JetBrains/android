/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.cleanup;

import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableEP;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.settings.GradleConfigurable;
import org.jetbrains.plugins.gradle.settings.GradleRunnerConfigurable;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.openapi.options.Configurable.PROJECT_CONFIGURABLE;

class GradleRunnerCleanupTask extends AndroidStudioCleanUpTask {
  @Override
  void doCleanUp(@NotNull Project project) {
    ExtensionsArea area = Extensions.getArea(project);
    ExtensionPoint<ConfigurableEP<Configurable>> projectConfigurable = area.getExtensionPoint(PROJECT_CONFIGURABLE);

    // https://code.google.com/p/android/issues/detail?id=213178
    // Disable the Gradle -> Runner settings.
    for (ConfigurableEP<Configurable> configurableEP : projectConfigurable.getExtensions()) {
      if (GradleConfigurable.class.getName().equals(configurableEP.instanceClass)) {
        List<ConfigurableEP> children = new ArrayList<>();
        for (ConfigurableEP child : configurableEP.children) {
          if (!GradleRunnerConfigurable.class.getName().equals(child.instanceClass)) {
            children.add(child);
          }
        }
        configurableEP.children = children.toArray(new ConfigurableEP[0]);
      }
    }
  }
}
