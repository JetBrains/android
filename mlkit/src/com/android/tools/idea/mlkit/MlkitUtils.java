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

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.mlkit.lightpsi.LightModelClass;
import com.android.tools.idea.projectsystem.AndroidModuleSystem;
import com.android.tools.idea.projectsystem.ProjectSystemUtil;
import com.google.common.base.CaseFormat;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.WolfTheProblemSolver;
import com.intellij.psi.PsiClass;
import com.intellij.util.indexing.FileBasedIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Provides common utility methods.
 */
public class MlkitUtils {

  private MlkitUtils() {
  }

  public static boolean isMlModelFileInAssetsFolder(@NotNull VirtualFile file) {
    // TODO(b/146357353): revisit the way to check if the file belongs to assets folder.
    return file.getFileType() == TfliteModelFileType.INSTANCE
           && file.getParent() != null
           && file.getParent().getName().equals(SdkConstants.FD_ASSETS);
  }

  // TODO(b/144867508): revisit later.
  @NotNull
  public static String computeModelClassName(@NotNull String modelFileUrl) {
    String modelFileName = FileUtil.getNameWithoutExtension(VfsUtil.extractFileName(modelFileUrl));
    return CaseFormat.LOWER_UNDERSCORE.to(CaseFormat.UPPER_CAMEL, modelFileName);
  }

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
  public static List<GradleCoordinate> getMissingDependencies(@NotNull Module module, @NotNull VirtualFile modelFile) {
    // TODO(148887002): calculate required deps based on the given model file and figure out how to handle versions.
    ImmutableList<String> requiredDeps = ImmutableList.of(
      "org.apache.commons:commons-compress:1.19",
      "org.tensorflow:tensorflow-lite:1.13.1",
      "org.tensorflow:tensorflow-lite-support:0.0.0-nightly"
    );

    AndroidModuleSystem moduleSystem = ProjectSystemUtil.getModuleSystem(module);
    List<GradleCoordinate> pendingDeps = new ArrayList<>();
    for (String requiredDepString : requiredDeps) {
      GradleCoordinate requiredDep = GradleCoordinate.parseCoordinateString(requiredDepString);
      GradleCoordinate requiredDepInAnyVersion = new GradleCoordinate(requiredDep.getGroupId(), requiredDep.getArtifactId(), "+");
      if (moduleSystem.getRegisteredDependency(requiredDepInAnyVersion) == null) {
        pendingDeps.add(requiredDep);
      }
    }
    return pendingDeps;
  }

  /**
   * Checks if dependencies required by all auto-generated classes exist.
   */
  public static void verifyDependenciesForAllModelFiles(@NotNull Module module) {
    FileBasedIndex index = FileBasedIndex.getInstance();
    index.processAllKeys(MlModelFileIndex.INDEX_ID, key -> {
      index.processValues(MlModelFileIndex.INDEX_ID, key, null, (file, value) -> {
        verifyDependenciesForModelFile(module, file);
        return true;
      }, module.getModuleScope(false));

      return true;
    }, module.getModuleScope(false), null);
  }

  /**
   * Checks if all dependencies required by the auto-generated class exist. If not, flags the model file with underlining its file name by a
   * red squiggly line.
   */
  public static void verifyDependenciesForModelFile(@NotNull Module module, @NotNull VirtualFile modelFile) {
    WolfTheProblemSolver problemSolver = WolfTheProblemSolver.getInstance(module.getProject());
    if (getMissingDependencies(module, modelFile).isEmpty()) {
      problemSolver.clearProblemsFromExternalSource(modelFile, module);
    }
    else {
      problemSolver.reportProblemsFromExternalSource(modelFile, module);
    }
  }
}
