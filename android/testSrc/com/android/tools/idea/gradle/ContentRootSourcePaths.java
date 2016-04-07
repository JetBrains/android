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
package com.android.tools.idea.gradle;

import com.android.builder.model.BuildTypeContainer;
import com.android.builder.model.SourceProvider;
import com.android.tools.idea.gradle.stubs.android.AndroidArtifactStub;
import com.android.tools.idea.gradle.stubs.android.AndroidProjectStub;
import com.android.tools.idea.gradle.stubs.android.ProductFlavorContainerStub;
import com.android.tools.idea.gradle.stubs.android.VariantStub;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.util.io.FileUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

/**
 * Verifies that the source paths of a {@link ContentRootData} are correct.
 */
public class ContentRootSourcePaths {
  @NotNull public static final ExternalSystemSourceType[] ALL_SOURCE_TYPES =
    {ExternalSystemSourceType.EXCLUDED, ExternalSystemSourceType.SOURCE_GENERATED, ExternalSystemSourceType.RESOURCE,
      ExternalSystemSourceType.SOURCE, ExternalSystemSourceType.TEST, ExternalSystemSourceType.TEST_RESOURCE,
      ExternalSystemSourceType.TEST_GENERATED};

  @NotNull private final Map<ExternalSystemSourceType, List<String>> myDirectoryPathsBySourceType = Maps.newHashMap();

  public ContentRootSourcePaths() {
    for (ExternalSystemSourceType sourceType : ALL_SOURCE_TYPES) {
      myDirectoryPathsBySourceType.put(sourceType, new ArrayList<>());
    }
  }

  /**
   * Stores the expected paths of all the source and test directories in the given {@code AndroidProject}.
   *
   * @param androidProject the given {@code AndroidProject}.
   */
  public void storeExpectedSourcePaths(@NotNull AndroidProjectStub androidProject) {
    VariantStub selectedVariant = androidProject.getFirstVariant();
    Assert.assertNotNull(selectedVariant);
    addGeneratedDirPaths(selectedVariant);

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainerStub flavor = androidProject.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceDirPaths(flavor);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildType = androidProject.findBuildType(buildTypeName);
    if (buildType != null) {
      addSourceDirPaths(buildType.getSourceProvider(), false);
    }

    addSourceDirPaths(androidProject.getDefaultConfig());
  }

  private void addGeneratedDirPaths(@NotNull VariantStub variant) {
    AndroidArtifactStub mainArtifact = variant.getMainArtifact();
    addGeneratedDirPaths(mainArtifact, false);

    AndroidArtifactStub testArtifact = variant.getInstrumentTestArtifact();
    addGeneratedDirPaths(testArtifact, true);
  }

  private void addGeneratedDirPaths(@NotNull AndroidArtifactStub androidArtifact, boolean isTest) {
    ExternalSystemSourceType sourceType = isTest ? ExternalSystemSourceType.TEST_GENERATED : ExternalSystemSourceType.SOURCE_GENERATED;
    addSourceDirPaths(sourceType, androidArtifact.getGeneratedSourceFolders());

    sourceType = isTest ? ExternalSystemSourceType.TEST_RESOURCE : ExternalSystemSourceType.RESOURCE;
    addSourceDirPaths(sourceType, androidArtifact.getGeneratedResourceFolders());
  }

  private void addSourceDirPaths(@NotNull ProductFlavorContainerStub productFlavor) {
    addSourceDirPaths(productFlavor.getSourceProvider(), false);
    addSourceDirPaths(productFlavor.getInstrumentationTestSourceProvider(), true);
  }

  private void addSourceDirPaths(@NotNull SourceProvider sourceProvider, boolean isTest) {
    ExternalSystemSourceType sourceType = isTest ? ExternalSystemSourceType.TEST : ExternalSystemSourceType.SOURCE;

    addSourceDirPaths(sourceType, sourceProvider.getAidlDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getAssetsDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getJavaDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getCppDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getCDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getRenderscriptDirectories());

    sourceType = isTest ? ExternalSystemSourceType.TEST_RESOURCE : ExternalSystemSourceType.RESOURCE;
    addSourceDirPaths(sourceType, sourceProvider.getResDirectories());
    addSourceDirPaths(sourceType, sourceProvider.getResourcesDirectories());
  }

  private void addSourceDirPaths(@NotNull ExternalSystemSourceType sourceType, @Nullable Iterable<File> sourceDirectories) {
    if (sourceDirectories == null) {
      return;
    }
    List<String> paths = getPaths(sourceType);
    for (File directory : sourceDirectories) {
      paths.add(FileUtil.toSystemIndependentName(directory.getPath()));
    }
    Collections.sort(paths);
  }

  public void assertCorrectStoredDirPaths(@NotNull Collection<String> paths, @NotNull ExternalSystemSourceType sourceType) {
    List<String> sortedPaths = Lists.newArrayList(paths);
    Collections.sort(sortedPaths);
    List<String> expectedPaths = getPaths(sourceType);
    String msg = String.format("Source paths (%s)", sourceType.toString().toLowerCase());
    Assert.assertEquals(msg, expectedPaths, sortedPaths);
  }

  @NotNull
  public List<String> getPaths(@NotNull ExternalSystemSourceType sourceType) {
    return myDirectoryPathsBySourceType.get(sourceType);
  }
}
