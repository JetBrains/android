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
import com.android.tools.idea.gradle.project.sync.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.ContentEntries;
import com.android.tools.idea.gradle.project.sync.setup.RootSourceFolder;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
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

public class ContentRootModuleSetupStep extends AndroidModuleSetupStep {
  private final ContentEntries myContentEntries = new ContentEntries();

  @Override
  public void setUpModule(@NotNull Module module,
                          @NotNull AndroidGradleModel androidModel,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull SyncAction.ModuleModels gradleModels,
                          @NotNull ProgressIndicator indicator) {
    ModifiableRootModel rootModel = ideModelsProvider.getModifiableRootModel(module);
    myContentEntries.removeExisting(rootModel);

    Collection<ContentEntry> contentEntries = findOrCreateContentEntries(rootModel, androidModel);
    List<RootSourceFolder> orphans = new ArrayList<>();

    setUpContentEntries(contentEntries, androidModel, orphans, gradleModels);
  }

  @NotNull
  private static Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel rootModel,
                                                                     @NotNull AndroidGradleModel androidModel) {
    ContentEntry contentEntry = rootModel.addContentEntry(androidModel.getRootDir());
    List<ContentEntry> contentEntries = Collections.singletonList(contentEntry);

    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(rootModel.addContentEntry(pathToIdeaUrl(buildFolderPath)));
    }
    return contentEntries;
  }

  private void setUpContentEntries(@NotNull Collection<ContentEntry> contentEntries,
                                   @NotNull AndroidGradleModel androidModel,
                                   @NotNull List<RootSourceFolder> orphans,
                                   @NotNull SyncAction.ModuleModels gradleModels) {
    Variant selectedVariant = androidModel.getSelectedVariant();

    // Native sources from AndroidGradleModel needs to be added only when NativeAndroidGradleModel is not present.
    boolean addNativeSources = gradleModels.findModel(NativeAndroidProject.class) == null;

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(androidModel, contentEntries, mainArtifact, orphans, false, addNativeSources);

    for (BaseArtifact artifact : getTestArtifacts(selectedVariant)) {
      addSourceFolders(androidModel, contentEntries, artifact, orphans, true, addNativeSources);
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidModel.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(androidModel, contentEntries, flavor, orphans, addNativeSources);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidModel.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(androidModel, contentEntries, buildTypeContainer.getSourceProvider(), orphans, false, addNativeSources);

      Collection<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(buildTypeContainer.getExtraSourceProviders());
      for (SourceProvider testSourceProvider : testSourceProviders) {
        addSourceFolder(androidModel, contentEntries, testSourceProvider, orphans, true, addNativeSources);
      }
    }

    ProductFlavorContainer defaultConfig = androidModel.getAndroidProject().getDefaultConfig();
    addSourceFolder(androidModel, contentEntries, defaultConfig, orphans, addNativeSources);

    addExcludedOutputFolders(contentEntries, androidModel);
  }

  private void addSourceFolders(@NotNull AndroidGradleModel androidModel,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull BaseArtifact artifact,
                                @NotNull List<RootSourceFolder> orphans,
                                boolean isTest,
                                boolean addNativeSources) {
    addGeneratedSourceFolders(androidModel, contentEntries, artifact, orphans, isTest);

    SourceProvider variantSourceProvider = artifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, variantSourceProvider, orphans, isTest, addNativeSources);
    }

    SourceProvider multiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, multiFlavorSourceProvider, orphans, isTest, addNativeSources);
    }
  }

  private void addGeneratedSourceFolders(@NotNull AndroidGradleModel androidModel,
                                         @NotNull Collection<ContentEntry> contentEntries,
                                         @NotNull BaseArtifact artifact,
                                         @NotNull List<RootSourceFolder> orphans,
                                         boolean isTest) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);

    GradleVersion modelVersion = androidModel.getModelVersion();
    if (artifact instanceof AndroidArtifact || (modelVersion != null && modelVersion.compareIgnoringQualifiers("1.2") >= 0)) {
      // getGeneratedSourceFolders used to be in AndroidArtifact only.
      Collection<File> generatedSourceFolders = getGeneratedSourceFolders(artifact);

      //noinspection ConstantConditions - this returned null in 1.2
      if (generatedSourceFolders != null) {
        addSourceFolders(androidModel, contentEntries, generatedSourceFolders, sourceType, orphans, true);
      }
    }

    if (artifact instanceof AndroidArtifact) {
      sourceType = getResourceSourceType(isTest);
      addSourceFolders(androidModel, contentEntries, ((AndroidArtifact)artifact).getGeneratedResourceFolders(), sourceType, orphans, true
      );
    }
  }

  private void addSourceFolder(@NotNull AndroidGradleModel androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull ProductFlavorContainer flavor,
                               @NotNull List<RootSourceFolder> orphans,
                               boolean addNativeSources) {
    addSourceFolder(androidModel, contentEntries, flavor.getSourceProvider(), orphans, false, addNativeSources);

    Collection<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(flavor.getExtraSourceProviders());
    for (SourceProvider sourceProvider : testSourceProviders) {
      addSourceFolder(androidModel, contentEntries, sourceProvider, orphans, true, addNativeSources);
    }
  }

  private void addSourceFolder(@NotNull AndroidGradleModel androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull SourceProvider sourceProvider,
                               @NotNull List<RootSourceFolder> orphans, 
                               boolean isTest,
                               boolean addNativeSources) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);

    addSourceFolders(androidModel, contentEntries, sourceProvider.getResDirectories(), sourceType, orphans, false);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getResourcesDirectories(), sourceType, orphans, false);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAssetsDirectories(), sourceType, orphans, false);

    sourceType = getSourceType(isTest);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAidlDirectories(), sourceType, orphans, false);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getJavaDirectories(), sourceType, orphans, false);

    if (addNativeSources) {
      addSourceFolders(androidModel, contentEntries, sourceProvider.getCDirectories(), sourceType, orphans, false);
      addSourceFolders(androidModel, contentEntries, sourceProvider.getCppDirectories(), sourceType, orphans, false);
    }

    addSourceFolders(androidModel, contentEntries, sourceProvider.getRenderscriptDirectories(), sourceType, orphans, false);
    if (androidModel.getFeatures().isShadersSupported()) {
      addSourceFolders(androidModel, contentEntries, sourceProvider.getShadersDirectories(), sourceType, orphans, false);
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

  private void addSourceFolders(@NotNull AndroidGradleModel androidModel,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> folderPaths,
                                @NotNull JpsModuleSourceRootType type,
                                @NotNull List<RootSourceFolder> orphans,
                                boolean generated) {
    for (File folderPath : folderPaths) {
      if (generated && !isGeneratedAtCorrectLocation(folderPath, androidModel.getAndroidProject())) {
        androidModel.registerExtraGeneratedSourceFolder(folderPath);
      }
      myContentEntries.addSourceFolder(contentEntries, folderPath, type, orphans, generated);
    }
  }

  private static boolean isGeneratedAtCorrectLocation(@NotNull File folderPath, @NotNull AndroidProject project) {
    File generatedFolderPath = new File(project.getBuildFolder(), FD_GENERATED);
    return isAncestor(generatedFolderPath, folderPath, false);
  }

  private void addExcludedOutputFolders(@NotNull Collection<ContentEntry> contentEntries, @NotNull AndroidGradleModel androidModel) {
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    ContentEntry parentContentEntry = findParentContentEntry(buildFolderPath, contentEntries);
    if (parentContentEntry != null) {
      List<File> excludedFolderPaths = androidModel.getExcludedFolderPaths();
      for (File folderPath : excludedFolderPaths) {
        myContentEntries.addExcludedFolder(parentContentEntry, folderPath);
      }
    }
  }

  @Override
  @NotNull
  public String getDescription() {
    return "Source folder(s) setup";
  }
}
