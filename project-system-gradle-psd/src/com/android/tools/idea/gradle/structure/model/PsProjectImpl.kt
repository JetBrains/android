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

import com.android.tools.idea.gradle.dsl.api.GradleModelProvider
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.gradle.repositories.search.CachingRepositorySearchFactory
import com.android.tools.idea.gradle.repositories.search.RepositorySearchFactory
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.android.tools.idea.gradle.repositories.search.AndroidSdkRepositories
import com.android.tools.idea.gradle.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.GradleResolver
import com.android.tools.idea.gradle.util.GradleWrapper
import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.TestOnly
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import javax.swing.Icon

class PsProjectImpl(
  override val ideProject: Project,
  override val repositorySearchFactory: RepositorySearchFactory = CachingRepositorySearchFactory()
) : PsChildModel(), PsProject {
  override val descriptor by PsProjectDescriptors
  override var parsedModel: ProjectBuildModel = GradleModelProvider.getInstance().getProjectModel(ideProject); private set
  @Suppress("RedundantModalityModifier")  // Kotlin compiler bug (KT-24833)?
  final override val buildScriptVariables: PsVariables
  @Suppress("RedundantModalityModifier")  // Kotlin compiler bug (KT-24833)?
  final override val variables: PsVariables
  override val pomDependencyCache: PsPomDependencyCache = PsPomDependencies(ideProject)
  private var internalResolvedModuleModels: Map<String, PsResolvedModuleModel>? = null
  private val moduleCollection: PsModuleCollection
  val buildScript : PsBuildScript = PsBuildScript(this)
  override val name: String get() = ideProject.name  // Supposedly there is no way to rename the project from within the PSD.

  override val parent: PsModel? = null
  override val isDeclared: Boolean = true
  override val icon: Icon? = null

  override val modules: PsModelCollection<PsModule> get() = moduleCollection
  override val modelCount: Int get() = moduleCollection.size
  override var androidGradlePluginVersion by PsProjectDescriptors.androidGradlePluginVersion
  override var gradleVersion by PsProjectDescriptors.gradleVersion

  private var gradleVersionModified = false
  private var newGradleVersion: String? = null

  init {
    // TODO(b/77695733): Ensure that getProjectBuildModel() is indeed not null.
    buildScriptVariables = PsVariables(buildScript, "$name (build script)", "Build Script: $name", null)
    variables = PsVariables(this, "$name (project)", "Project: $name", buildScriptVariables)
    moduleCollection = PsModuleCollection(this)
  }

  override fun getPluginArtifactRepositories(): Collection<ArtifactRepository> =
    (parsedModel
       .projectBuildModel
       ?.buildscript()
       ?.repositories()
       ?.repositories()
       .orEmpty()
       .mapNotNull { it.toArtifactRepository() } +
     parsedModel
       .projectSettingsModel
       ?.pluginManagement()
       ?.repositories()
       ?.repositories()
       .orEmpty()
       .mapNotNull { it.toArtifactRepository() } +
     listOfNotNull(AndroidSdkRepositories.getAndroidRepository(), AndroidSdkRepositories.getGoogleRepository())
    ).toSet()

  override fun findModuleByName(moduleName: String): PsModule? =
    moduleCollection.firstOrNull { it -> it.name == moduleName }

  override fun findModuleByGradlePath(gradlePath: String): PsModule? =
    moduleCollection.firstOrNull { it -> it.gradlePath == gradlePath }

  override fun forEachModule(consumer: Consumer<PsModule>) {
    moduleCollection.sortedBy { it.name.lowercase(Locale.US) }.forEach(consumer)
  }

  override fun removeModule(gradlePath: String) {
    findModuleByGradlePath(gradlePath)?.let { module ->
      moduleCollection.remove(ModuleKey(module.moduleKind, gradlePath))
    }
  }

  override fun applyChanges() {
    if (isModified) {
      WriteCommandAction.writeCommandAction(ideProject).withName("Applying changes to the project structure.").run<RuntimeException> {
        parsedModel.applyChanges()
        if (gradleVersionModified) {
          GradleWrapper.find(ideProject)?.updateDistributionUrlAndDisplayFailure(newGradleVersion!!)
        }
        isModified = false
      }
      parsedModel = GradleModelProvider.getInstance().getProjectModel(ideProject)
      variables.refresh()
      buildScriptVariables.refresh()
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
      WriteCommandAction.writeCommandAction(ideProject).withName("Applying changes to the project structure.").run<RuntimeException> {
        parsedModel.applyChanges()
        isModified = false
      }
    }
    if (runnable()) {
      parsedModel = GradleModelProvider.getInstance().getProjectModel(ideProject)
      variables.refresh()
      internalResolvedModuleModels = null
      moduleCollection.refresh()
      isModified = true  // This is to trigger apply() which in turn will trigger the final sync.
    }
  }

  override fun onModuleChanged(disposable: Disposable, handler: (PsModule) -> Unit) {
    moduleCollection.onModuleChanged(disposable, handler)
  }

  override fun getGradleVersionValue(notApplied: Boolean): String? =
    if (notApplied && gradleVersionModified) newGradleVersion
    else GradleWrapper.find(ideProject)?.gradleVersion

  override fun setGradleVersionValue(value: String) {
    if (value == getGradleVersionValue(notApplied = true).orEmpty()) return
    isModified = true
    gradleVersionModified = true
    newGradleVersion = value
  }
}

@TestOnly
fun PsProjectImpl.testResolve() {
  // NOTE: The timeout is intentionally too high as in the test environment it may initially take longer to resolve all variants.
  refreshFrom(GradleResolver().requestProjectResolved(ideProject, ideProject).get(90, TimeUnit.SECONDS))
}

