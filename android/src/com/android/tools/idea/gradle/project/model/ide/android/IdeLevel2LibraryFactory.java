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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.level2.Library;
import com.android.utils.FileUtils;
import com.android.utils.ImmutableCollectors;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.stream.Collectors;

import static com.android.builder.model.level2.Library.*;

/**
 * Creates instance of {@link Library}.
 */
class IdeLevel2LibraryFactory {
  /**
   * @param library    Instance of level 2 library returned by android plugin.
   * @param modelCache Cache that stores previously copied entries.
   * @return Deep copy of {@link Library} based on library type.
   */
  @NotNull
  static Library create(@NotNull Library library, @NotNull ModelCache modelCache) {
    if (library.getType() == LIBRARY_ANDROID) {
      File folder = library.getFolder();
      return new IdeLevel2AndroidLibrary(library, modelCache,
                                         library.getArtifactAddress(),
                                         library.getFolder(),
                                         getFullPath(folder, library.getManifest()),
                                         getFullPath(folder, library.getJarFile()),
                                         getFullPath(folder, library.getResFolder()),
                                         getFullPath(folder, library.getAssetsFolder()),
                                         library.getLocalJars().stream().map(jar -> getFullPath(folder, jar)).collect(ImmutableCollectors.toImmutableList()),
                                         getFullPath(folder, library.getJniFolder()),
                                         getFullPath(folder, library.getAidlFolder()),
                                         getFullPath(folder, library.getRenderscriptFolder()),
                                         getFullPath(folder, library.getProguardRules()),
                                         getFullPath(folder, library.getLintJar()),
                                         getFullPath(folder, library.getExternalAnnotations()),
                                         getFullPath(folder, library.getPublicResources()),
                                         library.getArtifact(),
                                         library.getSymbolFile());
    }
    if (library.getType() == LIBRARY_JAVA) {
      return new IdeLevel2JavaLibrary(library.getArtifactAddress(), library.getArtifact(), modelCache, library);
    }
    if (library.getType() == LIBRARY_MODULE) {
      return new IdeLevel2ModuleLibrary(library.getArtifactAddress(), library.getArtifact(), library.getProjectPath(), library.getVariant(),
                                        modelCache, library);
    }
    throw new UnsupportedOperationException("Unknown library type " + library.getType());
  }

  /**
   * @param androidLibrary Instance of {@link AndroidLibrary} returned by android plugin.
   * @param modelCache     Cache that stores previously copied entries.
   * @return Instance of {@link Library} based on dependency type.
   */
  @NotNull
  static Library create(@NotNull AndroidLibrary androidLibrary, @NotNull ModelCache modelCache) {
    if (androidLibrary.getProject() != null) {
      return new IdeLevel2ModuleLibrary(computeAddress(androidLibrary), androidLibrary.getJarFile(), androidLibrary.getProject(),
                                        androidLibrary.getProjectVariant(), modelCache, androidLibrary);
    }
    else {
      return new IdeLevel2AndroidLibrary(androidLibrary, modelCache, computeAddress(androidLibrary), androidLibrary.getFolder(),
                                         androidLibrary.getManifest().getPath(), androidLibrary.getJarFile().getPath(),
                                         androidLibrary.getResFolder().getPath(), androidLibrary.getAssetsFolder().getPath(),
                                         androidLibrary.getLocalJars().stream().map(File::getPath).collect(Collectors.toList()),
                                         androidLibrary.getJniFolder().getPath(), androidLibrary.getAidlFolder().getPath(),
                                         androidLibrary.getRenderscriptFolder().getPath(), androidLibrary.getProguardRules().getPath(),
                                         androidLibrary.getLintJar().getPath(), androidLibrary.getExternalAnnotations().getPath(),
                                         androidLibrary.getPublicResources().getPath(), androidLibrary.getBundle(),
                                         getSymbolFilePath(androidLibrary));
    }
  }

  @NotNull
  private static String getFullPath(@NotNull File folder, @NotNull String fileName) {
    return FileUtils.join(folder, fileName).getPath();
  }

  @Nullable
  private static String getSymbolFilePath(@NotNull AndroidLibrary androidLibrary) {
    try {
      return androidLibrary.getSymbolFile().getPath();
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  /**
   * @param javaLibrary Instance of {@link JavaLibrary} returned by android plugin.
   * @param modelCache  Cache that stores previously copied entries.
   * @return Instance of {@link Library} based on dependency type.
   */
  @NotNull
  static Library create(@NotNull JavaLibrary javaLibrary, @NotNull ModelCache modelCache) {
    String project = getProject(javaLibrary);
    if (project != null) {
      // Java modules don't have variant.
      return new IdeLevel2ModuleLibrary(computeAddress(javaLibrary), javaLibrary.getJarFile(), project, null, modelCache,
                                        javaLibrary);
    }
    else {
      return new IdeLevel2JavaLibrary(computeAddress(javaLibrary), javaLibrary.getJarFile(), modelCache, javaLibrary);
    }
  }

  @Nullable
  private static String getProject(@NotNull JavaLibrary javaLibrary) {
    try {
      return javaLibrary.getProject();
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  /**
   * @param projectPath Name of module dependencies.
   * @param modelCache  Cache that stores previously copied entries.
   * @return An instance of {@link Library} of type LIBRARY_MODULE.
   */
  @NotNull
  static Library create(@NotNull String projectPath, @NotNull ModelCache modelCache) {
    return new IdeLevel2ModuleLibrary(projectPath, new File(projectPath), projectPath, null, modelCache, projectPath);
  }

  /**
   * @param library Instance of level 1 Library.
   * @return The artifact address that can be used as unique identifier in global library map.
   */
  @NotNull
  static String computeAddress(@NotNull com.android.builder.model.Library library) {
    return library.getResolvedCoordinates().toString().intern();
  }
}
