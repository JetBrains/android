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
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.api.repositories.MavenRepositoryModel
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
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
  override val parent: PsProject
) : PsChildModel() {
  abstract val gradlePath: String?
  final override var name: String = "" ; private set
  override val path: PsPath? get() = PsModulePath(name)
  var parsedModel: GradleBuildModel? = null ; private set

  var parentModule: PsModule? = null ; private set
  private var myParsedDependencies: PsParsedDependencies? = null
  private var myVariables: PsVariablesScope? = null
  private val dependenciesChangeEventDispatcher = EventDispatcher.create(DependenciesChangeListener::class.java)

  abstract val dependencies: PsDependencyCollection<PsModule, PsDeclaredLibraryDependency, PsDeclaredModuleDependency>
  val parsedDependencies: PsParsedDependencies
    get() = myParsedDependencies ?: PsParsedDependencies(parsedModel).also { myParsedDependencies = it }

  val variables: PsVariablesScope
    get() = myVariables ?: createVariablesScopeFor(this, name, parentModule?.variables ?: parent.variables,
                                                   parsedModel).also { myVariables = it }

  override val isDeclared: Boolean get() = parsedModel != null

  override val icon: Icon? = ANDROID_MODULE

  /**
   * <All Modules> constructor.
   */
  protected constructor(name: String, parent: PsProject) : this(parent) {
    init(name, null, null)
  }

  protected fun init(name: String, parentModule: PsModule?, parsedModel: GradleBuildModel?) {
    this.name = name
    this.parsedModel = parsedModel
    this.parentModule = parentModule

    myParsedDependencies?.let {
      fireDependenciesReloadedEvent()
    }
    myParsedDependencies = null
    myVariables = null
  }

  abstract val rootDir: File?
  abstract val projectType: PsModuleType
  abstract fun getConfigurations(): List<String>
  protected abstract fun resetDependencies()
  protected abstract fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency>

  fun addLibraryDependency(library: ParsedValue.Set.Parsed<String>, scopeName: String) {
    // Update/reset the "parsed" model.
    val compactNotation =
      library.dslText.let {
        when (it) {
          DslText.Literal -> library.value ?: ""
          is DslText.InterpolatedString -> it.text
          is DslText.OtherUnparsedDslText -> it.text
          is DslText.Reference -> "\${${it.text}}"
        }
      }

    addLibraryDependencyToParsedModel(scopeName, compactNotation)

    resetDependencies()

    val spec = PsArtifactDependencySpec.create(compactNotation)!!
    fireLibraryDependencyAddedEvent(spec)
    isModified = true
  }

  fun addModuleDependency(modulePath: String, scopeName: String) {
    // Update/reset the "parsed" model.
    addModuleDependencyToParsedModel(scopeName, modulePath)

    resetDependencies()

    fireModuleDependencyAddedEvent(modulePath)
    isModified = true
  }

  fun removeDependency(dependency: PsDeclaredDependency) {
    removeDependencyFromParsedModel(dependency)

    resetDependencies()

    fireDependencyRemovedEvent(dependency)
    isModified = true
  }

  fun setLibraryDependencyVersion(
    spec: PsArtifactDependencySpec,
    configurationName: String,
    newVersion: String
  ) {
    var modified = false
    val matchingDependencies = findLibraryDependencies(spec.group, spec.name)
      .filter { it -> it.spec == spec }
      .map { it as PsDeclaredDependency }
      .filter { it.configurationName == configurationName }
    // Usually there should be only one item in the matchingDependencies list. However, if there are duplicate entries in the config file
    // it might differ. We update all of them.

    for (dependency in matchingDependencies) {
      val parsedDependency = dependency.parsedModel
      assert(parsedDependency is ArtifactDependencyModel)
      val artifactDependencyModel = parsedDependency as ArtifactDependencyModel
      artifactDependencyModel.version().setValue(newVersion)
      modified = true
    }
    if (modified) {
      resetDependencies()
      for (dependency in matchingDependencies) {
        fireDependencyModifiedEvent(dependency)
      }
      isModified = true
    }
  }

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
      object : WriteCommandAction<Nothing>(parent.ideProject, name) {
        override fun run(result: Result<Nothing>) {
          parsedModel?.applyChanges()
          isModified = false
        }
      }.execute()
    }
  }

  private fun addLibraryDependencyToParsedModel(configurationName: String, compactNotation: String) {
    parsedModel?.let { parsedModel ->
      val dependencies = parsedModel.dependencies()
      dependencies.addArtifact(configurationName, compactNotation)
      parsedDependencies.reset(parsedModel)
    } ?: noParsedModel()
  }

  private fun addModuleDependencyToParsedModel(configurationName: String, modulePath: String) {
    parsedModel?.let { parsedModel ->
      val dependencies = parsedModel.dependencies()
      dependencies.addModule(configurationName, modulePath)
      parsedDependencies.reset(parsedModel)
    } ?: noParsedModel()
  }

  private fun removeDependencyFromParsedModel(dependency: PsDeclaredDependency) {
    parsedModel?.let { parsedModel ->
      parsedModel.dependencies().remove(dependency.parsedModel)
      parsedDependencies.reset(parsedModel)
    } ?: noParsedModel()
  }

  private fun noParsedModel() {
    throw IllegalStateException("Module $name does not have a parsed model.")
  }

  private fun fireLibraryDependencyAddedEvent(spec: PsArtifactDependencySpec) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(LibraryDependencyAddedEvent(spec))
  }

  private fun fireModuleDependencyAddedEvent(modulePath: String) {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(ModuleDependencyAddedEvent(modulePath))
  }

  private fun fireDependenciesReloadedEvent() {
    dependenciesChangeEventDispatcher.multicaster.dependencyChanged(DependenciesReloadedEvent())
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

  class DependenciesReloadedEvent internal constructor() : DependencyChangedEvent
}

private fun createVariablesScopeFor(
  module: PsModule,
  name: String,
  parentVariables: PsVariablesScope,
  parsedModel: GradleBuildModel?
): PsVariablesScope =
  parsedModel?.let { PsVariables(module, "Module: $name", it.ext(), parentVariables) } ?: PsVariablesScope.NONE
