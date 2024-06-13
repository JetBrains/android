/*
 * Copyright (C) 2021 The Android Open Source Project
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
@file:JvmName("MavenClassResolverUtils")

package org.jetbrains.android.dom.inspections

import com.android.support.AndroidxNameUtils
import com.android.tools.idea.imports.AndroidMavenImportFix
import com.android.tools.idea.imports.MavenClassRegistryManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import org.jetbrains.android.refactoring.isAndroidx

/**
 * Returns a collection of [LocalQuickFix] by querying
 * [MavenClassRegistryManager.getMavenClassRegistry].
 */
internal fun MavenClassRegistryManager.collectFixesFromMavenClassRegistry(
  className: String,
  project: Project,
  completionFileType: FileType?,
): List<LocalQuickFix> {
  val useAndroidX = project.isAndroidx()

  return getMavenClassRegistry()
    .findLibraryData(className, null, useAndroidX, completionFileType)
    .map {
      val resolvedArtifact =
        if (useAndroidX) AndroidxNameUtils.getCoordinateMapping(it.artifact) else it.artifact
      AndroidMavenImportFix(className, resolvedArtifact, it.version)
    }
}
