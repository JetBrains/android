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

import com.android.builder.model.NativeAndroidProject;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.android.tools.idea.gradle.project.sync.setup.module.AndroidModuleSetupStep;
import com.android.tools.idea.gradle.project.sync.setup.Facets;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.android.tools.idea.gradle.project.sync.setup.module.common.ContentEntriesSetup.removeExistingContentEntries;
import static com.android.tools.idea.gradle.util.FilePaths.pathToIdeaUrl;
import static com.intellij.openapi.util.io.FileUtil.isAncestor;

public class ContentRootsModuleSetupStep extends AndroidModuleSetupStep {
  @NotNull private final AndroidContentEntriesSetup.Factory myContentEntriesSetupFactory;

  public ContentRootsModuleSetupStep() {
    this(new AndroidContentEntriesSetup.Factory());
  }

  @VisibleForTesting
  ContentRootsModuleSetupStep(@NotNull AndroidContentEntriesSetup.Factory contentEntriesSetupFactory) {
    myContentEntriesSetupFactory = contentEntriesSetupFactory;
  }

  @Override
  protected void doSetUpModule(@NotNull Module module,
                               @NotNull IdeModifiableModelsProvider ideModelsProvider,
                               @NotNull AndroidModuleModel androidModel,
                               @Nullable SyncAction.ModuleModels gradleModels,
                               @Nullable ProgressIndicator indicator) {
    ModifiableRootModel moduleModel = ideModelsProvider.getModifiableRootModel(module);
    boolean hasNativeModel = hasNativeModel(module, ideModelsProvider, gradleModels);
    AndroidContentEntriesSetup setup = myContentEntriesSetupFactory.create(androidModel, moduleModel, hasNativeModel);
    List<ContentEntry> contentEntries = findContentEntries(moduleModel, androidModel, hasNativeModel);
    setup.execute(contentEntries);
  }

  @NotNull
  private static List<ContentEntry> findContentEntries(@NotNull ModifiableRootModel moduleModel,
                                                       @NotNull AndroidModuleModel androidModel,
                                                       boolean hasNativeModel) {
    if (!hasNativeModel) {
      removeExistingContentEntries(moduleModel);
    }

    List<ContentEntry> contentEntries = new ArrayList<>();
    ContentEntry contentEntry = moduleModel.addContentEntry(androidModel.getRootDir());
    contentEntries.add(contentEntry);

    File buildFolderPath = androidModel.getAndroidProject().getBuildFolder();
    if (!isAncestor(androidModel.getRootDirPath(), buildFolderPath, false)) {
      contentEntries.add(moduleModel.addContentEntry(pathToIdeaUrl(buildFolderPath)));
    }
    return contentEntries;
  }

  private static boolean hasNativeModel(@NotNull Module module,
                                        @NotNull IdeModifiableModelsProvider ideModelsProvider,
                                        @Nullable SyncAction.ModuleModels gradleModels) {
    if (gradleModels != null) {
      return gradleModels.findModel(NativeAndroidProject.class) != null;
    }
    NdkFacet facet = Facets.findFacet(module, ideModelsProvider, NdkFacet.getFacetType().getId());
    return facet != null && facet.getNdkModuleModel() != null;
  }

  @Override
  public boolean invokeOnBuildVariantChange() {
    return true;
  }

  @Override
  public boolean invokeOnSkippedSync() {
    return true;
  }
}
