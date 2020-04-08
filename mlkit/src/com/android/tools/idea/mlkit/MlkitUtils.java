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

import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

/**
 * Provides common utility methods.
 */
public class MlkitUtils {

  private MlkitUtils() {
  }

  public static boolean isMlModelBindingBuildFeatureEnabled(@NotNull Module module) {
    return AndroidFacet.getInstance(module) != null && ProjectSystemUtil.getModuleSystem(module).isMlModelBindingEnabled();
  }

  public static boolean isModelFileInMlModelsFolder(@NotNull Module module, @NotNull VirtualFile file) {
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    return androidFacet != null &&
           file.getFileType() == TfliteModelFileType.INSTANCE &&
           SourceProviders.getInstance(androidFacet).getCurrentAndSomeFrequentlyUsedInactiveSourceProviders().stream()
             .flatMap(sourceProvider -> sourceProvider.getMlModelsDirectories().stream())
             .anyMatch(mlDir -> VfsUtilCore.isAncestor(mlDir, file, true));
  }

  @NotNull
  public static PsiClass[] getLightModelClasses(@NotNull Project project, @NotNull Map<VirtualFile, MlModelMetadata> modelFileMap) {
    List<PsiClass> lightModelClassList = new ArrayList<>();
    for (Map.Entry<VirtualFile, MlModelMetadata> metadata : modelFileMap.entrySet()) {
      if (!metadata.getValue().isValidModel()) {
        continue;
      }

      Module module = ModuleUtilCore.findModuleForFile(metadata.getKey(), project);
      LightModelClass lightModelClass =
        module != null ? MlkitModuleService.getInstance(module).getOrCreateLightModelClass(metadata.getValue()) : null;
      if (lightModelClass != null) {
        lightModelClassList.add(lightModelClass);
      }
    }
    return lightModelClassList.toArray(PsiClass.EMPTY_ARRAY);
  }

  /**
   * Returns the set of missing dependencies that are required by the auto-generated model classes.
   */
  @NotNull
  public static List<GradleCoordinate> getMissingDependencies(@NotNull Module module, @NotNull VirtualFile modelFile) {
    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    List<GradleCoordinate> pendingDeps = new ArrayList<>();
    for (String requiredDepString : getRequiredDependencies()) {
      GradleCoordinate requiredDep = GradleCoordinate.parseCoordinateString(requiredDepString);
      GradleCoordinate requiredDepInAnyVersion = new GradleCoordinate(requiredDep.getGroupId(), requiredDep.getArtifactId(), "+");
      if (moduleSystem.getRegisteredDependency(requiredDepInAnyVersion) == null) {
        pendingDeps.add(requiredDep);
      }
    }
    return pendingDeps;
  }

  @NotNull
  public static ImmutableList<String> getRequiredDependencies() {
    // TODO(148887002): calculate required deps based on the given model file and figure out how to handle versions.
    return ImmutableList.of(
      "org.apache.commons:commons-compress:1.20",
      "org.tensorflow:tensorflow-lite:2.1.0",
      "org.tensorflow:tensorflow-lite-support:0.0.0-nightly"
    );
  }
}
