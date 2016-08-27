/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.android;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.ContentEntries;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.tools.idea.gradle.AndroidGradleModel.getTestArtifacts;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.android.tools.idea.gradle.util.GradleUtil.getGeneratedSourceFolders;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

class AndroidContentEntries extends ContentEntries {
  @NotNull private final AndroidGradleModel myAndroidModel;

  @NotNull
  static AndroidContentEntries findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel,
                                                          @NotNull AndroidGradleModel androidModel) {
    ContentEntry contentEntry = rootModel.addContentEntry(androidModel.getRootDir());
    List<ContentEntry> contentEntries = Collections.singletonList(contentEntry);

    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(rootModel.addContentEntry(pathToIdeaUrl(buildFolderPath)));
    }
    return new AndroidContentEntries(androidModel, rootModel, contentEntries);
  }

  private AndroidContentEntries(@NotNull AndroidGradleModel androidModel,
                                @NotNull ModifiableRootModel rootModel,
                                @NotNull Collection<ContentEntry> contentEntries) {
    super(rootModel, contentEntries);
    myAndroidModel = androidModel;
  }

  void setUpContentEntries(@NotNull SyncAction.ModuleModels gradleModels) {
    Variant selectedVariant = myAndroidModel.getSelectedVariant();

    // Native sources from AndroidGradleModel needs to be added only when NativeAndroidGradleModel is not present.
    boolean addNativeSources = gradleModels.findModel(NativeAndroidProject.class) == null;

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(mainArtifact, false, addNativeSources);

    for (BaseArtifact artifact : getTestArtifacts(selectedVariant)) {
      addSourceFolders(artifact, true, addNativeSources);
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = myAndroidModel.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(flavor, addNativeSources);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = myAndroidModel.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(buildTypeContainer.getSourceProvider(), false, addNativeSources);

      Collection<SourceProvider> testSourceProviders = myAndroidModel.getTestSourceProviders(buildTypeContainer.getExtraSourceProviders());
      for (SourceProvider testSourceProvider : testSourceProviders) {
        addSourceFolder(testSourceProvider, true, addNativeSources);
      }
    }

    ProductFlavorContainer defaultConfig = getAndroidProject().getDefaultConfig();
    addSourceFolder(defaultConfig, addNativeSources);

    addExcludedOutputFolders();
    addOrphans();
  }

  private void addSourceFolders(@NotNull BaseArtifact artifact, boolean isTest, boolean addNativeSources) {
    addGeneratedSourceFolders(artifact, isTest);

    SourceProvider variantSourceProvider = artifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(variantSourceProvider, isTest, addNativeSources);
    }

    SourceProvider multiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(multiFlavorSourceProvider, isTest, addNativeSources);
    }
  }

  private void addGeneratedSourceFolders(@NotNull BaseArtifact artifact, boolean isTest) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);

    GradleVersion modelVersion = myAndroidModel.getModelVersion();
    if (artifact instanceof AndroidArtifact || (modelVersion != null && modelVersion.compareIgnoringQualifiers("1.2") >= 0)) {
      // getGeneratedSourceFolders used to be in AndroidArtifact only.
      Collection<File> generatedSourceFolders = getGeneratedSourceFolders(artifact);

      //noinspection ConstantConditions - this returned null in 1.2
      if (generatedSourceFolders != null) {
        addSourceFolders(generatedSourceFolders, sourceType, true);
      }
    }

    if (artifact instanceof AndroidArtifact) {
      sourceType = getResourceSourceType(isTest);
      addSourceFolders(((AndroidArtifact)artifact).getGeneratedResourceFolders(), sourceType, true);
    }
  }

  private void addSourceFolder(@NotNull ProductFlavorContainer flavor, boolean addNativeSources) {
    addSourceFolder(flavor.getSourceProvider(), false, addNativeSources);

    Collection<SourceProvider> testSourceProviders = myAndroidModel.getTestSourceProviders(flavor.getExtraSourceProviders());
    for (SourceProvider sourceProvider : testSourceProviders) {
      addSourceFolder(sourceProvider, true, addNativeSources);
    }
  }

  private void addSourceFolder(@NotNull SourceProvider sourceProvider, boolean isTest, boolean addNativeSources) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);

    addSourceFolders(sourceProvider.getResDirectories(), sourceType, false);
    addSourceFolders(sourceProvider.getResourcesDirectories(), sourceType, false);
    addSourceFolders(sourceProvider.getAssetsDirectories(), sourceType, false);

    sourceType = getSourceType(isTest);
    addSourceFolders(sourceProvider.getAidlDirectories(), sourceType, false);
    addSourceFolders(sourceProvider.getJavaDirectories(), sourceType, false);

    if (addNativeSources) {
      addSourceFolders(sourceProvider.getCDirectories(), sourceType, false);
      addSourceFolders(sourceProvider.getCppDirectories(), sourceType, false);
    }

    addSourceFolders(sourceProvider.getRenderscriptDirectories(), sourceType, false);
    if (myAndroidModel.getFeatures().isShadersSupported()) {
      addSourceFolders(sourceProvider.getShadersDirectories(), sourceType, false);
    }
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? TEST_RESOURCE : RESOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? TEST_SOURCE : SOURCE;
  }

  private void addSourceFolders(@NotNull Collection<File> folderPaths, @NotNull JpsModuleSourceRootType type, boolean generated) {
    for (File folderPath : folderPaths) {
      if (generated && !isGeneratedAtCorrectLocation(folderPath)) {
        myAndroidModel.registerExtraGeneratedSourceFolder(folderPath);
      }
      addSourceFolder(folderPath, type, generated);
    }
  }

  private boolean isGeneratedAtCorrectLocation(@NotNull File folderPath) {
    File generatedFolderPath = new File(getAndroidProject().getBuildFolder(), FD_GENERATED);
    return isAncestor(generatedFolderPath, folderPath, false);
  }

  private void addExcludedOutputFolders() {
    File buildFolderPath = getAndroidProject().getBuildFolder();
    ContentEntry parentContentEntry = findParentContentEntry(buildFolderPath, getValues());
    if (parentContentEntry != null) {
      List<File> excludedFolderPaths = myAndroidModel.getExcludedFolderPaths();
      for (File folderPath : excludedFolderPaths) {
        addExcludedFolder(parentContentEntry, folderPath);
      }
    }
  }

  @NotNull
  private AndroidProject getAndroidProject() {
    return myAndroidModel.getAndroidProject();
  }
}
