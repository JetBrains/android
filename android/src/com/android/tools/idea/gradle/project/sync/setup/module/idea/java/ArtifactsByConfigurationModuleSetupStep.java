/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import static com.android.SdkConstants.DOT_JAR;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.text.StringUtil.endsWithIgnoreCase;

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import java.io.File;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class ArtifactsByConfigurationModuleSetupStep extends JavaModuleSetupStep {
  @NotNull private final JavaModuleDependenciesSetup myDependenciesSetup;

  public ArtifactsByConfigurationModuleSetupStep() {
    myDependenciesSetup = new JavaModuleDependenciesSetup();
  }

  /**
   * If the {@code javaModuleModel} represent a JAR wrapper module sets up an exported dependency of the module on the JAR files it wraps.
   */
  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull JavaModuleModel javaModuleModel) {
    // Java wrapper modules have the only configuration names "default".
    if (javaModuleModel.getArtifactsByConfiguration().size() != 1) return;
    Map.Entry<String, Set<File>> entry = javaModuleModel.getArtifactsByConfiguration().entrySet().iterator().next();
    if (!entry.getKey().equals("default")) return;

    Module module = context.getModule();
    IdeModifiableModelsProvider ideModelsProvider = context.getIdeModelsProvider();

    Set<File> artifacts = entry.getValue();
    if (artifacts != null && !artifacts.isEmpty()) {
      for (File artifact : artifacts) {
        if (!artifact.isFile() || !endsWithIgnoreCase(artifact.getName(), DOT_JAR)) {
          // We only expose artifacts that are jar files.
          continue;
        }
        String artifactName = getNameWithoutExtension(artifact);

        String libraryName = module.getName() + "." + artifactName;
        myDependenciesSetup
          .setUpLibraryDependency(module, ideModelsProvider, libraryName, COMPILE, artifact, null, null, true/* exported */);
      }
    }
  }
}
