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

import com.android.tools.idea.gradle.IdeaJavaProject;
import com.android.tools.idea.gradle.customizer.ModuleCustomizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
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
import static com.intellij.openapi.util.text.StringUtil.endsWithIgnoreCase;

public class ArtifactsByConfigurationModuleCustomizer implements ModuleCustomizer<IdeaJavaProject> {
  @Override
  public void customizeModule(@NotNull Project project, @NotNull ModifiableRootModel moduleModel, @Nullable IdeaJavaProject javaModel) {
    if (javaModel != null) {
      Map<String, Set<File>> artifactsByConfiguration = javaModel.getArtifactsByConfiguration();
      if (artifactsByConfiguration != null) {
        for (Map.Entry<String, Set<File>> entry : artifactsByConfiguration.entrySet()) {
          Set<File> artifacts = entry.getValue();
          if (artifacts != null && !artifacts.isEmpty()) {
            for (File artifact : artifacts) {
              if (!artifact.isFile() || !endsWithIgnoreCase(artifact.getName(), DOT_JAR)) {
                // We only expose artifacts that are jar files.
                continue;
              }
              String libraryName = moduleModel.getModule().getName() + "." + getNameWithoutExtension(artifact);
              LibraryTable libraryTable = ProjectLibraryTable.getInstance(project);
              Library library = libraryTable.getLibraryByName(libraryName);
              if (library == null) {
                // Create library.
                LibraryTable.ModifiableModel libraryTableModel = libraryTable.getModifiableModel();
                try {
                  library = libraryTableModel.createLibrary(libraryName);
                  Library.ModifiableModel libraryModel = library.getModifiableModel();
                  try {
                    String url = pathToUrl(artifact.getPath());
                    libraryModel.addRoot(url, CLASSES);
                  }
                  finally {
                    libraryModel.commit();
                  }
                }
                finally {
                  libraryTableModel.commit();
                }
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
}
