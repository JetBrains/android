/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.dcl.lang.sync

import com.intellij.openapi.externalSystem.model.DataNode
import com.intellij.openapi.externalSystem.model.Key
import com.intellij.openapi.externalSystem.model.project.ProjectData
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants
import com.intellij.openapi.externalSystem.util.Order
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension
import org.gradle.declarative.dsl.tooling.models.DeclarativeSchemaModel
import com.android.tools.idea.gradle.dcl.lang.flags.DeclarativeIdeSupport

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

  override fun populateProjectExtraModels(gradleProject: IdeaProject, ideProject: DataNode<ProjectData>) {
    if (DeclarativeIdeSupport.isEnabled()) {
      val declarativeSchemaModel = resolverCtx.getRootModel(DeclarativeSchemaModel::class.java) ?: return
      ideProject.createChild(DECLARATIVE_PROJECT_SCHEMAS, declarativeSchemaModel.convertProject())
      ideProject.createChild(DECLARATIVE_SETTINGS_SCHEMAS, declarativeSchemaModel.convertSettings())
    }
    nextResolver.populateProjectExtraModels(gradleProject, ideProject)
  }

  override fun getExtraBuildModelClasses(): Set<Class<*>> {
    if (DeclarativeIdeSupport.isEnabled()) {
      return setOf(DeclarativeSchemaModel::class.java)
    }
    return emptySet()
  }

}