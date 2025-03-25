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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.tools.idea.gradle.dcl.lang.sync.DeclarativeGradleModelProvider
import com.android.tools.idea.gradle.dcl.lang.sync.ProjectSchemas
import com.android.tools.idea.gradle.dcl.lang.sync.SettingsSchemas
import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.model.ProjectImportModelProvider
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension

@Order(ExternalSystemConstants.UNORDERED)
class GradleSchemaProjectResolver : AbstractProjectResolverExtension() {

  companion object {
    val DECLARATIVE_PROJECT_SCHEMAS = Key.create(
      ProjectSchemas::class.java, 1 /* not used */
    )
    val DECLARATIVE_SETTINGS_SCHEMAS = Key.create(
      SettingsSchemas::class.java, 1 /* not used */
    )
  }

  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData?>) {
    val projectSchemas: ProjectSchemas? = resolverCtx.getRootModel(ProjectSchemas::class.java)
    if (projectSchemas != null) {
      ideProject.createChild(DECLARATIVE_PROJECT_SCHEMAS, projectSchemas)
    }
    val settingsSchemas: SettingsSchemas? = resolverCtx.getRootModel(SettingsSchemas::class.java)
    if (settingsSchemas != null) {
      ideProject.createChild(DECLARATIVE_SETTINGS_SCHEMAS, settingsSchemas)
    }
    nextResolver.populateProjectExtraModels(gradleProject, ideProject)
  }

  override fun getModelProvider(): ProjectImportModelProvider {
    return DeclarativeGradleModelProvider()
  }

  override fun getExtraProjectModelClasses(): Set<Class<out Any>> {
    throw UnsupportedOperationException("getExtraProjectModelClasses() is not used when getModelProvider() is overridden.")
  }

}