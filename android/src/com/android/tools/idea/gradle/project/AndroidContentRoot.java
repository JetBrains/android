/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.project;

import com.android.build.gradle.model.AndroidProject;
import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.build.gradle.model.Variant;
import com.android.builder.model.SourceProvider;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Creates a module content root from an {@link AndroidProject}.
 */
class AndroidContentRoot {
  @NotNull private final ContentRootData myContentRootData;

  AndroidContentRoot(@NotNull String rootPath) {
    myContentRootData = new ContentRootData(GradleConstants.SYSTEM_ID, rootPath);
  }

  /**
   * Adds this content root to the given IDE module.
   *
   * @param moduleInfo the given IDE module.
   */
  void addTo(@NotNull DataNode<ModuleData> moduleInfo) {
    moduleInfo.createChild(ProjectKeys.CONTENT_ROOT, myContentRootData);
  }

  /**
   * Stores the paths of 'source'/'test'/'excluded' directories, according to the project structure in the given Android-Gradle project.
   *
   * @param androidProject  structure of the Android-Gradle project.
   * @param selectedVariant selected build variant.
   */
  void storePaths(@NotNull AndroidProject androidProject, @NotNull Variant selectedVariant) {
    storePaths(selectedVariant);
    Collection<String> generatedDirPaths = getIncludedDirPaths();

    Map<String, ProductFlavorContainer> productFlavors = androidProject.getProductFlavors();
    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = productFlavors.get(flavorName);
      if (flavor != null) {
        storePaths(flavor);
      }
    }

    ProductFlavorContainer defaultConfig = androidProject.getDefaultConfig();
    storePaths(defaultConfig);

    excludeDirsExcept(generatedDirPaths);
  }

  private void storePaths(@NotNull Variant variant) {
    storePaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedSourceFolders());
    storePaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedResourceFolders());
    storePaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestSourceFolders());
    storePaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestResourceFolders());
  }

  @NotNull
  private Collection<String> getIncludedDirPaths() {
    Set<String> dirPaths = Sets.newHashSet();
    dirPaths.addAll(myContentRootData.getPaths(ExternalSystemSourceType.SOURCE));
    dirPaths.addAll(myContentRootData.getPaths(ExternalSystemSourceType.TEST));
    return dirPaths;
  }

  private void storePaths(@NotNull ProductFlavorContainer flavor) {
    storePaths(ExternalSystemSourceType.SOURCE, flavor.getSourceProvider());
    storePaths(ExternalSystemSourceType.TEST, flavor.getTestSourceProvider());
  }

  private void storePaths(@NotNull ExternalSystemSourceType sourceType, @NotNull SourceProvider sourceProvider) {
    storePaths(sourceType, sourceProvider.getAidlDirectories());
    storePaths(sourceType, sourceProvider.getAssetsDirectories());
    storePaths(sourceType, sourceProvider.getJavaDirectories());
    storePaths(sourceType, sourceProvider.getJniDirectories());
    storePaths(sourceType, sourceProvider.getRenderscriptDirectories());
    storePaths(sourceType, sourceProvider.getResDirectories());
    storePaths(sourceType, sourceProvider.getResourcesDirectories());
  }

  private void storePaths(@NotNull ExternalSystemSourceType sourceType, @Nullable Iterable<File> directories) {
    if (directories == null) {
      return;
    }
    for (File dir : directories) {
      storePath(sourceType, dir);
    }
  }

  private void excludeDirsExcept(@NotNull Collection<String> generatedDirPaths) {
    File moduleDir = new File(myContentRootData.getRootPath());
    for (File child : childrenOf(moduleDir)) {
      if (child.isDirectory()) {
        exclude(child, generatedDirPaths);
      }
    }
  }

  private void exclude(@NotNull File dir, @NotNull Collection<String> generatedDirPaths) {
    String name = dir.getName();
    if (name.startsWith(".")) {
      storePath(ExternalSystemSourceType.EXCLUDED, dir);
      return;
    }
    if (name.equals("build")) {
      Collection<String> dirsToExclude = getRelativePathsFirtDirName(dir, generatedDirPaths);
      for (File child : childrenOf(dir)) {
        if (child.isDirectory() && !dirsToExclude.contains(child.getName())) {
          storePath(ExternalSystemSourceType.EXCLUDED, child);
        }
      }
    }
  }

  @NotNull
  private static Collection<String> getRelativePathsFirtDirName(@NotNull File base, @NotNull Collection<String> paths) {
    String basePath = base.getAbsolutePath();
    Set<String> dirNames = Sets.newHashSet();
    for (String path : paths) {
      String relativePath = FileUtilRt.getRelativePath(basePath, path, File.separatorChar);
      if (relativePath != null && !relativePath.startsWith("..") && !relativePath.startsWith(File.separator)) {
        relativePath = PathUtil.getCanonicalPath(relativePath);
        List<String> segments = FileUtil.splitPath(relativePath);
        if (!segments.isEmpty()) {
          dirNames.add(segments.get(0));
        }
      }
    }
    return dirNames;
  }

  @NotNull
  private static File[] childrenOf(@NotNull File dir) {
    File[] children = dir.listFiles();
    return FileUtil.notNullize(children);
  }

  private void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File directory) {
    myContentRootData.storePath(sourceType, ExternalSystemUtil.toCanonicalPath(directory.getAbsolutePath()));
  }
}
