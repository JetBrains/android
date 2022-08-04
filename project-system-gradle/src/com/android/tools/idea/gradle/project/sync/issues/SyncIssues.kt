/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.issues

import com.android.tools.idea.gradle.model.IdeSyncIssue
import com.android.tools.idea.gradle.project.sync.idea.data.service.AndroidProjectKeys.SYNC_ISSUE
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.ProjectKeys
import com.intellij.openapi.externalSystem.model.project.ModuleData
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.find
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findAll
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.findProjectData
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

class SyncIssues(private val issues: List<IdeSyncIssue>) : List<IdeSyncIssue> by issues {
  companion object {
    @JvmField
    val EMPTY = SyncIssues(emptyList())

    @JvmName("forModule")
    @JvmStatic
    fun Module.syncIssues(): SyncIssues {
      val linkedProjectPath = ExternalSystemApiUtil.getExternalRootProjectPath(this) ?: return EMPTY
      val projectDataNode = findProjectData(project, GradleConstants.SYSTEM_ID, linkedProjectPath) ?: return EMPTY
      val moduleDataNode = find(projectDataNode, ProjectKeys.MODULE) { node ->
        node.data.internalName == name
      } ?: return EMPTY
      return SyncIssues(findAll(moduleDataNode, SYNC_ISSUE).map { dataNode -> dataNode.data })
    }
  }
}

class SyncIssueDataService : AbstractProjectDataService<IdeSyncIssue, Void>() {
  override fun importData(toImport: Collection<DataNode<IdeSyncIssue>>,
                          projectData: ProjectData?,
                          project: Project,
                          modelsProvider: IdeModifiableModelsProvider) {
    val moduleToSyncIssueMap: MutableMap<Module, List<IdeSyncIssue>> = mutableMapOf()
    ExternalSystemApiUtil.groupBy(toImport, ModuleData::class.java).entrySet().forEach { (moduleNode, syncIssues) ->
      val module = modelsProvider.findIdeModule(moduleNode.data) ?: return@forEach
      moduleToSyncIssueMap[module] = syncIssues.map { it.data }
    }
    val rootProjectPath = projectData?.linkedExternalProjectPath ?: error("projectData required")
    SyncIssuesReporter.getInstance().report(moduleToSyncIssueMap, rootProjectPath)
  }

  override fun getTargetDataKey(): Key<IdeSyncIssue> {
    return SYNC_ISSUE
  }
}