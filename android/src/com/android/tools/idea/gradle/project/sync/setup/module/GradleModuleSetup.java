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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetType;
import com.android.tools.idea.gradle.project.sync.GradleSyncState;
import com.android.tools.idea.gradle.project.sync.GradleSyncSummary;
import com.android.tools.idea.gradle.project.sync.ng.SyncAction;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.gradle.GradleScript;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.BuildScriptClasspathModel;

import java.io.File;

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class GradleModuleSetup {
  public void setUpModule(@NotNull Module module,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull SyncAction.ModuleModels models) {
    GradleModuleModel gradleModuleModel = createGradleModel(module, models);
    setUpModule(module, ideModelsProvider, gradleModuleModel);
  }

  @NotNull
  private static GradleModuleModel createGradleModel(@NotNull Module module,
                                                     @NotNull SyncAction.ModuleModels models) {
    GradleProject gradleProject = models.findModel(GradleProject.class);
    assert gradleProject != null;
    GradleScript buildScript = null;
    try {
      buildScript = gradleProject.getBuildScript();
    }
    catch (Throwable e) {
      // Ignored. We got here because the project is using Gradle 1.8 or older.
    }

    File buildFilePath = buildScript != null ? buildScript.getSourceFile() : null;

    BuildScriptClasspathModel classpathModel = models.findModel(BuildScriptClasspathModel.class);
    String gradleVersion = classpathModel != null ? classpathModel.getGradleVersion() : null;

    return new GradleModuleModel(module.getName(), gradleProject, buildFilePath, gradleVersion);
  }

  public void setUpModule(@NotNull Module module,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull GradleModuleModel model) {
    GradleFacet facet = findFacet(module, ideModelsProvider, GradleFacet.getFacetTypeId());
    if (facet == null) {
      ModifiableFacetModel facetModel = ideModelsProvider.getModifiableFacetModel(module);
      GradleFacetType facetType = GradleFacet.getFacetType();
      facet = facetType.createFacet(module, GradleFacet.getFacetName(), facetType.createDefaultConfiguration(), null);
      facetModel.addFacet(facet);
    }
    facet.setGradleModuleModel(model);

    String gradleVersion = model.getGradleVersion();
    GradleSyncSummary syncReport = GradleSyncState.getInstance(module.getProject()).getSummary();
    if (isNotEmpty(gradleVersion) && syncReport.getGradleVersion() == null) {
      syncReport.setGradleVersion(GradleVersion.parse(gradleVersion));
    }
  }
}
