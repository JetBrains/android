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

import com.android.tools.idea.gradle.structure.model.meta.ModelSimpleProperty
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue
import com.android.tools.idea.gradle.structure.navigation.PsLibraryDependencyNavigationPath
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon

interface PsLibraryDependency : PsBaseDependency {
  override val parent: PsModule
  val spec: PsArtifactDependencySpec

  override val path: PsLibraryDependencyNavigationPath get() = PsLibraryDependencyNavigationPath(this)
  override val icon: Icon get() = LIBRARY_ICON
}

interface PsDeclaredLibraryDependency: PsLibraryDependency, PsDeclaredDependency {
  var version: ParsedValue<String>
  val versionProperty: ModelSimpleProperty<Unit, String>
  fun canExtractVariable(): Boolean
  override fun toKey() = spec.toString()
}

interface PsResolvedLibraryDependency : PsLibraryDependency, PsResolvedDependency {
  fun hasPromotedVersion(): Boolean
  fun getTransitiveDependencies(): Set<PsResolvedLibraryDependency> = setOf()
}
