/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.*
import com.android.utils.usLocaleCapitalize
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.configuration.GradleProjectImportHandler
import org.jetbrains.kotlin.idea.configuration.compilerArgumentsBySourceSet
import org.jetbrains.kotlin.idea.configuration.configureFacetByGradleModule
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData
import java.util.Locale

class KotlinGradleAndroidModuleModelProjectDataService : AbstractProjectDataService<AndroidModuleModel, Void>() {
    override fun getTargetDataKey() = ANDROID_MODEL

    override fun postProcess(
            toImport: MutableCollection<DataNode<AndroidModuleModel>>,
            projectData: ProjectData?,
            project: Project,
            modelsProvider: IdeModifiableModelsProvider
    ) {
        super.postProcess(toImport, projectData, project, modelsProvider)
        for (moduleModelNode in toImport) {
            val moduleNode = ExternalSystemApiUtil.findParent(moduleModelNode, ProjectKeys.MODULE) ?: continue
            val moduleData = moduleNode.data
            val sourceSetNodes = ExternalSystemApiUtil.findAll(moduleNode, GradleSourceSetData.KEY)

            // If module per source set is enabled then we will have source set data nodes
            if (sourceSetNodes.isNotEmpty()) {
              sourceSetNodes.forEach { sourceSetNode ->
                val ideModule = modelsProvider.findIdeModule(sourceSetNode.data) ?: return@forEach
                ideModule.compilerArgumentsBySourceSet = moduleNode.compilerArgumentsBySourceSet
                val kotlinSourceSetName = moduleModelNode.data.selectedVariantName +
                                          (sourceSetNode.data.moduleName.takeUnless { it == "main" }?.usLocaleCapitalize() ?: "")
                val kotlinFacet = configureFacetByGradleModule(ideModule, modelsProvider, moduleNode, sourceSetNode, kotlinSourceSetName)
                                  ?: return@forEach
                GradleProjectImportHandler.getInstances(project).forEach { it.importBySourceSet(kotlinFacet, sourceSetNode) }
              }
              return
            }

            val ideModule = modelsProvider.findIdeModule(moduleData) ?: continue
            val sourceSetName = moduleModelNode.data.selectedVariant.name
            val kotlinFacet = configureFacetByGradleModule(moduleNode, sourceSetName, ideModule, modelsProvider) ?: continue
            GradleProjectImportHandler.getInstances(project).forEach { it.importByModule(kotlinFacet, moduleNode) }
        }
    }
}