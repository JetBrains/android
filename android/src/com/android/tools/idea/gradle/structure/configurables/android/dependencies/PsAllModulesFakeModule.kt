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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies

import com.android.tools.idea.gradle.structure.model.*
import java.io.File

open class PsAllModulesFakeModule(override val parent: PsProject) : PsModule(parent, "<All Modules>") {

  override val rootDir: File? = null

  override var isModified: Boolean
    get() = parent.isModified
    set(value) = throw UnsupportedOperationException()

  override fun applyChanges() = parent.applyChanges()

  override fun getConfigurations(): List<String> = throw UnsupportedOperationException()

  override fun addLibraryDependency(library: String, scopesNames: List<String>) = throw UnsupportedOperationException()

  override fun addModuleDependency(modulePath: String, scopesNames: List<String>) = throw UnsupportedOperationException()

  override fun removeDependency(dependency: PsDeclaredDependency) = throw UnsupportedOperationException()

  override fun setLibraryDependencyVersion(spec: PsArtifactDependencySpec,
                                           configurationName: String,
                                           newVersion: String) = throw UnsupportedOperationException()
}
