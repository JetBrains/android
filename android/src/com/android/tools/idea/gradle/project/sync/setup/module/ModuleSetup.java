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
package com.android.tools.idea.gradle.project.sync.setup.module;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.NativeAndroidProject;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.project.facet.ndk.NdkFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.project.model.NdkModuleModel;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ModuleExtendedModel;

import java.io.File;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.gradle.util.Facets.removeAllFacetsOfType;
import static com.intellij.openapi.util.io.FileUtil.toSystemDependentName;

public class ModuleSetup {
  @NotNull private final IdeModifiableModelsProvider myIdeModelsProvider;
  @NotNull private final VariantSelector myVariantSelector;
  @NotNull private final GradleModuleSetup myGradleModuleSetup;
  @NotNull private final AndroidModuleSetup myAndroidModuleSetup;
  @NotNull private final NdkModuleSetup myNdkModuleSetup;
  @NotNull private final JavaModuleSetup myJavaModuleSetup;

  public ModuleSetup(@NotNull IdeModifiableModelsProvider ideModelsProvider) {
    this(ideModelsProvider, new VariantSelector(), new GradleModuleSetup(), new AndroidModuleSetup(), new NdkModuleSetup(), new JavaModuleSetup());
  }

  @VisibleForTesting
  ModuleSetup(@NotNull IdeModifiableModelsProvider ideModelsProvider,
              @NotNull VariantSelector variantSelector,
              @NotNull GradleModuleSetup gradleModuleSetup,
              @NotNull AndroidModuleSetup androidModuleSetup,
              @NotNull NdkModuleSetup ndkModuleSetup,
              @NotNull JavaModuleSetup javaModuleSetup) {
    myIdeModelsProvider = ideModelsProvider;
    myVariantSelector = variantSelector;
    myGradleModuleSetup = gradleModuleSetup;
    myAndroidModuleSetup = androidModuleSetup;
    myNdkModuleSetup = ndkModuleSetup;
    myJavaModuleSetup = javaModuleSetup;
  }

  public void setUpModule(@NotNull Module module, @NotNull SyncAction.ModuleModels models, @NotNull ProgressIndicator indicator) {
    boolean isProjectRootFolder = false;

    File gradleSettingsFile = new File(getModulePath(module), FN_SETTINGS_GRADLE);
    if (gradleSettingsFile.isFile() && !models.hasModel(AndroidProject.class) && !models.hasModel(NativeAndroidProject.class)) {
      // This is just a root folder for a group of Gradle projects. We don't set an IdeaGradleProject so the JPS builder won't try to
      // compile it using Gradle. We still need to create the module to display files inside it.
      isProjectRootFolder = true;
    }

    myGradleModuleSetup.setUpModule(module, myIdeModelsProvider, models);

    AndroidProject androidProject = models.findModel(AndroidProject.class);
    if (androidProject != null) {
      AndroidModuleModel androidModel = createAndroidModel(module, androidProject);
      if (androidModel != null) {
        // This is an Android module without variants.
        myAndroidModuleSetup.setUpModule(module, myIdeModelsProvider, androidModel, models, indicator);
      }
      else {
        // This is an Android module without variants. Treat as a non-buildable Java module.
        setUpJavaModule(module, models, indicator, true /* Android project without variants */);
      }
      return;
    }
    // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
    removeAllFacetsOfType(AndroidFacet.ID, myIdeModelsProvider.getModifiableFacetModel(module));

    NativeAndroidProject nativeAndroidProject = models.findModel(NativeAndroidProject.class);
    if (nativeAndroidProject != null) {
      NdkModuleModel ndkModuleModel = new NdkModuleModel(module.getName(), getModulePath(module), nativeAndroidProject);
      myNdkModuleSetup.setUpModule(module, myIdeModelsProvider, ndkModuleModel, models, indicator);
      return;
    }
    // This is not an Android module. Remove any AndroidFacet set in a previous sync operation.
    removeAllFacetsOfType(NdkFacet.getFacetTypeId(), myIdeModelsProvider.getModifiableFacetModel(module));

    if (!isProjectRootFolder) {
      setUpJavaModule(module, models, indicator, false /* Regular Java module */);
    }
  }

  @Nullable
  private AndroidModuleModel createAndroidModel(@NotNull Module module, @NotNull AndroidProject androidProject) {
    Variant variantToSelect = myVariantSelector.findVariantToSelect(androidProject);
    if (variantToSelect != null) {
      return new AndroidModuleModel(module.getName(), getModulePath(module), androidProject, variantToSelect.getName());
    }
    // If an Android project does not have variants, it would be impossible to build. This is a possible but invalid use case.
    // For now we are going to treat this case as a Java library module, because everywhere in the IDE (e.g. run configurations,
    // editors, test support, variants tool window, project building, etc.) we have the assumption that there is at least one variant
    // per Android project, and changing that in the code base is too risky, for very little benefit.
    // See https://code.google.com/p/android/issues/detail?id=170722
    return null;
  }

  @NotNull
  private static File getModulePath(@NotNull Module module) {
    File moduleFilePath = new File(toSystemDependentName(module.getModuleFilePath()));
    return moduleFilePath.getParentFile();
  }

  private void setUpJavaModule(@NotNull Module module,
                               @NotNull SyncAction.ModuleModels models,
                               @NotNull ProgressIndicator indicator,
                               boolean androidProjectWithoutVariants) {
    ModuleExtendedModel javaModel = models.findModel(ModuleExtendedModel.class);
    JavaModuleModel javaModuleModel = new JavaModuleModel(models.getModule(), javaModel, androidProjectWithoutVariants);
    myJavaModuleSetup.setUpModule(module, myIdeModelsProvider, javaModuleModel, models, indicator);
  }
}
