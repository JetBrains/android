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

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsModelCollection
import java.util.function.Consumer

internal class PsJavaDependencyCollection(private val parent: PsJavaModule) : PsModelCollection<PsJavaDependency> {

  private val libraryDependenciesBySpec = mutableMapOf<String, PsLibraryJavaDependency>()

  init {
    addDependencies()
  }

  private fun addDependencies() {
    val parsedDependencies = parent.parsedDependencies

    val gradleModel = parent.resolvedModel
    gradleModel?.jarLibraryDependencies?.forEach { libraryDependency ->
      val moduleVersion = libraryDependency.moduleVersion
      if (moduleVersion != null) {
        val spec = PsArtifactDependencySpec.create(moduleVersion)
        val parsed = parsedDependencies.findLibraryDependencies(moduleVersion.group, moduleVersion.name)
        if (!parsed.isEmpty()) {
          val dependency = PsLibraryJavaDependency(parent, spec, libraryDependency, parsed.first())
          libraryDependenciesBySpec[spec.toString()] = dependency
        }
      }
    }
  }

  override fun forEach(consumer: Consumer<PsJavaDependency>) {
    forEachDependency(libraryDependenciesBySpec, consumer)
  }

  private fun forEachDependency(dependenciesBySpec: Map<String, PsJavaDependency>,
                                consumer: Consumer<PsJavaDependency>) {
    dependenciesBySpec.values.forEach(consumer)
  }

  fun forEachDeclaredDependency(consumer: (PsJavaDependency) -> Unit) {
    forEachDeclaredDependency(libraryDependenciesBySpec, consumer)
  }

  private fun forEachDeclaredDependency(dependenciesBySpec: Map<String, PsJavaDependency>, consumer: (PsJavaDependency) -> Unit) {
    dependenciesBySpec.values.filter { it.isDeclared }.forEach(consumer)
  }
}
