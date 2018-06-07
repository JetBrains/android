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

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.structure.model.repositories.search.ArtifactRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.JCenterRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.LocalMavenRepository
import com.android.tools.idea.gradle.structure.model.repositories.search.MavenCentralRepository
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Result
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.util.EventDispatcher
import com.intellij.util.Urls
import icons.StudioIcons.Shell.Filetree.ANDROID_MODULE
import java.io.File
import java.util.*
import javax.swing.Icon

abstract class PsModule protected constructor(
  override val parent: PsProject,
  open val gradlePath: String?
) : PsChildModel() {
  override var name: String = ""
  var parsedModel: GradleBuildModel? = null

  private var myParsedDependencies: PsParsedDependencies? = null
  private var myVariables: PsVariablesScope? = null
  private val dependenciesChangeEventDispatcher = EventDispatcher.create(DependenciesChangeListener::class.java)

  val parsedDependencies: PsParsedDependencies
    get() = myParsedDependencies ?: PsParsedDependencies(parsedModel).also { myParsedDependencies = it }

  val variables: PsVariablesScope
    get() = myVariables ?: createVariablesScopeFor(this, name, parent.variables, parsedModel).also { myVariables = it }

  override val isDeclared: Boolean get() = parsedModel != null

  override val icon: Icon? = ANDROID_MODULE

  /**
   * <All Modules> constructor.
   */
  protected constructor(name: String, parent: PsProject) : this(parent, null) {
    init(name, null)
  }

  protected fun init(name: String, parsedModel: GradleBuildModel?) {
    this.name = name
    this.parsedModel = parsedModel

    myParsedDependencies = null
    myVariables = null
  }

  abstract val rootDir: File?
  abstract fun getConfigurations(): List<String>
  abstract fun addLibraryDependency(library: String, scopesNames: List<String>)
  abstract fun addModuleDependency(modulePath: String, scopesNames: List<String>)
  abstract fun removeDependency(dependency: PsDeclaredDependency)
  abstract fun setLibraryDependencyVersion(
    spec: PsArtifactDependencySpec,
    configurationName: String,
    newVersion: String)

  fun getArtifactRepositories(): List<ArtifactRepository> {
    val repositories = mutableListOf<ArtifactRepository>()
    populateRepositories(repositories)
    return repositories
  }

  fun add(listener: DependenciesChangeListener, parentDisposable: Disposable) {
    dependenciesChangeEventDispatcher.addListener(listener, parentDisposable)
  }

  fun addDependencyChangedListener(parentDisposable: Disposable, listener: (DependencyChangedEvent) -> Unit) {
    dependenciesChangeEventDispatcher.addListener(object : DependenciesChangeListener {
      override fun dependencyChanged(event: DependencyChangedEvent) = listener(event)
    }, parentDisposable)
  }

  fun fireDependencyModifiedEvent(dependency: PsDeclaredDependency) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(DependencyModifiedEvent(dependency))
  }

  fun fireDependencyRemovedEvent(dependency: PsDeclaredDependency) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(DependencyRemovedEvent(dependency))
  }

  open fun canDependOn(module: PsModule): Boolean = false

  open fun applyChanges() {
    if (isModified && parsedModel?.isModified == true) {
      val name = String.format("Applying changes to module '%1\$s'", name)
      object : WriteCommandAction<Nothing>(parent.resolvedModel, name) {
        override fun run(result: Result<Nothing>) {
          parsedModel?.applyChanges()
          isModified = false
        }
      }.execute()
    }
  }

  protected fun addLibraryDependencyToParsedModel(configurationNames: List<String>, compactNotation: String) {
    parsedModel?.let { parsedModel ->
      val dependencies = parsedModel.dependencies()
      configurationNames.forEach { configurationName -> dependencies.addArtifact(configurationName, compactNotation) }
      parsedDependencies.reset(parsedModel)
    }
  }

  protected fun addModuleDependencyToParsedModel(configurationNames: List<String>, modulePath: String) {
    parsedModel?.let { parsedModel ->
      val dependencies = parsedModel.dependencies()
      configurationNames.forEach { configurationName -> dependencies.addModule(configurationName, modulePath) }
      parsedDependencies.reset(parsedModel)
    }
  }

  protected fun removeDependencyFromParsedModel(dependency: PsDeclaredDependency) {
    parsedModel?.let { parsedModel ->
      parsedModel.dependencies().remove(dependency.parsedModel)
      parsedDependencies.reset(parsedModel)
    }
  }

  protected fun fireLibraryDependencyAddedEvent(spec: PsArtifactDependencySpec) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(LibraryDependencyAddedEvent(spec))
  }

  protected fun fireModuleDependencyAddedEvent(modulePath: String) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(ModuleDependencyAddedEvent(modulePath))
  }

  protected open fun populateRepositories(repositories: MutableList<ArtifactRepository>) {
    repositories.addAll(
      parsedModel?.repositories()?.repositories().orEmpty().mapNotNull { repositoryModel ->
        when (repositoryModel.type) {
          RepositoryModel.RepositoryType.JCENTER_DEFAULT -> JCenterRepository()
          RepositoryModel.RepositoryType.MAVEN_CENTRAL -> MavenCentralRepository()
          RepositoryModel.RepositoryType.MAVEN -> maybeCreateLocalMavenRepository(repositoryModel as MavenRepositoryModel)
          RepositoryModel.RepositoryType.GOOGLE_DEFAULT -> null
          RepositoryModel.RepositoryType.FLAT_DIR -> null
        }
      })
  }

  private fun maybeCreateLocalMavenRepository(mavenRepositoryModel: MavenRepositoryModel): LocalMavenRepository? {
    val repositoryUrl = mavenRepositoryModel.url().forceString()
    val parsedRepositoryUrl = Urls.parse(repositoryUrl, false)
    if (parsedRepositoryUrl != null && parsedRepositoryUrl.isInLocalFileSystem) {
      val repositoryPath = parsedRepositoryUrl.path
      val repositoryRootFile = File(repositoryPath)
      if (repositoryRootFile.isAbsolute) {
        return LocalMavenRepository(repositoryRootFile, mavenRepositoryModel.name().forceString())
      }
    }
    return null
  }

  interface DependenciesChangeListener : EventListener {
    fun dependencyChanged(event: DependencyChangedEvent)
  }

  interface DependencyChangedEvent

  class LibraryDependencyAddedEvent internal constructor(val spec: PsArtifactDependencySpec) : DependencyChangedEvent

  class ModuleDependencyAddedEvent internal constructor(val modulePath: String) : DependencyChangedEvent

  class DependencyModifiedEvent internal constructor(val dependency: PsDeclaredDependency) : DependencyChangedEvent

  class DependencyRemovedEvent internal constructor(val dependency: PsDeclaredDependency) : DependencyChangedEvent
}

private fun createVariablesScopeFor(
  module: PsModule,
  name: String,
  parentVariables: PsVariables,
  parsedModel: GradleBuildModel?
): PsVariablesScope =
  parsedModel?.let { PsVariables(module, "Module: $name", it.ext(), parentVariables) } ?: PsVariablesScope.NONE
