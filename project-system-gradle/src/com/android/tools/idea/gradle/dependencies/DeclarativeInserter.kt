/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dependencies

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.utils.addIfNotNull

/**
 * We assume for now that declarative project is pure (no non-declarative modules)
 * and no version catalog in it.
 */
class DeclarativeInserter(private val projectModel: ProjectBuildModel): DependenciesInserter(projectModel) {

  override fun applySettingsPlugin(pluginId: String,
                               version: String): Set<PsiFile> {
    val changedFiles = mutableSetOf<PsiFile>()
    val settingsFile = projectModel.declarativeSettingsModel
    if (settingsFile == null)
      log.warn("Settings file does not exist so cannot insert declaration into plugin{} block")

    settingsFile?.plugins()?.let {
      it.applyPlugin(pluginId, version)
      changedFiles.addIfNotNull(projectModel.projectSettingsModel?.psiFile)
    }

    return changedFiles
  }
}