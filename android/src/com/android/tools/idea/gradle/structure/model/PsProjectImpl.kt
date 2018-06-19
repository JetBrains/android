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
import com.android.tools.idea.gradle.project.model.AndroidModuleModel
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.util.GradleUtil
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import org.jetbrains.android.facet.AndroidFacet
import java.util.*
import java.util.function.Consumer
import javax.swing.Icon

class PsProjectImpl(override val ideProject: Project) : PsChildModel(), PsProject {
  override var resolvedModel: Project? = ideProject; private set
  override var parsedModel: ProjectBuildModel = GradleModelProvider.get().getProjectModel(ideProject); private set
  @Suppress("RedundantModalityModifier")  // Kotlin compiler bug (KT-24833)?
  final override var variables: PsVariables
    private set
  override val pomDependencyCache: PsPomDependencyCache = PsPomDependencies()
  private var internalResolvedModuleModels: Map<String, PsResolvedModuleModel>? = null
  private val moduleCollection: PsModuleCollection

  override val name: String get() = resolvedModel?.name ?: ""

  override val parent: PsModel? = null
  override val isDeclared: Boolean = true
  override val icon: Icon? = null

  override val modelCount: Int get() = moduleCollection.items().size

  init {
    // TODO(b/77695733): Ensure that getProjectBuildModel() is indeed not null.
    variables = PsVariables(
      this, "Project: $name", Objects.requireNonNull<GradleBuildModel>(this.parsedModel.projectBuildModel).ext(), null)
    moduleCollection = PsModuleCollection(this)
  }

  override fun findModuleByName(moduleName: String): PsModule? =
    moduleCollection.items().stream().filter { it -> it.name == moduleName }.findFirst().orElse(null)

  override fun findModuleByGradlePath(gradlePath: String): PsModule? =
    moduleCollection.items().stream().filter { it -> it.gradlePath == gradlePath }.findFirst().orElse(null)

  override fun forEachModule(consumer: Consumer<PsModule>) {
    moduleCollection.items().sortedBy { it.name.toLowerCase() }.forEach(consumer)
  }

  override fun applyChanges() {
    if (isModified) {
      object : WriteCommandAction<Nothing>(ideProject, "Applying changes to the project structure.") {
        override fun run(result: Result<Nothing>) {
          parsedModel.applyChanges()
          isModified = false
        }
      }.execute()
      resolvedModel = null
      parsedModel = GradleModelProvider.get().getProjectModel(ideProject)
      variables = PsVariables(
        this, "Project: $name", Objects.requireNonNull<GradleBuildModel>(parsedModel.projectBuildModel).ext(), null)
      internalResolvedModuleModels = null
      moduleCollection.refresh()
    }
  }

  internal fun getResolvedModuleModelsByGradlePath(): Map<String, PsResolvedModuleModel> {
    internalResolvedModuleModels?.let { return it }

    val resolvedModules = mutableMapOf<String, PsResolvedModuleModel>()
    resolvedModel?.let { ModuleManager.getInstance(it).modules }?.forEach { resolvedModel ->
      val gradlePath = GradleUtil.getGradlePath(resolvedModel)
      if (gradlePath != null) {
        val gradleModel = AndroidFacet.getInstance(resolvedModel)
        if (gradleModel != null) {
          val androidModuleModel = AndroidModuleModel.get(gradleModel)
          if (androidModuleModel != null) {
            resolvedModules[gradlePath] = PsResolvedModuleModel.PsAndroidModuleResolvedModel(gradlePath, androidModuleModel)
          }
        }
        else {
          val javaModuleModel = JavaModuleModel.get(resolvedModel)
          if (javaModuleModel != null && javaModuleModel.isBuildable) {
            resolvedModules[gradlePath] = PsResolvedModuleModel.PsJavaModuleResolvedModel(gradlePath, javaModuleModel)
          }
        }
      }
    }
    internalResolvedModuleModels = resolvedModules
    return resolvedModules
  }
}
