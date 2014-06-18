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
import com.android.tools.idea.gradle.util.FilePaths;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static com.android.builder.model.AndroidProject.FD_INTERMEDIATES;
import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

/**
 * Sets the content roots of an IDEA module imported from an {@link com.android.builder.model.AndroidProject}.
 */
public class ContentRootModuleCustomizer extends AbstractContentRootModuleCustomizer<IdeaAndroidProject> {
  public static final List<String> EXCLUDED_OUTPUT_FOLDER_NAMES = Arrays.asList(FD_INTERMEDIATES, FD_OUTPUTS);

  @Override
  @NotNull
  protected Collection<ContentEntry> findOrCreateContentEntries(@NotNull ModifiableRootModel model,
                                                                @NotNull IdeaAndroidProject androidProject) {
    VirtualFile rootDir = androidProject.getRootDir();
    File rootDirPath = VfsUtilCore.virtualToIoFile(rootDir);

    List<ContentEntry> contentEntries = Lists.newArrayList(model.addContentEntry(rootDir));
    File buildFolderPath = androidProject.getDelegate().getBuildFolder();
    if (!FileUtil.isAncestor(rootDirPath, buildFolderPath, false)) {
      contentEntries.add(model.addContentEntry(FilePaths.pathToIdeaUrl(buildFolderPath)));
    }

    return contentEntries;
  }

  @Override
  protected void setUpContentEntries(@NotNull Module module,
                                     @NotNull Collection<ContentEntry> contentEntries,
                                     @NotNull IdeaAndroidProject androidProject,
                                     @NotNull List<RootSourceFolder> orphans) {
    Variant selectedVariant = androidProject.getSelectedVariant();

    AndroidArtifact mainArtifact = selectedVariant.getMainArtifact();
    addSourceFolders(androidProject, contentEntries, mainArtifact, false, orphans);

    AndroidArtifact testArtifact = androidProject.findInstrumentationTestArtifactInSelectedVariant();
    if (testArtifact != null) {
      addSourceFolders(androidProject, contentEntries, testArtifact, true, orphans);
    }

    for (String flavorName : selectedVariant.getProductFlavors()) {
      ProductFlavorContainer flavor = androidProject.findProductFlavor(flavorName);
      if (flavor != null) {
        addSourceFolder(androidProject, contentEntries, flavor, orphans);
      }
    }

    String buildTypeName = selectedVariant.getBuildType();
    BuildTypeContainer buildTypeContainer = androidProject.findBuildType(buildTypeName);
    if (buildTypeContainer != null) {
      addSourceFolder(androidProject, contentEntries, buildTypeContainer.getSourceProvider(), false, orphans);
    }

    ProductFlavorContainer defaultConfig = androidProject.getDelegate().getDefaultConfig();
    addSourceFolder(androidProject, contentEntries, defaultConfig, orphans);

    addExcludedOutputFolders(contentEntries, androidProject);
  }

  private void addSourceFolders(@NotNull IdeaAndroidProject androidProject,
                                @NotNull Collection<ContentEntry> contentEntry,
                                @NotNull AndroidArtifact androidArtifact,
                                boolean isTest,
                                @NotNull List<RootSourceFolder> orphans) {
    addGeneratedSourceFolder(androidProject, contentEntry, androidArtifact, isTest, orphans);

    SourceProvider variantSourceProvider = androidArtifact.getVariantSourceProvider();
    if (variantSourceProvider != null) {
      addSourceFolder(androidProject, contentEntry, variantSourceProvider, isTest, orphans);
    }

    SourceProvider multiFlavorSourceProvider = androidArtifact.getMultiFlavorSourceProvider();
    if (multiFlavorSourceProvider != null) {
      addSourceFolder(androidProject, contentEntry, multiFlavorSourceProvider, isTest, orphans);
    }
  }

  private void addGeneratedSourceFolder(@NotNull IdeaAndroidProject androidProject,
                                        @NotNull Collection<ContentEntry> contentEntries,
                                        @NotNull AndroidArtifact androidArtifact,
                                        boolean isTest,
                                        @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getSourceType(isTest);
    addSourceFolders(androidProject, contentEntries, androidArtifact.getGeneratedSourceFolders(), sourceType, true, orphans);

    sourceType = getResourceSourceType(isTest);
    addSourceFolders(androidProject, contentEntries, androidArtifact.getGeneratedResourceFolders(), sourceType, true, orphans);
  }

  private void addSourceFolder(@NotNull IdeaAndroidProject androidProject,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull ProductFlavorContainer flavor,
                               @NotNull List<RootSourceFolder> orphans) {
    addSourceFolder(androidProject, contentEntries, flavor.getSourceProvider(), false, orphans);

    Collection<SourceProviderContainer> extraArtifactSourceProviders = flavor.getExtraSourceProviders();
    for (SourceProviderContainer sourceProviders : extraArtifactSourceProviders) {
      String artifactName = sourceProviders.getArtifactName();
      if (AndroidProject.ARTIFACT_ANDROID_TEST.equals(artifactName)) {
        addSourceFolder(androidProject, contentEntries, sourceProviders.getSourceProvider(), true, orphans);
        break;
      }
    }
  }

  private void addSourceFolder(@NotNull IdeaAndroidProject androidProject,
                               @NotNull Collection<ContentEntry> contentEntries,
                               @NotNull SourceProvider sourceProvider,
                               boolean isTest,
                               @NotNull List<RootSourceFolder> orphans) {
    JpsModuleSourceRootType sourceType = getResourceSourceType(isTest);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getResDirectories(), sourceType, false, orphans);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getResourcesDirectories(), sourceType, false, orphans);

    sourceType = getSourceType(isTest);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getAidlDirectories(), sourceType, false, orphans);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getAssetsDirectories(), sourceType, false, orphans);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getJavaDirectories(), sourceType, false, orphans);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getJniDirectories(), sourceType, false, orphans);
    addSourceFolders(androidProject, contentEntries, sourceProvider.getRenderscriptDirectories(), sourceType, false, orphans);
  }

  @NotNull
  private static JpsModuleSourceRootType getResourceSourceType(boolean isTest) {
    return isTest ? JavaResourceRootType.TEST_RESOURCE : JavaResourceRootType.RESOURCE;
  }

  @NotNull
  private static JpsModuleSourceRootType getSourceType(boolean isTest) {
    return isTest ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE;
  }

  private void addSourceFolders(@NotNull IdeaAndroidProject androidProject,
                                @NotNull Collection<ContentEntry> contentEntries,
                                @NotNull Collection<File> folderPaths,
                                @NotNull JpsModuleSourceRootType type,
                                boolean generated,
                                @NotNull List<RootSourceFolder> orphans) {
    for (File folderPath : folderPaths) {
      if (generated && !isGeneratedAtCorrectLocation(folderPath, androidProject.getDelegate())) {
        androidProject.registerExtraGeneratedSourceFolder(folderPath);
      }
      addSourceFolder(contentEntries, folderPath, type, generated, orphans);
    }
  }

  private static boolean isGeneratedAtCorrectLocation(@NotNull File folderPath, @NotNull AndroidProject project) {
    File generatedFolderPath = new File(project.getBuildFolder(), AndroidProject.FD_GENERATED);
    return FileUtil.isAncestor(generatedFolderPath, folderPath, false);
  }

  private void addExcludedOutputFolders(@NotNull Collection<ContentEntry> contentEntries, @NotNull IdeaAndroidProject androidProject) {
    File buildFolderPath = androidProject.getDelegate().getBuildFolder();
    ContentEntry parentContentEntry = findParentContentEntry(contentEntries, buildFolderPath);
    assert parentContentEntry != null;

    // Explicitly exclude the output folders created by the Android Gradle plug-in
    for (String folderName : EXCLUDED_OUTPUT_FOLDER_NAMES) {
      File excludedFolderPath = new File(buildFolderPath, folderName);
      addExcludedFolder(parentContentEntry, excludedFolderPath);
    }

    // Iterate through the build folder's children, excluding any folders that are not "generated" and haven't been already excluded.
    File[] children = FileUtil.notNullize(buildFolderPath.listFiles());
    for (File child : children) {
      if (androidProject.shouldManuallyExclude(child)) {
        addExcludedFolder(parentContentEntry, child);
      }
    }
  }
}
