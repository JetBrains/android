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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.structure.model.PsArtifactDependencySpec
import com.android.tools.idea.gradle.structure.model.PsChildModel
import com.android.tools.idea.gradle.structure.model.PsModel
import com.intellij.util.PlatformIcons
import javax.swing.Icon

/**
 * A description of a reason why a dependency is included in a build.
 */
sealed class ReverseDependency : PsChildModel() {
  /**
   * The requested spec of the library whose presence in the build is justified by the reverse dependency.
   */
  abstract val spec: PsArtifactDependencySpec
  abstract val resolvedSpec: PsArtifactDependencySpec
  val isPromoted: Boolean get() = resolvedSpec > spec

  override val isDeclared: Boolean = true
  override val parent: PsModel? = null

  class Declared(
    override val resolvedSpec: PsArtifactDependencySpec,
    val dependency: PsDeclaredLibraryAndroidDependency
  ) : ReverseDependency() {
    override val spec: PsArtifactDependencySpec = dependency.spec
    override val name: String = spec.toString()
  }

  class Transitive(
    override val resolvedSpec: PsArtifactDependencySpec,
    val requestingResolvedDependency: PsResolvedLibraryAndroidDependency,
    override val spec: PsArtifactDependencySpec
  ) : ReverseDependency() {
    override val name: String = requestingResolvedDependency.spec.toString()
    override val icon: Icon? = PlatformIcons.LIBRARY_ICON
  }
}