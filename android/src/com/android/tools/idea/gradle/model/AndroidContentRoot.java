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
package com.android.tools.idea.gradle.model;

import com.android.build.gradle.model.AndroidProject;
import com.android.build.gradle.model.ProductFlavorContainer;
import com.android.build.gradle.model.Variant;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.google.common.collect.Sets;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Configures a module's content root from an {@link AndroidProject}.
 */
public final class AndroidContentRoot {
  private static final String OUTPUT_DIR_NAME = "build";

  private AndroidContentRoot() {
  }

  /**
   * Stores the paths of 'source'/'test'/'excluded' directories, according to the project structure in the given Android-Gradle project.
   *
   * @param androidProject    structure of the Android-Gradle project.
   * @param storage           persists the configuration of a content root.
   * @param excludeOutputDirs indicates whether output directories should be marked as 'excluded.'
   */
  public static void storePaths(@NotNull IdeaAndroidProject androidProject,
                                @NotNull ContentRootStorage storage,
                                boolean excludeOutputDirs) {
    Variant selectedVariant = androidProject.getSelectedVariant();
    storePaths(selectedVariant, storage);
    Collection<String> generatedDirPaths = storage.getIncludedDirPaths();

    AndroidProject delegate = androidProject.getDelegate();
    Map<String, ProductFlavorContainer> productFlavors = delegate.getProductFlavors();
    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = productFlavors.get(flavorName);
      if (flavor != null) {
        storePaths(flavor, storage);
      }
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    storePaths(defaultConfig, storage);

    if (excludeOutputDirs) {
      excludeDirsExcept(generatedDirPaths, storage);
    }
  }

  private static void storePaths(@NotNull Variant variant, @NotNull ContentRootStorage storage) {
    storePaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedSourceFolders(), storage);
    storePaths(ExternalSystemSourceType.SOURCE, variant.getGeneratedResourceFolders(), storage);
    storePaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestSourceFolders(), storage);
    storePaths(ExternalSystemSourceType.TEST, variant.getGeneratedTestResourceFolders(), storage);
  }

  private static void storePaths(@NotNull ProductFlavorContainer flavor, @NotNull ContentRootStorage storage) {
    storePaths(ExternalSystemSourceType.SOURCE, flavor.getSourceProvider(), storage);
    storePaths(ExternalSystemSourceType.TEST, flavor.getTestSourceProvider(), storage);
  }

  private static void storePaths(@NotNull ExternalSystemSourceType sourceType,
                                 @NotNull SourceProvider sourceProvider,
                                 @NotNull ContentRootStorage storage) {
    storePaths(sourceType, sourceProvider.getAidlDirectories(), storage);
    storePaths(sourceType, sourceProvider.getAssetsDirectories(), storage);
    storePaths(sourceType, sourceProvider.getJavaDirectories(), storage);
    storePaths(sourceType, sourceProvider.getJniDirectories(), storage);
    storePaths(sourceType, sourceProvider.getRenderscriptDirectories(), storage);
    storePaths(sourceType, sourceProvider.getResDirectories(), storage);
    storePaths(sourceType, sourceProvider.getResourcesDirectories(), storage);
  }

  private static void storePaths(@NotNull ExternalSystemSourceType sourceType,
                                 @Nullable Iterable<File> directories,
                                 @NotNull ContentRootStorage storage) {
    if (directories == null) {
      return;
    }
    for (File dir : directories) {
      storage.storePath(sourceType, dir);
    }
  }

  /**
   * Marks directories as 'excluded'. An 'excluded' directory may:
   * <ol>
   * <li>have a name starting with "."</li>
   * <li>be a child of the output directory 'build'. The directories whose paths are specified in {@code generatedDirPaths} are not
   * excluded</li>
   * </ol>
   *
   * @param generatedDirPaths the paths of the directories where generated source code is stored.
   * @param storage           persists the configuration of a content root.
   */
  private static void excludeDirsExcept(@NotNull Collection<String> generatedDirPaths, @NotNull ContentRootStorage storage) {
    for (File child : childrenOf(new File(storage.getRootDirPath()))) {
      if (child.isDirectory()) {
        exclude(child, generatedDirPaths, storage);
      }
    }
  }

  private static void exclude(@NotNull File dir, @NotNull Collection<String> generatedDirPaths, @NotNull ContentRootStorage storage) {
    String name = dir.getName();
    if (name.startsWith(".")) {
      storage.storePath(ExternalSystemSourceType.EXCLUDED, dir);
      return;
    }
    if (name.equals(OUTPUT_DIR_NAME)) {
      Collection<String> dirsToExclude = getChildDirNames(dir, generatedDirPaths);
      for (File child : childrenOf(dir)) {
        if (child.isDirectory() && !dirsToExclude.contains(child.getName())) {
          storage.storePath(ExternalSystemSourceType.EXCLUDED, child);
        }
      }
    }
  }

  @NotNull
  private static Collection<String> getChildDirNames(@NotNull File base, @NotNull Collection<String> paths) {
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

  /**
   * Persists the configuration of a content root.
   */
  public interface ContentRootStorage {
    /**
     * @return the root directory of the content root.
     */
    @NotNull
    String getRootDirPath();

    /**
     * @return the paths of the directories marked as 'source' and 'test.'
     */
    @NotNull
    Collection<String> getIncludedDirPaths();

    /**
     * Stores the path of the given directory as a directory of the given type.
     * @param sourceType the type of source directory (e.g. 'source', 'test,' etc.)
     * @param dir        the given directory.
     */
    void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File dir);
  }
}
