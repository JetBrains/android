/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.structure.configurables.CachingRepositorySearchFactory
import com.android.tools.idea.gradle.structure.configurables.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import java.util.*
import java.util.function.Consumer
import javax.swing.Icon

class PsProjectImpl(
  override val ideProject: Project,
  override val repositorySearchFactory: RepositorySearchFactory = CachingRepositorySearchFactory()
) : PsChildModel(), PsProject {
  override val descriptor by ProjectDescriptors
  override var parsedModel: ProjectBuildModel = GradleModelProvider.get().getProjectModel(ideProject); private set
  @Suppress("RedundantModalityModifier")  // Kotlin compiler bug (KT-24833)?
  final override val variables: PsVariables
  override val pomDependencyCache: PsPomDependencyCache = PsPomDependencies()
  private var internalResolvedModuleModels: Map<String, PsResolvedModuleModel>? = null
  private val moduleCollection: PsModuleCollection

  override val name: String get() = ideProject.name  // Supposedly there is no way to rename the project from within the PSD.

  override val parent: PsModel? = null
  override val isDeclared: Boolean = true
  override val icon: Icon? = null

  override val modules: PsModelCollection<PsModule> get() = moduleCollection
  override val modelCount: Int get() = moduleCollection.size

  init {
    // TODO(b/77695733): Ensure that getProjectBuildModel() is indeed not null.
    variables = PsVariables(this, "Project: $name", null)
    moduleCollection = PsModuleCollection(this)
  }

  override fun findModuleByName(moduleName: String): PsModule? =
    moduleCollection.firstOrNull { it -> it.name == moduleName }

  override fun findModuleByGradlePath(gradlePath: String): PsModule? =
    moduleCollection.firstOrNull { it -> it.gradlePath == gradlePath }

  override fun forEachModule(consumer: Consumer<PsModule>) {
    moduleCollection.sortedBy { it.name.toLowerCase() }.forEach(consumer)
  }

  override fun removeModule(gradlePath: String) {
    parsedModel.projectSettingsModel?.removeModulePath(gradlePath)
    isModified = true
    moduleCollection.refresh()
  }

  override fun applyChanges() {
    if (isModified) {
      object : WriteCommandAction<Nothing>(ideProject, "Applying changes to the project structure.") {
        override fun run(result: Result<Nothing>) {
          parsedModel.applyChanges()
          isModified = false
        }
      }.execute()
      parsedModel = GradleModelProvider.get().getProjectModel(ideProject)
      variables.refresh()
      internalResolvedModuleModels = null
      moduleCollection.refresh()
    }
  }

  fun refreshFrom(models: List<PsResolvedModuleModel>) {
    internalResolvedModuleModels = models.associateBy { it.gradlePath }
    moduleCollection.refresh()
  }

  internal fun getResolvedModuleModelsByGradlePath(): Map<String, PsResolvedModuleModel> =
    internalResolvedModuleModels ?: mapOf()

  fun applyRunAndReparse(runnable: () -> Boolean) {
    if (isModified) {
      object : WriteCommandAction<Nothing>(ideProject, "Applying changes to the project structure.") {
        override fun run(result: Result<Nothing>) {
          parsedModel.applyChanges()
          isModified = false
        }
      }.execute()
    }
    if (runnable()) {
      parsedModel = GradleModelProvider.get().getProjectModel(ideProject)
      variables.refresh()
      internalResolvedModuleModels = null
      moduleCollection.refresh()
      isModified = true  // This is to trigger apply() which in turn will trigger the final sync.
    }
  }

  object ProjectDescriptors: ModelDescriptor<PsProject, Nothing, Nothing> {
    override fun getResolved(model: PsProject): Nothing? = null
    override fun getParsed(model: PsProject): Nothing? = null
    override fun setModified(model: PsProject) { model.isModified = true }
    override fun enumerateModels(model: PsProject): Collection<PsModel> = model.modules
  }
}
