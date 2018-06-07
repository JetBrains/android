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

import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.tools.idea.io.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.text.StringUtil.endsWithIgnoreCase;

public class ArtifactsByConfigurationModuleSetupStep extends JavaModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull JavaModuleModel javaModuleModel) {
    ModifiableRootModel moduleModel = context.getModifiableRootModel();
    Module module = context.getModule();
    IdeModifiableModelsProvider ideModelsProvider = context.getIdeModelsProvider();

    for (Map.Entry<String, Set<File>> entry : javaModuleModel.getArtifactsByConfiguration().entrySet()) {
      Set<File> artifacts = entry.getValue();
      if (artifacts != null && !artifacts.isEmpty()) {
        for (File artifact : artifacts) {
          if (!artifact.isFile() || !endsWithIgnoreCase(artifact.getName(), DOT_JAR)) {
            // We only expose artifacts that are jar files.
            continue;
          }
          File buildFolderPath = javaModuleModel.getBuildFolderPath();
          String artifactName = getNameWithoutExtension(artifact);

          if (buildFolderPath != null &&
              buildFolderPath.isDirectory() &&
              isAncestor(buildFolderPath, artifact, true) &&
              module.getName().equals(artifactName)) {
            // This is the jar obtained by compiling the module, no need to add it as dependency.
            continue;
          }
          String libraryName = module.getName() + "." + artifactName;
          Library library = ideModelsProvider.getLibraryByName(libraryName);
          if (library == null) {
            // Create library.
            library = ideModelsProvider.createLibrary(libraryName);
            Library.ModifiableModel libraryModel = ideModelsProvider.getModifiableLibraryModel(library);
            String url = pathToIdeaUrl(artifact);
            libraryModel.addRoot(url, CLASSES);
          }
          LibraryOrderEntry orderEntry = moduleModel.addLibraryEntry(library);
          orderEntry.setScope(COMPILE);
          orderEntry.setExported(true);
        }
      }
    }
  }
}
