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

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;

/**
 * Configures a module's content root from an {@link AndroidProject}.
 */
public final class AndroidContentRoot {
  @NonNls public static final String BUILD_DIR = "build";

  // TODO: Retrieve this information from Gradle.
  private static final String[] EXCLUDED_OUTPUT_DIR_NAMES =
    // Note that build/exploded-bundles should *not* be excluded
    {"apk", "assets", "bundles", "classes", "dependency-cache", "incremental", "libs", "manifests", "symbols", "tmp", "res"};

  private AndroidContentRoot() {
  }

  /**
   * Stores the paths of 'source'/'test'/'excluded' directories, according to the project structure in the given Android-Gradle project.
   *
   * @param androidProject structure of the Android-Gradle project.
   * @param storage        persists the configuration of a content root.
   */
  public static void storePaths(@NotNull IdeaAndroidProject androidProject, @NotNull ContentRootStorage storage) {
    Variant selectedVariant = androidProject.getSelectedVariant();

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    storeSourcePaths(mainArtifact, storage, false);

    AndroidArtifact testArtifact = androidProject.findInstrumentationTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      storeSourcePaths(testArtifact, storage, true);
    }

    AndroidProject delegate = androidProject.getDelegate();

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidProject.findProductFlavor(flavorName);
      if (flavor != null) {
        storeSourcePaths(flavor, storage);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidProject.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      storeSourcePaths(buildTypeContainer.getSourceProvider(), storage, false);
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    storeSourcePaths(defaultConfig, storage);

    excludeOutputDirs(storage);
  }

  private static void storeSourcePaths(@NotNull AndroidArtifact androidArtifact,
                                       @NotNull ContentRootStorage storage,
                                       boolean isTest) {
    storeGeneratedDirPaths(androidArtifact, storage, isTest);

    SourceProvider variantSourceProvider = androidArtifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      storeSourcePaths(variantSourceProvider, storage, isTest);
    }

    SourceProvider multiFlavorSourceProvider = androidArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      storeSourcePaths(multiFlavorSourceProvider, storage, isTest);
    }
  }

  private static void storeGeneratedDirPaths(@NotNull AndroidArtifact androidArtifact,
                                             @NotNull ContentRootStorage storage,
                                             boolean isTest) {
    ExternalSystemSourceType sourceType = isTest ? ExternalSystemSourceType.TEST_GENERATED : ExternalSystemSourceType.SOURCE_GENERATED;
    storePaths(sourceType, androidArtifact.getGeneratedSourceFolders(), storage);

    sourceType = getResourceSourceType(isTest);
    storePaths(sourceType, androidArtifact.getGeneratedResourceFolders(), storage);
  }

  private static void storeSourcePaths(@NotNull ProductFlavorContainer flavor, @NotNull ContentRootStorage storage) {
    storeSourcePaths(flavor.getSourceProvider(), storage, false);

    Collection<SourceProviderContainer> extraArtifactSourceProviders = flavor.getExtraSourceProviders();
    for (SourceProviderContainer sourceProviders : extraArtifactSourceProviders) {
      String artifactName = sourceProviders.getArtifactName();
      if (AndroidProject.ARTIFACT_INSTRUMENT_TEST.equals(artifactName)) {
        storeSourcePaths(sourceProviders.getSourceProvider(), storage, true);
        break;
      }
    }
  }

  private static void storeSourcePaths(@NotNull SourceProvider sourceProvider, @NotNull ContentRootStorage storage, boolean isTest) {
    ExternalSystemSourceType sourceType = isTest ? ExternalSystemSourceType.TEST : ExternalSystemSourceType.SOURCE;
    storePaths(sourceType, sourceProvider.getAidlDirectories(), storage);
    storePaths(sourceType, sourceProvider.getAssetsDirectories(), storage);
    storePaths(sourceType, sourceProvider.getJavaDirectories(), storage);
    storePaths(sourceType, sourceProvider.getJniDirectories(), storage);
    storePaths(sourceType, sourceProvider.getRenderscriptDirectories(), storage);

    sourceType = getResourceSourceType(isTest);
    storePaths(sourceType, sourceProvider.getResDirectories(), storage);
    storePaths(sourceType, sourceProvider.getResourcesDirectories(), storage);
  }

  private static ExternalSystemSourceType getResourceSourceType(boolean isTest) {
    return isTest ? ExternalSystemSourceType.TEST_RESOURCE : ExternalSystemSourceType.RESOURCE;
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

  private static void excludeOutputDirs(@NotNull ContentRootStorage storage) {
    File rootDir = new File(storage.getRootDirPath());
    for (File child : childrenOf(rootDir)) {
      if (child.isDirectory()) {
        exclude(child, storage);
      }
    }
    File outputDir = new File(rootDir, BUILD_DIR);
    for (String childName : EXCLUDED_OUTPUT_DIR_NAMES) {
      File child = new File(outputDir, childName);
      storage.storePath(ExternalSystemSourceType.EXCLUDED, child);
    }
  }

  private static void exclude(@NotNull File dir, @NotNull ContentRootStorage storage) {
    String name = dir.getName();
    if (name.startsWith(".")) {
      storage.storePath(ExternalSystemSourceType.EXCLUDED, dir);
    }
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
     * Stores the path of the given directory as a directory of the given type.
     *
     * @param sourceType the type of source directory (e.g. 'source', 'test,' etc.)
     * @param dir        the given directory.
     */
    void storePath(@NotNull ExternalSystemSourceType sourceType, @NotNull File dir);
  }
}
