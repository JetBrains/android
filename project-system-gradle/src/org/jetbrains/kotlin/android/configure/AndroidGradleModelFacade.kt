/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.idea.gradle.inspections.KotlinGradleModelFacade
import org.jetbrains.kotlin.idea.gradle.inspections.findModulesByNames

class AndroidGradleModelFacade : KotlinGradleModelFacade {
    override fun getDependencyModules(ideModule: DataNode<ModuleData>, gradleIdeaProject: IdeaProject): Collection<DataNode<ModuleData>> {
        val ideProject = ideModule.parent as DataNode<ProjectData>
        ExternalSystemApiUtil.find(ideModule, AndroidProjectKeys.ANDROID_MODEL)?.let { androidModel ->
            val modules = androidModel.data.mainArtifact.level2Dependencies.moduleDependencies
            val projectIds = modules.mapNotNull { it.projectPath }
            return projectIds.mapNotNullTo(LinkedHashSet()) { projectId ->
                ExternalSystemApiUtil.findFirstRecursively(ideProject) {
                  (it.data as? ModuleData)?.id == projectId
                } as DataNode<ModuleData>?
            }

        }
        return emptyList()
    }
}
