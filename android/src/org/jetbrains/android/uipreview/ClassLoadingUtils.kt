/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.SdkConstants
import com.android.tools.idea.projectsystem.ProjectSyncModificationTracker
import com.android.tools.idea.projectsystem.getProjectSystem
import com.google.common.io.Files
import com.intellij.openapi.module.Module
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import java.io.File
import java.nio.file.Path

/**
 * Returns a stream of JAR files of the referenced libraries for the [Module] of this class loader.
 */
private fun Module.getExternalLibraryJars(): Sequence<File> {
  return project.getProjectSystem().getClassJarProvider().getModuleExternalLibraries(this).asSequence()
}

/**
 * Returns the list of [Path]s to external JAR files referenced by the class loader.
 */
fun Module?.getLibraryDependenciesJars(): List<Path> {
  if (this == null || this.isDisposed) {
    return emptyList()
  }
  return CachedValuesManager.getManager(project).getCachedValue(this) {
    val libraries = getExternalLibraryJars()
      .filter { file: File -> SdkConstants.EXT_JAR == Files.getFileExtension(file.name) && file.exists() }
      .mapNotNull { it.toPath() }
      .toList()
    CachedValueProvider.Result.create(libraries, ProjectSyncModificationTracker.getInstance(project))
  }
}