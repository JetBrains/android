/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.mlkit;

import static com.google.common.collect.Streams.stream;

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileType;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.mlkit.MlNames;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides common utility methods.
 */
public final class MlUtils {

  private static final ImmutableList<String> REQUIRED_DEPENDENCY_LIST = ImmutableList.of(
    "org.tensorflow:tensorflow-lite-support:0.1.0",
    "org.tensorflow:tensorflow-lite-metadata:0.1.0"
  );

  private MlUtils() {
  }

  public static boolean isMlModelBindingBuildFeatureEnabled(@NotNull Module module) {
    return AndroidFacet.getInstance(module) != null && ProjectSystemUtil.getModuleSystem(module).isMlModelBindingEnabled();
  }

  public static boolean isModelFileInMlModelsFolder(@NotNull Module module, @NotNull VirtualFile file) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    return androidFacet != null &&
           FileTypeRegistry.getInstance().isFileOfType(file, TfliteModelFileType.INSTANCE) &&
           SourceProviders.getInstance(androidFacet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
             .flatMap(sourceProvider -> stream(sourceProvider.getMlModelsDirectories()))
             .anyMatch(mlDir -> VfsUtilCore.isAncestor(mlDir, file, true));
  }

  /**
   * Computes the class name based on the file name and location, returns empty string if it can not be determined.
   */
  @NotNull
  public static String computeModelClassName(@NotNull Module module, @NotNull VirtualFile file) {
    String relativePath = relativePathToMlModelsFolder(module, file);
    return relativePath != null ? MlNames.computeModelClassName(relativePath) : "";
  }

  @Nullable
  private static String relativePathToMlModelsFolder(@NotNull Module module, @NotNull VirtualFile file) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidFacet == null || !FileTypeRegistry.getInstance().isFileOfType(file, TfliteModelFileType.INSTANCE)) {
      return null;
    }

    Optional<VirtualFile> ancestor =
      SourceProviders.getInstance(androidFacet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
        .flatMap(sourceProvider -> stream(sourceProvider.getMlModelsDirectories()))
        .filter(mlDir -> VfsUtilCore.isAncestor(mlDir, file, true))
        .findFirst();
    return ancestor.map(virtualFile -> VfsUtilCore.getRelativePath(file, virtualFile)).orElse(null);
  }


  /**
   * Returns the set of missing dependencies required to enable GPU option in the auto-generated model classes.
   */
  @NotNull
  public static List<GradleCoordinate> getMissingTfliteGpuDependencies(@NotNull Module module) {
    return getMissingDependencies(module, ImmutableList.of("org.tensorflow:tensorflow-lite-gpu:2.3.0"));
  }

  /**
   * Returns the set of missing dependencies that are required by the auto-generated model classes.
   */
  @NotNull
  public static List<GradleCoordinate> getMissingRequiredDependencies(@NotNull Module module) {
    return getMissingDependencies(module, REQUIRED_DEPENDENCY_LIST);
  }

  @NotNull
  private static List<GradleCoordinate> getMissingDependencies(@NotNull Module module, ImmutableList<String> dependencies) {
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    List<GradleCoordinate> pendingDeps = new ArrayList<>();
    for (String requiredDepString : dependencies) {
      GradleCoordinate requiredDep = Objects.requireNonNull(GradleCoordinate.parseCoordinateString(requiredDepString));
      GradleCoordinate requiredDepInAnyVersion = new GradleCoordinate(requiredDep.getGroupId(), requiredDep.getArtifactId(), "+");
      if (moduleSystem.getRegisteredDependency(requiredDepInAnyVersion) == null) {
        pendingDeps.add(requiredDep);
      }
    }
    return pendingDeps;
  }

  /**
   * Returns the list of {@link GradleCoordinate} pair which consists of current registered dependency and the required dependency having
   * higher version.
   */
  @NotNull
  public static List<Pair<GradleCoordinate, GradleCoordinate>> getDependenciesLowerThanRequiredVersion(@NotNull Module module) {
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    List<Pair<GradleCoordinate, GradleCoordinate>> resultDepPairList = new ArrayList<>();
    for (String requiredDepString : REQUIRED_DEPENDENCY_LIST) {
      GradleCoordinate requiredDep = Objects.requireNonNull(GradleCoordinate.parseCoordinateString(requiredDepString));
      GradleCoordinate requiredDepInAnyVersion = new GradleCoordinate(requiredDep.getGroupId(), requiredDep.getArtifactId(), "+");
      GradleCoordinate registeredDep = moduleSystem.getRegisteredDependency(requiredDepInAnyVersion);
      if (registeredDep != null && GradleCoordinate.COMPARE_PLUS_LOWER.compare(registeredDep, requiredDep) < 0) {
        resultDepPairList.add(Pair.create(registeredDep, requiredDep));
      }
    }
    return resultDepPairList;
  }
}
