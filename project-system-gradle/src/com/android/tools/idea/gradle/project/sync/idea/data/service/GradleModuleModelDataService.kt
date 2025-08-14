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
package com.android.tools.idea.gradle.project.sync.idea.data.service

import com.android.tools.idea.gradle.project.entities.GradleModuleModelEntity
import com.android.tools.idea.gradle.project.entities.gradleModuleModel
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet
import com.android.tools.idea.gradle.project.model.GradleModuleModel
import com.android.tools.idea.gradle.project.sync.setup.Facets
import com.intellij.facet.ModifiableFacetModel
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.platform.workspace.jps.entities.ModuleId
import com.intellij.platform.workspace.jps.entities.modifyModuleEntity
import org.jetbrains.plugins.gradle.util.GradleConstants
import com.android.tools.idea.gradle.project.sync.setup.Facets.findFacet;


/** Applies Gradle settings to the modules of Gradle project.  */
class GradleModuleModelDataService @Suppress("unused") // Instantiated by IDEA
constructor() : ModuleModelDataService<GradleModuleModel>() {
  override fun getTargetDataKey(): Key<GradleModuleModel> {
    return AndroidProjectKeys.GRADLE_MODULE_MODEL
  }

  override fun importData(
    toImport: Collection<DataNode<GradleModuleModel>>,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider,
    modelsByModuleName: MutableMap<String?, DataNode<GradleModuleModel>>
  ) {
    for (module in modelsProvider.modules) {
      val model = modelsByModuleName[module.name]?.data ?: continue

      val facet = findFacet(module, modelsProvider, GradleFacet.getFacetTypeId()) ?: run {
        // Create facet if it doesn't exist.
        val facetType = GradleFacet.getFacetType()
       facetType.createFacet(module, GradleFacet.getFacetName(), facetType.createDefaultConfiguration(), null).also { facet ->
         @Suppress("UnstableApiUsage")
         modelsProvider.getModifiableFacetModel(module).addFacet(facet, ExternalSystemApiUtil.toExternalSource(GradleConstants.SYSTEM_ID))
       }
      }
      facet.updateLastKnownAgpVersion(model)

      val storage = (modelsProvider as IdeModifiableModelsProviderImpl).actualStorageBuilder
      storage.modifyModuleEntity(storage.resolve(ModuleId(module.name))!!) {
        this.gradleModuleModel = GradleModuleModelEntity(
          entitySource = this@modifyModuleEntity.entitySource,
          gradleModuleModel = model
        )
      }
    }
  }

  override fun removeData(
    toRemoveComputable: Computable<out MutableCollection<out Module?>?>,
    toIgnore: MutableCollection<out DataNode<GradleModuleModel?>?>,
    projectData: ProjectData,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    for (module in toRemoveComputable.get()) {
      val facetModel: ModifiableFacetModel = modelsProvider.getModifiableFacetModel(module)
      Facets.removeAllFacets<GradleFacet?>(facetModel, GradleFacet.getFacetTypeId())
    }
  }
}
