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
package com.android.tools.idea.gradle.customizer.android;

import com.android.builder.model.*;
import com.android.tools.idea.gradle.IdeaAndroidProject;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;

/**
 * Sets the content roots of an IDEA module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaAndroidProject> {
  // TODO: Retrieve this information from Gradle.
  private static final String[] EXCLUDED_OUTPUT_DIR_NAMES =
    // Note that build/exploded-bundles and build/exploded-aar should *not* be excluded
    {"apk", "assets", "bundles", "classes", "dependency-cache", "incremental", "libs", "manifests", "symbols", "tmp", "res"};

  @Override
  @NotNull
  protected ContentEntry findOrCreateContentEntry(@NotNull ModifiableRootModel model, @NotNull IdeaAndroidProject androidProject) {
    ContentEntry[] contentEntries = model.getContentEntries();
    VirtualFile rootDir = androidProject.getRootDir();
    if (contentEntries.length > 0) {
      for (ContentEntry contentEntry : contentEntries) {
        VirtualFile contentEntryFile = contentEntry.getFile();
        if (rootDir.equals(contentEntryFile)) {
          return contentEntry;
        }
      }
    }
    return model.addContentEntry(rootDir);
  }

  @Override
  protected void setUpContentEntry(@NotNull ContentEntry contentEntry, @NotNull IdeaAndroidProject androidProject) {
    Variant selectedVariant = androidProject.getSelectedVariant();

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(contentEntry, mainArtifact, false);

    AndroidArtifact testArtifact = androidProject.findInstrumentationTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      addSourceFolders(contentEntry, testArtifact, true);
    }

    AndroidProject delegate = androidProject.getDelegate();

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidProject.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(contentEntry, flavor);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidProject.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(contentEntry, buildTypeContainer.getSourceProvider(), false);
    }

    ProductFlavorContainer defaultConfig = delegate.getDefaultConfig();
    addSourceFolder(contentEntry, defaultConfig);

    addExcludedOutputFolders(contentEntry);
  }

  private void addSourceFolders(@NotNull ContentEntry contentEntry, @NotNull AndroidArtifact androidArtifact, boolean isTest) {
    addGeneratedSourceFolder(contentEntry, androidArtifact, isTest);

    SourceProvider variantSourceProvider = androidArtifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(contentEntry, variantSourceProvider, isTest);
    }

    SourceProvider multiFlavorSourceProvider = androidArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(contentEntry, multiFlavorSourceProvider, isTest);
    }
  }

  private void addGeneratedSourceFolder(@NotNull ContentEntry contentEntry, @NotNull AndroidArtifact androidArtifact, boolean isTest) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);
    addSourceFolders(contentEntry, sourceType, androidArtifact.getGeneratedSourceFolders(), true);

    sourceType = getResourceSourceType(isTest);
    addSourceFolders(contentEntry, sourceType, androidArtifact.getGeneratedResourceFolders(), true);
  }

  private void addSourceFolder(@NotNull ContentEntry contentEntry, @NotNull ProductFlavorContainer flavor) {
    addSourceFolder(contentEntry, flavor.getSourceProvider(), false);

    Collection<SourceProviderContainer> extraArtifactSourceProviders = flavor.getExtraSourceProviders();
    for (SourceProviderContainer sourceProviders : extraArtifactSourceProviders) {
      String artifactName = sourceProviders.getArtifactName();
      if (AndroidProject.ARTIFACT_INSTRUMENT_TEST.equals(artifactName)) {
        addSourceFolder(contentEntry, sourceProviders.getSourceProvider(), true);
        break;
      }
    }
  }

  private void addSourceFolder(@NotNull ContentEntry contentEntry, @NotNull SourceProvider sourceProvider, boolean isTest) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getAidlDirectories(), false);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getAssetsDirectories(), false);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getJavaDirectories(), false);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getJniDirectories(), false);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getRenderscriptDirectories(), false);

    sourceType = getResourceSourceType(isTest);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getResDirectories(), false);
    addSourceFolders(contentEntry, sourceType, sourceProvider.getResourcesDirectories(), false);
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
  }

  private void addSourceFolders(@NotNull ContentEntry contentEntry,
                                @NotNull JpsModuleSourceRootType sourceType,
                                @NotNull Collection<File> dirPaths,
                                boolean isGenerated) {
    for (File dirPath : dirPaths) {
      addSourceFolder(contentEntry, sourceType, dirPath, isGenerated);
    }
  }

  private void addExcludedOutputFolders(@NotNull ContentEntry contentEntry) {
    VirtualFile file = contentEntry.getFile();
    assert file != null;
    File rootDirPath = VfsUtilCore.virtualToIoFile(file);

    for (File child : FileUtil.notNullize(rootDirPath.listFiles())) {
      if (child.isDirectory() && child.getName().startsWith(".")) {
        addExcludedFolder(contentEntry, child);
      }
    }
    File outputDirPath = new File(rootDirPath, BUILD_DIR);
    for (String childName : EXCLUDED_OUTPUT_DIR_NAMES) {
      File child = new File(outputDirPath, childName);
      addExcludedFolder(contentEntry, child);
    }
  }
}
