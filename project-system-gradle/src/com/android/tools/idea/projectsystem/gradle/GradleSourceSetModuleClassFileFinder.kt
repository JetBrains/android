/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.projectsystem.gradle

import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.projectsystem.ScopeType
import com.android.tools.idea.projectsystem.SourceSetModuleClassFileFinder
import com.android.tools.idea.rendering.classloading.loaders.JarManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ModificationTracker
import java.nio.file.Path
import java.util.EnumSet

class GradleSourceSetModuleClassFileFinder(module: Module, scope: CompileRootsScope) : SourceSetModuleClassFileFinder(module, scope) {

  override fun getModuleCompileOutputs(module: Module, scopes: EnumSet<ScopeType>): List<Path> =
    GradleClassFinderUtil.getModuleCompileOutputs(module, scopes).map { it.toPath() }.toList()

  override fun getModificationTracker(module: Module): ModificationTracker =
    GradleBuildState.getInstance(module.project).modificationTracker

  override fun validateModule(module: Module) {
    if (module.isLinkedAndroidModule() && module.isHolderModule()) {
      LOG.error("Using SourceSetModuleClassFileFinder with a holder module is basically never right.")
    }
  }

  override fun loadClassFileFromJar(project: Project, jarPath: Path, entryPath: String) =
    JarManager.getInstance(project).loadFileFromJar(jarPath, entryPath)

  companion object {
    private val LOG = Logger.getInstance(GradleSourceSetModuleClassFileFinder::class.java)
  }
}
