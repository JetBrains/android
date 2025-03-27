/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.apk.viewer.gradle

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.apk.viewer.ApkAnalyzerToken
import com.android.tools.idea.gradle.model.IdeAndroidProjectType
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.gradle.util.OutputType
import com.android.tools.idea.gradle.util.getOutputFilesFromListingFile
import com.android.tools.idea.gradle.util.getOutputListingFile
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import kotlin.sequences.orEmpty

/*
 * Copyright (C) 2025 The Android Open Source Project
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
class ApkAnalyzerGradleToken : ApkAnalyzerToken<GradleProjectSystem>, GradleToken {
  override fun getDefaultApkToAnalyze(projectSystem: GradleProjectSystem): VirtualFile? {
    return ModuleManager.getInstance(projectSystem.project).modules.asSequence()
      .mapNotNull { GradleAndroidModel.get(it) }
      .filter { it.androidProject.projectType == IdeAndroidProjectType.PROJECT_TYPE_APP }
      .filter { AgpVersion.parse(it.androidProject.agpVersion) >= AgpVersion.parse("4.1.0") } // b/191146142
      .flatMap { androidModel ->
        if (androidModel.features.isBuildOutputFileSupported) {
          androidModel
            .selectedVariant
            .mainArtifact
            .buildInformation
            .getOutputListingFile(OutputType.Apk)
            ?.let { getOutputFilesFromListingFile(it) }
            ?.asSequence()
            .orEmpty()
        }
        else {
          emptySequence()
        }
      }
      .filterNotNull()
      .find { it.exists() }
      ?.let { VfsUtil.findFileByIoFile(it, true) }
  }

}