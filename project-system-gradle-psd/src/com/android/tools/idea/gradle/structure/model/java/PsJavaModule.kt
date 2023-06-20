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
package com.android.tools.idea.gradle.structure.model.java

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel
import com.android.tools.idea.gradle.structure.model.ModuleKind
import com.android.tools.idea.gradle.structure.model.PsDeclaredLibraryDependency
import com.android.tools.idea.gradle.structure.model.PsModel
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsModuleType
import com.android.tools.idea.gradle.structure.model.PsProject
import com.android.tools.idea.gradle.structure.model.meta.ModelDescriptor
import com.android.tools.idea.gradle.structure.model.meta.getValue
import com.intellij.icons.AllIcons
import com.intellij.openapi.externalSystem.model.project.dependencies.DependencyNode
import com.intellij.openapi.externalSystem.model.project.dependencies.ProjectDependencies
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import org.jetbrains.plugins.gradle.model.ExternalProject
import java.io.File
import javax.swing.Icon

class PsJavaModule(
  parent: PsProject,
  override val gradlePath: String
  ) : PsModule(parent, ModuleKind.JAVA) {
  override val descriptor by JavaModuleDescriptors
  var resolvedModel: ExternalProject? = null ; private set
  var resolvedModelDependencies: ProjectDependencies? = null ; private set
  override var rootDir: File? = null ; private set
  override val projectType: PsModuleType = PsModuleType.JAVA
  override val icon: Icon? = AllIcons.Nodes.Module
  private var myDependencyCollection: PsDeclaredJavaDependencyCollection? = null
  private var myResolvedDependencyCollection: PsResolvedJavaDependencyCollection? = null

  fun init(
    name: String,
    parentModule: PsModule?,
    resolvedModel: ExternalProject?,
    dependencies: ProjectDependencies?,
    parsedModel: GradleBuildModel?
  ) {
    super.init(name, parentModule, parsedModel)
    this.resolvedModel = resolvedModel
    this.resolvedModelDependencies = dependencies
    rootDir = resolvedModel?.projectDir
    myResolvedDependencyCollection = null
    myDependencyCollection?.let { it.refresh(); fireDependenciesReloadedEvent() }
  }

  override val dependencies: PsDeclaredJavaDependencyCollection
    get() = myDependencyCollection ?: PsDeclaredJavaDependencyCollection(this).also { myDependencyCollection = it }

  val resolvedDependencies: PsResolvedJavaDependencyCollection
    get() = myResolvedDependencyCollection ?: PsResolvedJavaDependencyCollection(this).also { myResolvedDependencyCollection = it }

  override fun getConfigurations(onlyImportantFor: ImportantFor?): List<String> {
    val defaultImportant = setOf("implementation",
                        "annotationProcessor",
                        "api",
                        "compile",
                        "runtime",
                        "testAnnotationProcessor",
                        "testImplementation",
                        "testRuntime")
    val defaultOther = setOf("implementation",
                        "annotationProcessor",
                        "api",
                        "compile",
                        "compileOnly",
                        "runtime",
                        "runtimeOnly",
                        "testAnnotationProcessor",
                        "testCompile",
                        "testCompileOnly",
                        "testImplementation",
                        "testRuntime",
                        "testRuntimeOnly")
    val projectConfigs = resolvedModel?.artifactsByConfiguration?.keys ?: setOf()
    return when {
      onlyImportantFor != null -> defaultImportant.toList()
      else -> (defaultImportant + defaultOther + projectConfigs).toList()
    }
  }

  // Java libraries can depend on any type of modules, including Android apps (when a Java library is actually a 'test'
  // module for the Android app.)
  override fun canDependOn(module: PsModule): Boolean = true

  override fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency> =
    dependencies.findLibraryDependencies(group, name)

  override fun resetDependencies() {
    myDependencyCollection?.refresh()
    myResolvedDependencyCollection = null
  }

  override fun maybeAddConfiguration(configurationName: String) = Unit
  override fun maybeRemoveConfiguration(configurationName: String) = Unit

  object JavaModuleDescriptors: ModelDescriptor<PsJavaModule, Nothing, Nothing> {
    override fun getResolved(model: PsJavaModule): Nothing? = null
    override fun getParsed(model: PsJavaModule): Nothing? = null
    override fun prepareForModification(model: PsJavaModule) = Unit
    override fun setModified(model: PsJavaModule) { model.isModified = true }
    override fun enumerateModels(model: PsJavaModule): Collection<PsModel> = model.dependencies.items
  }
}
