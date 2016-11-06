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
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.JavaProject;
import com.android.tools.idea.gradle.project.sync.SyncAction;
import com.android.tools.idea.gradle.project.sync.common.VariantSelector;
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
  @NotNull private final AndroidModuleSetupStep[] myAndroidModuleSetupSteps;
  @NotNull private final JavaModuleSetupStep[] myJavaModuleSetupSteps;

  public ModuleSetup(@NotNull IdeModifiableModelsProvider ideModelsProvider) {
    this(ideModelsProvider, new VariantSelector(), new GradleModuleSetup(), AndroidModuleSetupStep.getExtensions(),
         JavaModuleSetupStep.getExtensions());
  }

  @VisibleForTesting
  ModuleSetup(@NotNull IdeModifiableModelsProvider ideModelsProvider,
              @NotNull VariantSelector variantSelector,
              @NotNull GradleModuleSetup gradleModuleSetup,
              @NotNull AndroidModuleSetupStep[] androidModuleSetupSteps,
              @NotNull JavaModuleSetupStep[] javaModuleSetupSteps) {
    myIdeModelsProvider = ideModelsProvider;
    myVariantSelector = variantSelector;
    myGradleModuleSetup = gradleModuleSetup;
    myAndroidModuleSetupSteps = androidModuleSetupSteps;
    myJavaModuleSetupSteps = javaModuleSetupSteps;
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
    if (androidProject == null) {
      // Remove any AndroidFacet set in a previous sync operation.
      removeAllFacetsOfType(AndroidFacet.ID, myIdeModelsProvider.getModifiableFacetModel(module));
    }
    else {
      AndroidGradleModel androidModel = createAndroidModel(module, androidProject);
      if (androidModel == null) {
        // Android module without variants. Treat as a non-buildable Java module.
        setUpJavaModule(module, models, indicator, true /* Android project without variants */);
      }
      else {
        setUpAndroidModule(module, models, indicator, androidModel);
      }
    }
  }

  @Nullable
  private AndroidGradleModel createAndroidModel(@NotNull Module module, @NotNull AndroidProject androidProject) {
    Variant variantToSelect = myVariantSelector.findVariantToSelect(androidProject);
    if (variantToSelect != null) {
      return new AndroidGradleModel(module.getName(), getModulePath(module), androidProject, variantToSelect.getName());
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
    //noinspection deprecation
    ModuleExtendedModel javaModel = models.findModel(ModuleExtendedModel.class);
    JavaProject javaProject = JavaProject.create(models.getModule(), javaModel, androidProjectWithoutVariants);
    for (JavaModuleSetupStep step : myJavaModuleSetupSteps) {
      displayStepDescription(step.getDescription(), module, indicator);
      step.setUpModule(module, javaProject, myIdeModelsProvider, models, indicator);
    }
  }

  private void setUpAndroidModule(@NotNull Module module,
                                  @NotNull SyncAction.ModuleModels models,
                                  @NotNull ProgressIndicator indicator,
                                  @NotNull AndroidGradleModel androidModel) {
    for (AndroidModuleSetupStep step : myAndroidModuleSetupSteps) {
      displayStepDescription(step.getDescription(), module, indicator);
      step.setUpModule(module, myIdeModelsProvider, androidModel, models, indicator);
    }
  }

  private static void displayStepDescription(@NotNull String stepDescription,
                                             @NotNull Module module,
                                             @NotNull ProgressIndicator indicator) {
    indicator.setText2(String.format("Module ''%1$s': %2$s", module.getName(), stepDescription));
  }
}
