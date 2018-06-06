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
import com.android.tools.idea.gradle.project.model.JavaModuleModel
import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency
import com.android.tools.idea.gradle.structure.model.PsModule
import com.android.tools.idea.gradle.structure.model.PsProject
import com.intellij.icons.AllIcons
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.resolveActualCall
import java.io.File
import javax.swing.Icon

class PsJavaModule(
  parent: PsProject,
  name: String,
  gradlePath: String,
  val gradleModel: JavaModuleModel,
  parsedModel: GradleBuildModel
) : PsModule(parent, name, gradlePath, parsedModel) {

  private var myDependencyCollection: PsJavaDependencyCollection? = null

  override val rootDir: File? get() = gradleModel.contentRoots.firstOrNull()?.rootDirPath
  override val icon: Icon? = AllIcons.Nodes.PpJdk

  private val orCreateDependencyCollection: PsJavaDependencyCollection
    get() = myDependencyCollection ?: PsJavaDependencyCollection(this).also { myDependencyCollection = it }

  override fun getConfigurations(): List<String> = gradleModel.configurations

  // Java libraries can depend on any type of modules, including Android apps (when a Java library is actually a 'test'
  // module for the Android app.)
  override fun canDependOn(module: PsModule): Boolean = true

  fun forEachDeclaredDependency(consumer: (PsJavaDependency) -> Unit) {
    orCreateDependencyCollection.forEachDeclaredDependency(consumer)
  }

  fun forEachDependency(consumer: (PsJavaDependency) -> Unit) {
    orCreateDependencyCollection.forEach(consumer)
  }

  override fun addLibraryDependency(library: String, scopesNames: List<String>) {
    // Update/reset the "parsed" model.
    addLibraryDependencyToParsedModel(scopesNames, library)

    // Reset dependencies.
    myDependencyCollection = null

    val spec = PsArtifactDependencySpec.create(library)!!
    fireLibraryDependencyAddedEvent(spec)
    isModified = true
  }

  override fun addModuleDependency(modulePath: String, scopesNames: List<String>) {
    throw UnsupportedOperationException()
  }

  override fun removeDependency(dependency: PsDeclaredDependency) {
    throw UnsupportedOperationException()
  }

  override fun setLibraryDependencyVersion(spec: PsArtifactDependencySpec,
                                           configurationName: String,
                                           newVersion: String) {
    throw UnsupportedOperationException()
  }
}
