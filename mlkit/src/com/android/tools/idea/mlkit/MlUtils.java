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

import static com.android.ide.common.repository.WellKnownMavenArtifactId.TFLITE_GPU;
import static com.android.ide.common.repository.WellKnownMavenArtifactId.TFLITE_METADATA;
import static com.android.ide.common.repository.WellKnownMavenArtifactId.TFLITE_SUPPORT;
import static com.google.common.collect.Streams.stream;

import com.android.ide.common.gradle.Version;
import com.android.ide.common.repository.WellKnownMavenArtifactId;
import com.android.tools.idea.mlkit.viewer.TfliteModelFileType;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.RegisteredDependencyId;
import com.android.tools.idea.projectsystem.RegisteringModuleSystem;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.android.tools.idea.projectsystem.gradle.GradleModuleSystem;
import com.android.tools.idea.projectsystem.gradle.GradleRegisteredDependencyId;
import com.android.tools.mlkit.MlNames;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides common utility methods.
 */
public class MlUtils {

  private static final ImmutableMap<WellKnownMavenArtifactId,Version> REQUIRED_DEPENDENCY_LIST =
    ImmutableMap.<WellKnownMavenArtifactId,Version>builder()
      .put(TFLITE_SUPPORT, Version.parse("0.1.0"))
      .put(TFLITE_METADATA, Version.parse("0.1.0"))
      .build();

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
  public static List<WellKnownMavenArtifactId> getMissingTfliteGpuDependencies(@NotNull Module module) {
    return getMissingDependencies(module, ImmutableSet.of(TFLITE_GPU));
  }

  /**
   * Returns the set of missing dependencies that are required by the auto-generated model classes.
   */
  @NotNull
  public static List<WellKnownMavenArtifactId> getMissingRequiredDependencies(@NotNull Module module) {
    return getMissingDependencies(module, REQUIRED_DEPENDENCY_LIST.keySet());
  }

  @NotNull
  private static List<WellKnownMavenArtifactId> getMissingDependencies(@NotNull Module module, ImmutableSet<WellKnownMavenArtifactId> dependencies) {
    RegisteringModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module).getRegisteringModuleSystem();
    if (moduleSystem == null) return List.of();
    List<WellKnownMavenArtifactId> pendingDeps = new ArrayList<>();
    for (WellKnownMavenArtifactId id : dependencies) {
      if (!moduleSystem.hasRegisteredDependency(id)) {
        pendingDeps.add(id);
      }
    }
    return pendingDeps;
  }

  /**
   * Returns information about current registered dependencies and required higher version.
   */
  @NotNull
  public static List<Pair<RegisteredDependencyId,Map.Entry<WellKnownMavenArtifactId,Version>>> getDependenciesLowerThanRequiredVersion(@NotNull Module module) {
    AndroidModuleSystem androidModuleSystem = ProjectSystemUtil.getModuleSystem(module);
    List<Pair<RegisteredDependencyId,Map.Entry<WellKnownMavenArtifactId,Version>>> resultDepPairList = new ArrayList<>();
    if (androidModuleSystem instanceof GradleModuleSystem moduleSystem) {
      for (Map.Entry<WellKnownMavenArtifactId,Version> depInfo : REQUIRED_DEPENDENCY_LIST.entrySet()) {
        GradleRegisteredDependencyId id = moduleSystem.getRegisteredDependency(depInfo.getKey());
        // TODO: null safety everywhere
        if (id != null && id.getDependency().getVersion().getLowerBound().compareTo(depInfo.getValue()) < 0) {
          resultDepPairList.add(Pair.create(id, depInfo));
        }
      }
    }
    return resultDepPairList;
  }
}
