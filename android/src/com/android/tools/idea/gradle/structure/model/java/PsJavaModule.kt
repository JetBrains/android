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
import com.android.tools.idea.gradle.structure.model.*
import com.intellij.icons.AllIcons
import java.io.File
import javax.swing.Icon

class PsJavaModule(
  parent: PsProject,
  gradlePath: String
  ) : PsModule(parent, gradlePath) {
  var resolvedModel: JavaModuleModel? = null ; private set
  override var rootDir: File? = null ; private set
  override val projectType: PsModuleType = PsModuleType.JAVA
  override val icon: Icon? = AllIcons.Nodes.PpJdk
  private var myDependencyCollection: PsJavaDependencyCollection? = null

  fun init(name: String, resolvedModel: JavaModuleModel?, parsedModel: GradleBuildModel?) {
    super.init(name, parsedModel)
    this.resolvedModel = resolvedModel
    rootDir = resolvedModel?.contentRoots?.firstOrNull()?.rootDirPath
    myDependencyCollection = null
  }

  val dependencies: PsJavaDependencyCollection
    get() = myDependencyCollection ?: PsJavaDependencyCollection(this).also { myDependencyCollection = it }

  override fun getConfigurations(): List<String> = resolvedModel?.configurations.orEmpty()

  // Java libraries can depend on any type of modules, including Android apps (when a Java library is actually a 'test'
  // module for the Android app.)
  override fun canDependOn(module: PsModule): Boolean = true

  fun forEachDeclaredDependency(consumer: (PsJavaDependency) -> Unit) {
    dependencies.forEachDeclaredDependency(consumer)
  }

  fun forEachDependency(consumer: (PsJavaDependency) -> Unit) {
    dependencies.forEach(consumer)
  }

  override fun resetDependencies() {
    myDependencyCollection = null
  }

  override fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency> =
    throw UnsupportedOperationException()
}
