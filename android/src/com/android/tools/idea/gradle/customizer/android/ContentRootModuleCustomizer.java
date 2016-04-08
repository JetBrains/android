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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.NativeAndroidGradleModel;
import com.android.tools.idea.gradle.customizer.AbstractContentRootModuleCustomizer;
import com.android.tools.idea.gradle.facet.NativeAndroidGradleFacet;
import com.android.tools.idea.gradle.project.GradleExperimentalSettings;
import com.android.tools.idea.gradle.util.Facets;
import com.android.tools.idea.gradle.util.FilePaths;
import com.android.tools.idea.gradle.variant.view.BuildVariantModuleCustomizer;
import com.google.common.collect.Lists;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.FD_GENERATED;
import static com.android.tools.idea.gradle.util.FilePaths.findParentContentEntry;
import static com.android.tools.idea.gradle.util.GradleUtil.GRADLE_SYSTEM_ID;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;
import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE;
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE;
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE;

/**
 * Sets the content roots of an IDEA module imported from an {@link AndroidProject}.
 */
public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<AndroidGradleModel>
  implements BuildVariantModuleCustomizer<AndroidGradleModel> {

  @Override
  protected boolean shouldRemoveContentEntries(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    // The old content entries are already removed by the cpp.ContentRootModuleCustomizer if this module also contain
    // NativeAndroidGradleModel, otherwise they need to be clear now.
    NativeAndroidGradleFacet nativeAndroidGradleFacet = Facets.findFacet(module, modelsProvider, NativeAndroidGradleFacet.TYPE_ID);
    return nativeAndroidGradleFacet == null || nativeAndroidGradleFacet.getNativeAndroidGradleModel() == null;
  }

  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                                @NotNull AndroidGradleModel androidModel) {
    List<ContentEntry> contentEntries = Lists.newArrayList(moduleModel.addContentEntry(androidModel.getRootDir()));
    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(moduleModel.addContentEntry(FilePaths.pathToIdeaUrl(buildFolderPath)));
    }
    return contentEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull ModifiableRootModel moduleModel,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull AndroidGradleModel androidModel,
                                     @NotNull List<RootSourceFolder> orphans) {
    Variant selectedVariant = androidModel.getSelectedVariant();

    // Native sources from AndroidGradleModel needs to be added only when NativeAndroidGradleModel is not present.
    boolean shouldAddNativeSources = NativeAndroidGradleModel.get(moduleModel.getModule()) == null;

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(androidModel, contentEntries, mainArtifact, false, shouldAddNativeSources, orphans);

    if (GradleExperimentalSettings.getInstance().LOAD_ALL_TEST_ARTIFACTS) {
      for (BaseArtifact artifact : androidModel.getTestArtifactsInSelectedVariant()) {
        addSourceFolders(androidModel, contentEntries, artifact, true, shouldAddNativeSources, orphans);
      }
    } else {
      BaseArtifact testArtifact = androidModel.findSelectedTestArtifact(androidModel.getSelectedVariant());
      if (testArtifact != null) {
        addSourceFolders(androidModel, contentEntries, testArtifact, true, shouldAddNativeSources, orphans);
      }
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidModel.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(androidModel, contentEntries, flavor, shouldAddNativeSources, orphans);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidModel.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(androidModel, contentEntries, buildTypeContainer.getSourceProvider(), false, shouldAddNativeSources, orphans);

      Collection<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(buildTypeContainer.getExtraSourceProviders());
      for (SourceProvider testSourceProvider : testSourceProviders) {
        addSourceFolder(androidModel, contentEntries, testSourceProvider, true, shouldAddNativeSources, orphans);
      }
    }

    ProductFlavorContainer defaultConfig = androidModel.getAndroidProject().getDefaultConfig();
    addSourceFolder(androidModel, contentEntries, defaultConfig, shouldAddNativeSources, orphans);

    addExcludedOutputFolders(contentEntries, androidModel);
  }

  private void addSourceFolders(@NotNull AndroidGradleModel androidModel,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull BaseArtifact artifact,
                                boolean isTest,
                                boolean shouldAddNativeSources,
                                @NotNull List<RootSourceFolder> orphans) {
    addGeneratedSourceFolders(androidModel, contentEntries, artifact, isTest, orphans);

    SourceProvider variantSourceProvider = artifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, variantSourceProvider, isTest, shouldAddNativeSources, orphans);
    }

    SourceProvider multiFlavorSourceProvider = artifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(androidModel, contentEntries, multiFlavorSourceProvider, isTest, shouldAddNativeSources, orphans);
    }
  }

  private void addGeneratedSourceFolders(@NotNull AndroidGradleModel androidModel,
                                         @NotNull Collection<ContentEntry> contentEntries,
                                         @NotNull BaseArtifact artifact,
                                         boolean isTest,
                                         @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);

    if (artifact instanceof AndroidArtifact || androidModel.modelVersionIsAtLeast("1.2")) {
      // getGeneratedSourceFolders used to be in AndroidArtifact only.
      Collection<File> generatedSourceFolders = artifact.getGeneratedSourceFolders();

      //noinspection ConstantConditions - this returned null in 1.2
      if (generatedSourceFolders != null) {
        addSourceFolders(androidModel, contentEntries, generatedSourceFolders, sourceType, true, orphans);
      }
    }

    if (artifact instanceof AndroidArtifact) {
      sourceType = getResourceSourceType(isTest);
      addSourceFolders(androidModel, contentEntries, ((AndroidArtifact)artifact).getGeneratedResourceFolders(), sourceType, true,
                       orphans);
    }
  }

  private void addSourceFolder(@NotNull AndroidGradleModel androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull ProductFlavorContainer flavor,
                               boolean shouldAddNativeSources,
                               @NotNull List<RootSourceFolder> orphans) {
    addSourceFolder(androidModel, contentEntries, flavor.getSourceProvider(), false, shouldAddNativeSources, orphans);

    Collection<SourceProvider> testSourceProviders = androidModel.getTestSourceProviders(flavor.getExtraSourceProviders());
    for (SourceProvider sourceProvider : testSourceProviders) {
      addSourceFolder(androidModel, contentEntries, sourceProvider, true, shouldAddNativeSources, orphans);
    }
  }

  private void addSourceFolder(@NotNull AndroidGradleModel androidModel,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull SourceProvider sourceProvider,
                               boolean isTest,
                               boolean shouldAddNativeSources,
                               @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getResDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getResourcesDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAssetsDirectories(), sourceType, false, orphans);

    sourceType = getSourceType(isTest);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getAidlDirectories(), sourceType, false, orphans);
    addSourceFolders(androidModel, contentEntries, sourceProvider.getJavaDirectories(), sourceType, false, orphans);
    if (shouldAddNativeSources) {
      addSourceFolders(androidModel, contentEntries, sourceProvider.getCDirectories(), sourceType, false, orphans);
      addSourceFolders(androidModel, contentEntries, sourceProvider.getCppDirectories(), sourceType, false, orphans);
    }
    addSourceFolders(androidModel, contentEntries, sourceProvider.getRenderscriptDirectories(), sourceType, false, orphans);
    if (androidModel.supportsShaders()) {
      addSourceFolders(androidModel, contentEntries, sourceProvider.getShadersDirectories(), sourceType, false, orphans);
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
                                boolean generated,
                                @NotNull List<RootSourceFolder> orphans) {
    for (File folderPath : folderPaths) {
      if (generated && !isGeneratedAtCorrectLocation(folderPath, androidModel.getAndroidProject())) {
        androidModel.registerExtraGeneratedSourceFolder(folderPath);
      }
      addSourceFolder(contentEntries, folderPath, type, generated, orphans);
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
        addExcludedFolder(parentContentEntry, folderPath);
      }
    }
  }

  @Override
  @NotNull
  public ProjectSystemId getProjectSystemId() {
    return GRADLE_SYSTEM_ID;
  }

  @Override
  @NotNull
  public Class<AndroidGradleModel> getSupportedModelType() {
    return AndroidGradleModel.class;
  }
}
