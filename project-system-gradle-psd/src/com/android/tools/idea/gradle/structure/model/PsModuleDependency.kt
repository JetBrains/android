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

import com.android.ide.common.gradle.model.IdeArtifactName
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule
import com.android.tools.idea.gradle.structure.model.android.PsResolvedModuleAndroidDependency
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule
import com.android.tools.idea.gradle.structure.navigation.PsModuleDependencyNavigationPath

interface PsModuleDependency : PsBaseDependency {
  val gradlePath: String
  override val path: PsPath get() = PsModuleDependencyNavigationPath(this)
}

interface PsDeclaredModuleDependency: PsDeclaredDependency, PsModuleDependency {
  override fun toKey() = gradlePath
}

interface PsResolvedModuleDependency: PsResolvedDependency, PsModuleDependency

val PsResolvedModuleDependency.targetModuleResolvedDependencies: PsDependencyCollection<*, *, *, *>?
  get() {
    val targetModule = parent.parent.findModuleByGradlePath(gradlePath)
    return when (targetModule) {
      is PsAndroidModule ->
        targetModule
          .resolvedVariants
          .firstOrNull { it.name == (this as? PsResolvedModuleAndroidDependency)?.moduleVariant }
          ?.findArtifact(IdeArtifactName.MAIN)
          ?.dependencies
      is PsJavaModule -> targetModule.resolvedDependencies
      else -> throw AssertionError()
    }
  }
