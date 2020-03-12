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

open class PsAllModulesFakeModule(override val parent: PsProject) : PsModule("<All Modules>", ModuleKind.FAKE, parent) {
  override val descriptor get() = PsModelDescriptor.None
  override val dependencies: PsDependencyCollection<PsModule, Nothing, Nothing, Nothing> get() = throw UnsupportedOperationException()
  override val projectType: PsModuleType = PsModuleType.UNKNOWN
  override val gradlePath: String? = null
  override val rootDir: File? = null

  override var isModified: Boolean
    get() = parent.isModified
    set(_) = throw UnsupportedOperationException()

  override fun applyChanges() = parent.applyChanges()

  override fun getConfigurations(onlyImportantFor: ImportantFor?): List<String> = throw UnsupportedOperationException()

  override fun resetDependencies() = throw UnsupportedOperationException()

  override fun findLibraryDependencies(group: String?, name: String): List<PsDeclaredLibraryDependency> =
    throw UnsupportedOperationException()

  override fun maybeAddConfiguration(configurationName: String) = throw UnsupportedOperationException()
  override fun maybeRemoveConfiguration(configurationName: String) = throw UnsupportedOperationException()
}
