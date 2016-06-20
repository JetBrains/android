/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.customizer.java;

import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Set;

import static com.android.SdkConstants.DOT_JAR;
import static com.android.tools.idea.gradle.customizer.AbstractDependenciesModuleCustomizer.pathToUrl;
import static com.intellij.openapi.roots.DependencyScope.COMPILE;
import static com.intellij.openapi.roots.OrderRootType.CLASSES;
import static com.intellij.openapi.util.io.FileUtil.getNameWithoutExtension;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static com.intellij.openapi.util.text.StringUtil.endsWithIgnoreCase;

public class ArtifactsByConfigurationModuleCustomizer implements ModuleCustomizer<JavaProject> {
  @Override
  public void customizeModule(@NotNull Project project,
                              @NotNull Module module,
                              @NotNull IdeModifiableModelsProvider modelsProvider,
                              @Nullable JavaProject javaProject) {
    if (javaProject != null) {
      ModifiableRootModel moduleModel = modelsProvider.getModifiableRootModel(module);

      for (Map.Entry<String, Set<File>> entry : javaProject.getArtifactsByConfiguration().entrySet()) {
        Set<File> artifacts = entry.getValue();
        if (artifacts != null && !artifacts.isEmpty()) {
          for (File artifact : artifacts) {
            if (!artifact.isFile() || !endsWithIgnoreCase(artifact.getName(), DOT_JAR)) {
              // We only expose artifacts that are jar files.
              continue;
            }
            File buildFolderPath = javaProject.getBuildFolderPath();
            String artifactName = getNameWithoutExtension(artifact);

            if (buildFolderPath != null &&
                buildFolderPath.isDirectory() &&
                isAncestor(buildFolderPath, artifact, true) &&
                module.getName().equals(artifactName)) {
              // This is the jar obtained by compiling the module, no need to add it as dependency.
              continue;
            }
            String libraryName = module.getName() + "." + artifactName;
            Library library = modelsProvider.getLibraryByName(libraryName);
            if (library == null) {
              // Create library.
              library = modelsProvider.createLibrary(libraryName);
              Library.ModifiableModel libraryModel = modelsProvider.getModifiableLibraryModel(library);
              String url = pathToUrl(artifact.getPath());
              libraryModel.addRoot(url, CLASSES);
              LibraryOrderEntry orderEntry = moduleModel.addLibraryEntry(library);
              orderEntry.setScope(COMPILE);
              orderEntry.setExported(true);
            }
          }
        }
      }
    }
  }
}
