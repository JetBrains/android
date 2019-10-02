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
package com.android.tools.idea.gradle.project.sync.setup.module.android;

import static com.intellij.openapi.util.io.FileUtilRt.getRelativePath;
import static com.intellij.openapi.util.io.FileUtilRt.toSystemIndependentName;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;
import static com.intellij.util.containers.ContainerUtil.getFirstItem;

import com.android.builder.model.SourceProvider;
import com.android.ide.common.gradle.model.IdeAndroidArtifact;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.model.AndroidModel;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VfsUtilCore;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.facet.AndroidFacetType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.android.model.impl.JpsAndroidModuleProperties;

public class AndroidFacetModuleSetupStep extends AndroidModuleSetupStep {
  // It is safe to use "/" instead of File.separator. JpsAndroidModule uses it.
  private static final String SEPARATOR = "/";

  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull AndroidModuleModel androidModel) {
    Module module = context.getModule();
    IdeModifiableModelsProvider ideModelsProvider = context.getIdeModelsProvider();

    AndroidFacet facet = AndroidFacet.getInstance(module, ideModelsProvider);
    if (facet == null) {
      facet = createAndAddFacet(module, ideModelsProvider);
    }
    configureFacet(facet, androidModel);
  }

  @NotNull
  private static AndroidFacet createAndAddFacet(@NotNull Module module, @NotNull IdeModifiableModelsProvider ideModelsProvider) {
    ModifiableFacetModel model = ideModelsProvider.getModifiableFacetModel(module);
    AndroidFacetType facetType = AndroidFacet.getFacetType();
    AndroidFacet facet = facetType.createFacet(module, AndroidFacet.NAME, facetType.createDefaultConfiguration(), null);
    model.addFacet(facet);
    return facet;
  }

  private static void configureFacet(@NotNull AndroidFacet facet, @NotNull AndroidModuleModel androidModel) {
    JpsAndroidModuleProperties facetProperties = facet.getProperties();
    facetProperties.ALLOW_USER_CONFIGURATION = false;

    facetProperties.PROJECT_TYPE = androidModel.getAndroidProject().getProjectType();

    File modulePath = androidModel.getRootDirPath();
    SourceProvider sourceProvider = androidModel.getDefaultSourceProvider();
    facetProperties.MANIFEST_FILE_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getManifestFile());
    facetProperties.RES_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getResDirectories());
    facetProperties.ASSETS_FOLDER_RELATIVE_PATH = relativePath(modulePath, sourceProvider.getAssetsDirectories());

    facetProperties.RES_FOLDERS_RELATIVE_PATH =
      Stream.concat(
        androidModel
          .getActiveSourceProviders()
          .stream()
          .flatMap(provider -> provider.getResDirectories().stream()),
        androidModel.getMainArtifact().getGeneratedResourceFolders().stream()
      )
        .map(it -> VfsUtilCore.pathToUrl(it.getAbsolutePath()))
        .collect(Collectors.joining(JpsAndroidModuleProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION));

    IdeAndroidArtifact androidTestArtifact = androidModel.getArtifactForAndroidTest();
    facetProperties.TEST_RES_FOLDERS_RELATIVE_PATH =
      Stream.concat(
        androidModel
          .getTestSourceProviders()
          .stream()
          .flatMap(provider -> provider.getResDirectories().stream()),
        androidTestArtifact != null ? androidTestArtifact.getGeneratedResourceFolders().stream() : Stream.empty()
      )
        .map(it -> VfsUtilCore.pathToUrl(it.getAbsolutePath()))
        .collect(Collectors.joining(JpsAndroidModuleProperties.PATH_LIST_SEPARATOR_IN_FACET_CONFIGURATION));

    syncSelectedVariant(facetProperties, androidModel);
    AndroidModel.set(facet, androidModel);
    androidModel.syncSelectedVariantAndTestArtifact(facet);
  }

  private static void syncSelectedVariant(@NotNull JpsAndroidModuleProperties facetProperties,
                                          @NotNull AndroidModuleModel androidModel) {
    String variantStoredInFacet = facetProperties.SELECTED_BUILD_VARIANT;
    if (isNotEmpty(variantStoredInFacet) && androidModel.getVariantNames().contains(variantStoredInFacet)) {
      androidModel.setSelectedVariantName(variantStoredInFacet);
    }
  }

  // We are only getting the relative path of the first file in the collection, because JpsAndroidModuleProperties only accepts one path.
  @NotNull
  private static String relativePath(@NotNull File basePath, @NotNull Collection<File> dirs) {
    return relativePath(basePath, getFirstItem(dirs));
  }

  @NotNull
  private static String relativePath(@NotNull File basePath, @Nullable File file) {
    String relativePath = null;
    if (file != null) {
      relativePath = getRelativePath(basePath, file);
    }
    if (relativePath != null && !relativePath.startsWith(SEPARATOR)) {
      return SEPARATOR + toSystemIndependentName(relativePath);
    }
    return "";
  }
}
