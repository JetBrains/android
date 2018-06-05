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

import com.android.builder.model.AndroidProject.ARTIFACT_MAIN
import com.android.tools.idea.gradle.dsl.api.dependencies.DependencyModel
import com.android.tools.idea.gradle.dsl.api.dependencies.ModuleDependencyModel
import com.android.tools.idea.gradle.structure.model.*
import icons.StudioIcons.Shell.Filetree.ANDROID_MODULE
import javax.swing.Icon

class PsDeclaredModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>,
  configurationName: String,
  moduleVariant: String?,
  override val parsedModel: ModuleDependencyModel
) : PsModuleAndroidDependency(
  parent, gradlePath, artifacts, configurationName, moduleVariant
), PsDeclaredDependency {
  override val name: String = parsedModel.name()
  override val isDeclared: Boolean = true
  override val joinedConfigurationNames: String = configurationName
  override val icon: Icon? get() = ANDROID_MODULE
}

class PsResolvedModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>,
  configurationName: String,
  moduleVariant: String?,
  private val targetModule: PsModule,
  private val parsedModels: Collection<ModuleDependencyModel>
) : PsModuleAndroidDependency(
  parent, gradlePath, artifacts, configurationName, moduleVariant
), PsResolvedDependency {
  override val name: String = targetModule.name
  override val isDeclared: Boolean get() = !parsedModels.isEmpty()
  override val joinedConfigurationNames: String get() = parsedModels.joinToString(separator = ", ") { it.configurationName()}
  override fun getParsedModels(): List<DependencyModel> = parsedModels.toList()
  override val icon: Icon? get() = targetModule.icon
}

abstract class PsModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  override val gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>,
  override val configurationName: String,
  private val moduleVariant: String?
) : PsAndroidDependency(parent, artifacts), PsModuleDependency {

  override fun toText(type: PsDependency.TextType): String = name

  fun findReferredArtifact(): PsAndroidArtifact? {
    val referred = parent.parent.findModuleByGradlePath(gradlePath)
    if (moduleVariant != null && referred is PsAndroidModule) {
      val moduleVariant = referred.findVariant(moduleVariant)
      if (moduleVariant != null) {
        return moduleVariant.findArtifact(ARTIFACT_MAIN)
      }
    }
    return null
  }
}
