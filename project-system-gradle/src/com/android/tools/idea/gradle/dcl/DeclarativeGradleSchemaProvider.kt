/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl

import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeIdeSupport
import com.android.tools.idea.gradle.dcl.lang.ide.DeclarativeSchemaProvider
import com.android.tools.idea.gradle.dcl.lang.sync.BuildDeclarativeSchemas
import com.android.tools.idea.gradle.project.sync.idea.GradleSchemaProjectResolver.Companion.DECLARATIVE_PROJECT_SCHEMAS
import com.android.tools.idea.gradle.project.sync.idea.GradleSchemaProjectResolver.Companion.DECLARATIVE_SETTINGS_SCHEMAS
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.util.GradleConstants

class DeclarativeGradleSchemaProvider : DeclarativeSchemaProvider {
  override fun getSchema(project: Project): BuildDeclarativeSchemas? {
    if (!DeclarativeIdeSupport.isEnabled()) return null
    val externalProjectPath: String = project.basePath ?: return null
    val projectInfo = ProjectDataManager.getInstance()
      .getExternalProjectData(project, GradleConstants.SYSTEM_ID, externalProjectPath)
    val projectStructure = projectInfo?.externalProjectStructure ?: return null
    val projectSchemas = ExternalSystemApiUtil.find(projectStructure, DECLARATIVE_PROJECT_SCHEMAS)
    val settingsSchemas = ExternalSystemApiUtil.find(projectStructure, DECLARATIVE_SETTINGS_SCHEMAS)
    return if (projectSchemas != null && settingsSchemas != null)
      BuildDeclarativeSchemas(settingsSchemas.data.settings,
                              projectSchemas.data.projects)
    else null
  }
}