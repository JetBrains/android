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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.repositories.search.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.repositories.search.ArtifactRepository
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import java.util.function.Consumer

interface PsProject : PsModel {
  val ideProject: Project
  val parsedModel: ProjectBuildModel
  val buildScriptVariables: PsVariables
  val versionCatalogVariables: PsVariables
  val variables: PsVariables
  val pomDependencyCache: PsPomDependencyCache
  val repositorySearchFactory: RepositorySearchFactory
  val modules: PsModelCollection<PsModule>
  val modelCount: Int
  var androidGradlePluginVersion: ParsedValue<String>
  var gradleVersion: ParsedValue<String>

  fun getPluginArtifactRepositories(): Collection<ArtifactRepository>

  fun findModuleByName(moduleName: String): PsModule?
  fun findModuleByGradlePath(gradlePath: String): PsModule?
  fun forEachModule(consumer: Consumer<PsModule>)
  fun applyChanges()

  fun removeModule(gradlePath: String)

  fun onModuleChanged(disposable: Disposable, handler: (PsModule) -> Unit)

  fun getGradleVersionValue(notApplied: Boolean): String?
  fun setGradleVersionValue(value: String)
}
