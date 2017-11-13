/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.setup.module.idea.java;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacet;
import com.android.tools.idea.gradle.project.facet.java.JavaFacetConfiguration;
import com.android.tools.idea.gradle.project.model.JavaModuleModel;
import com.android.tools.idea.gradle.project.sync.ModuleSetupContext;
import com.android.tools.idea.gradle.project.sync.setup.module.idea.JavaModuleSetupStep;
import com.intellij.facet.FacetManager;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.intellij.openapi.util.io.FileUtil.toSystemIndependentName;

public class JavaFacetModuleSetupStep extends JavaModuleSetupStep {
  @Override
  protected void doSetUpModule(@NotNull ModuleSetupContext context, @NotNull JavaModuleModel javaModuleModel) {
    Module module = context.getModule();
    IdeModifiableModelsProvider ideModelsProvider = context.getIdeModelsProvider();
    JavaFacet facet = setAndGetJavaGradleFacet(module, ideModelsProvider);

    GradleFacet gradleFacet = findFacet(module, ideModelsProvider, GradleFacet.getFacetTypeId());
    if (gradleFacet != null) {
      // This is an actual Gradle module, because it has the GradleFacet. Top-level modules in a multi-module project usually don't
      // have this facet.
      facet.setJavaModuleModel(javaModuleModel);
    }

    File buildFolderPath = javaModuleModel.getBuildFolderPath();

    JavaFacetConfiguration facetProperties = facet.getConfiguration();
    facetProperties.BUILD_FOLDER_PATH = buildFolderPath != null ? toSystemIndependentName(buildFolderPath.getPath()) : "";
    facetProperties.BUILDABLE = javaModuleModel.isBuildable();
  }

  @NotNull
  private static JavaFacet setAndGetJavaGradleFacet(@NotNull Module module, @NotNull IdeModifiableModelsProvider modelsProvider) {
    JavaFacet facet = findFacet(module, modelsProvider, JavaFacet.getFacetTypeId());
    if (facet != null) {
      return facet;
    }

    FacetManager facetManager = FacetManager.getInstance(module);
    ModifiableFacetModel model = modelsProvider.getModifiableFacetModel(module);
    facet = facetManager.createFacet(JavaFacet.getFacetType(), JavaFacet.getFacetName(), null);
    model.addFacet(facet);
    return facet;
  }

  @Override
  public boolean invokeOnSkippedSync() {
    return true;
  }
}
