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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.*
import com.intellij.util.PlatformIcons.LIBRARY_ICON
import javax.swing.Icon

class PsDeclaredModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>,
  override val parsedModel: ModuleDependencyModel
) : PsModuleAndroidDependency(
  parent, gradlePath, artifacts
), PsDeclaredModuleDependency {
  override val name: String get() = parsedModel.name()
  override val isDeclared: Boolean = true
  override val configurationName: String get() = parsedModel.configurationName()
  override val joinedConfigurationNames: String get() = configurationName
}

class PsResolvedModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  gradlePath: String,
  val artifact: PsAndroidArtifact,
  internal val moduleVariant: String?,
  targetModule: PsModule,
  override val declaredDependencies: List<PsDeclaredDependency>
) : PsModuleAndroidDependency(
  parent, gradlePath, listOf(artifact)
), PsResolvedModuleDependency {
  override val name: String = targetModule.name
  override val isDeclared: Boolean get() = !declaredDependencies.isEmpty()
}

abstract class PsModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  final override val gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>
) : PsAndroidDependency(parent, artifacts), PsModuleDependency {
  override fun toText(): String = name
  override val icon: Icon = parent.parent.findModuleByGradlePath(gradlePath)?.icon ?: LIBRARY_ICON
}
