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
import com.android.tools.idea.gradle.structure.model.PsDependency
import com.android.tools.idea.gradle.structure.model.PsModuleDependency
import com.android.tools.idea.gradle.util.GradleUtil.getModuleIcon
import com.intellij.openapi.module.Module
import icons.StudioIcons.Shell.Filetree.ANDROID_MODULE
import javax.swing.Icon

class PsModuleAndroidDependency internal constructor(
  parent: PsAndroidModule,
  override val gradlePath: String,
  artifacts: Collection<PsAndroidArtifact>,
  override val configurationName: String?,
  override val resolvedModel: Module?,
  parsedModels: Collection<ModuleDependencyModel>
) : PsAndroidDependency(parent, artifacts, parsedModels), PsModuleDependency {

  override val name: String = resolvedModel?.name ?: parsedModels.firstOrNull()?.name()!!

  override val icon: Icon?
    get() = if (resolvedModel != null) {
      getModuleIcon(resolvedModel)
    }
    else ANDROID_MODULE

  override fun addParsedModel(parsedModel: DependencyModel) {
    assert(parsedModel is ModuleDependencyModel)
    super.addParsedModel(parsedModel)
  }

  override fun toText(type: PsDependency.TextType): String = name

  fun findReferredArtifact(): PsAndroidArtifact? {
    val referred = parent.parent.findModuleByGradlePath(gradlePath)
    val moduleVariantName = configurationName
    if (moduleVariantName != null && referred is PsAndroidModule) {
      val moduleVariant = referred.findVariant(moduleVariantName)
      if (moduleVariant != null) {
        return moduleVariant.findArtifact(ARTIFACT_MAIN)
      }
    }
    return null
  }
}
