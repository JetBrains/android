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

import static com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacetType;
import com.android.tools.idea.gradle.project.model.GradleModuleModel;
import com.android.tools.idea.gradle.project.sync.GradleSyncStateHolder;
import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;

public class GradleModuleSetup {
  public void setUpModule(@NotNull Module module,
                          @NotNull IdeModifiableModelsProvider ideModelsProvider,
                          @NotNull GradleModuleModel model) {
    GradleFacet facet = findFacet(module, ideModelsProvider, GradleFacet.getFacetTypeId());
    if (facet == null) {
      ModifiableFacetModel facetModel = ideModelsProvider.getModifiableFacetModel(module);
      GradleFacetType facetType = GradleFacet.getFacetType();
      facet = facetType.createFacet(module, GradleFacet.getFacetName(), facetType.createDefaultConfiguration(), null);
      //noinspection UnstableApiUsage
      facetModel.addFacet(facet, ExternalSystemApiUtil.toExternalSource(GradleConstants.SYSTEM_ID));
    }
    facet.setGradleModuleModel(model);

    String gradleVersion = model.getGradleVersion();
    if (isNotEmpty(gradleVersion)) {
        GradleSyncStateHolder.getInstance(module.getProject()).recordGradleVersion(GradleVersion.version(gradleVersion));
    }
  }
}
