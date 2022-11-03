/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.gradle.project.sync.idea.data.model.ProjectJdkUpdateData
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.PROJECT_JDK_UPDATE
import com.android.tools.idea.gradle.project.sync.jdk.JdkUtils
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project

/**
 * Data service that updates the project Jdk located under .idea/misc.xml and used to resolve
 * project symbols after a successful gradle sync.
 *
 * The data node [ProjectJdkUpdateData] provides the necessary data to be able to update project jdk
 */
class ProjectJdkUpdateService : AbstractProjectDataService<ProjectJdkUpdateData, Project?>() {
  override fun getTargetDataKey() = PROJECT_JDK_UPDATE

  override fun importData(
    toImport: Collection<DataNode<ProjectJdkUpdateData>>,
    projectData: ProjectData?,
    project: Project,
    modelsProvider: IdeModifiableModelsProvider
  ) {
    if (toImport.isEmpty() || projectData == null) return
    require(toImport.size == 1) { "Expected to get a single project but got ${toImport.size}: $toImport" }
    ExternalSystemApiUtil.executeProjectChangeAction(object : DisposeAwareProjectChange(project) {
      override fun execute() {
        JdkUtils.updateProjectJdkWithPath(project, toImport.single().data.jdkPath)
      }
    })
  }
}