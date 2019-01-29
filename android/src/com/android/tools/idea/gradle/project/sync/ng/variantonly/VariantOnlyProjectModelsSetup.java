/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.variantonly;

import com.android.builder.model.Variant;
import com.android.ide.common.gradle.model.IdeNativeVariantAbi;
import com.android.ide.common.gradle.model.level2.IdeDependenciesFactory;
import com.android.tools.idea.gradle.project.ProjectStructure;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.ng.ModuleSetup;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedModuleModels;
import com.android.tools.idea.gradle.project.sync.ng.caching.CachedProjectModels;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels.VariantOnlyModuleModel;
import com.android.tools.idea.gradle.project.sync.ng.variantonly.VariantOnlyProjectModels.VariantOnlyModuleModel.NativeVariantAbiModel;
import com.android.tools.idea.gradle.project.sync.setup.module.ModuleFinder;
import com.android.tools.idea.gradle.project.sync.setup.module.android.AndroidVariantChangeModuleSetup;
import com.android.tools.idea.gradle.project.sync.setup.module.ndk.NdkVariantChangeModuleSetup;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class VariantOnlyProjectModelsSetup extends ModuleSetup<VariantOnlyProjectModels> {
  @NotNull private final IdeDependenciesFactory myDependenciesFactory;
  @NotNull private final CachedProjectModels.Loader myModelsCacheLoader;
  @NotNull private final AndroidVariantChangeModuleSetup myAndroidModuleSetup;
  @NotNull private final NdkVariantChangeModuleSetup myNdkModuleSetup;


  public VariantOnlyProjectModelsSetup(@NotNull Project project,
                                       @NotNull IdeModifiableModelsProvider modelsProvider,
                                       @NotNull ModuleSetupContext.Factory moduleSetupFactory,
                                       @NotNull IdeDependenciesFactory dependenciesFactory,
                                       @NotNull CachedProjectModels.Loader projectModelsCacheLoader,
                                       @NotNull AndroidVariantChangeModuleSetup moduleSetup,
                                       @NotNull NdkVariantChangeModuleSetup ndkModuleSetup) {
    super(project, modelsProvider, moduleSetupFactory);
    myModelsCacheLoader = projectModelsCacheLoader;
    myAndroidModuleSetup = moduleSetup;
    myDependenciesFactory = dependenciesFactory;
    myNdkModuleSetup = ndkModuleSetup;
  }

  @Override
  public void setUpModules(@NotNull VariantOnlyProjectModels projectModels, @NotNull ProgressIndicator indicator) {
    notifyModuleConfigurationStarted(indicator);
    CachedProjectModels cache = myModelsCacheLoader.loadFromDisk(myProject);
    assert cache != null;
    myDependenciesFactory.setUpGlobalLibraryMap(projectModels.getGlobalLibraryMap());
    // In the case of Variant-Only Sync, build files are not changed since last sync, so the ModuleFinder is still valid.
    ModuleFinder moduleFinder = ProjectStructure.getInstance(myProject).getModuleFinder();
    for (VariantOnlyModuleModel moduleModel : projectModels.getModuleModels()) {
      Module module = moduleFinder.findModuleByModuleId(moduleModel.getModuleId());
      if (module != null) {
        setUpNdkModule(module, moduleModel, cache);
        setUpAndroidModule(module, moduleModel, cache);
      }
    }
    // Update cache on disk.
    cache.saveToDisk(myProject);
  }

  private void setUpAndroidModule(@NotNull Module module, @NotNull VariantOnlyModuleModel moduleModel, @NotNull CachedProjectModels cache) {
    AndroidModuleModel androidModel = AndroidModuleModel.get(module);
    AndroidFacet androidFacet = AndroidFacet.getInstance(module);
    if (androidModel != null && androidFacet != null) {
      ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider);
      // Inject variant-only model to AndroidModuleModel.
      androidModel.addVariantOnlyModuleModel(moduleModel, myDependenciesFactory);
      List<Variant> variants = moduleModel.getVariants();
      if (!variants.isEmpty()) {
        // Set selected variant in AndroidModuleModel.
        String variantToSelect = variants.get(0).getName();
        androidModel.setSelectedVariantName(variantToSelect);
        androidModel.syncSelectedVariantAndTestArtifact(androidFacet);
        myAndroidModuleSetup.setUpModule(context, androidModel);
      }
      // Replace the AndroidModuleModel in cache.
      CachedModuleModels cachedModels = cache.findCacheForModule(module.getName());
      if (cachedModels != null) {
        cachedModels.addModel(androidModel);
      }
    }
  }

  private void setUpNdkModule(@NotNull Module module, @NotNull VariantOnlyModuleModel moduleModel, @NotNull CachedProjectModels cache) {
    NdkModuleModel ndkModuleModel = NdkModuleModel.get(module);
    NdkFacet ndkFacet = NdkFacet.getInstance(module);
    NativeVariantAbiModel variantAbi = moduleModel.getNativeVariantAbi();
    if (ndkModuleModel != null && ndkFacet != null && variantAbi != null) {
      ModuleSetupContext context = myModuleSetupFactory.create(module, myModelsProvider);
      // Inject NativeVariantAbi to NdkModuleModel.
      IdeNativeVariantAbi ideVariantAbi = new IdeNativeVariantAbi(variantAbi.model);
      ndkModuleModel.addVariantOnlyModuleModel(ideVariantAbi);
      ndkModuleModel.setSelectedVariantName(variantAbi.name);

      myNdkModuleSetup.setUpModule(context, ndkModuleModel);

      // Replace the NdkModuleModel in cache.
      CachedModuleModels cachedModels = cache.findCacheForModule(module.getName());
      if (cachedModels != null) {
        cachedModels.addModel(ndkModuleModel);
      }
    }
  }
}
