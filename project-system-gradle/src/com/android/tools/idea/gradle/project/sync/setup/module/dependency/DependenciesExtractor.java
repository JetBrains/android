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
package com.android.tools.idea.gradle.project.sync.setup.module.dependency;

import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.model.IdeAndroidLibrary;
import com.android.tools.idea.gradle.model.IdeDependencies;
import com.android.tools.idea.gradle.model.IdeJavaLibrary;
import com.android.tools.idea.gradle.model.IdeModuleLibrary;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.io.FilePaths;
import com.android.tools.idea.projectsystem.ModuleSystemUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import java.io.File;
import org.jetbrains.annotations.NotNull;

/**
 * Creates {@link DependencySet} from variant or artifact.
 */
public class DependenciesExtractor {
  @NotNull
  public static DependenciesExtractor getInstance() {
    return ApplicationManager.getApplication().getService(DependenciesExtractor.class);
  }

  /**
   * @param artifactDependencies to extract dependencies from.
   * @return Instance of {@link DependencySet} retrieved from given artifact.
   */
  @NotNull
  public DependencySet extractFrom(@NotNull IdeDependencies artifactDependencies,
                                   @NotNull ModuleFinder moduleFinder) {
    DependencySet dependencies = new DependencySet();
    populate(dependencies, artifactDependencies, moduleFinder);
    return dependencies;
  }

  private static void populate(@NotNull DependencySet dependencies,
                               @NotNull IdeDependencies artifactDependencies,
                               @NotNull ModuleFinder moduleFinder) {

    for (IdeJavaLibrary library : artifactDependencies.getJavaLibraries()) {
      LibraryDependency libraryDependency =
        LibraryDependency.create(library.getArtifact(), ImmutableList.of(library.getArtifact()));
      dependencies.add(libraryDependency);
    }

    for (IdeAndroidLibrary library : artifactDependencies.getAndroidLibraries()) {
      dependencies.add(createLibraryDependencyFromAndroidLibrary(library));
    }

    for (IdeModuleLibrary library : artifactDependencies.getModuleDependencies()) {
      String gradlePath = library.getProjectPath();
      if (isNotEmpty(gradlePath)) {
        Module module = moduleFinder.findModuleFromLibrary(library);
        if (module != null) {
          ModuleDependency dependency = new ModuleDependency(ModuleSystemUtil.getMainModule(module));
          dependencies.add(dependency);
        }
      }
    }
  }

  @NotNull
  private static LibraryDependency createLibraryDependencyFromAndroidLibrary(@NotNull IdeAndroidLibrary library) {
    ImmutableList.Builder<File> binaryPaths = new ImmutableList.Builder<>();
    for (String file : library.getCompileJarFiles()) {
      binaryPaths.add(FilePaths.stringToFile(file));
    }
    binaryPaths.add(FilePaths.stringToFile(library.getResFolder()));
    return LibraryDependency.create(library.getArtifact(), binaryPaths.build());
  }
}
