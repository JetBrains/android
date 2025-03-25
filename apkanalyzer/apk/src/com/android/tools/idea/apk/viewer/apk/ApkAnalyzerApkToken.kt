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
package com.android.tools.idea.apk.viewer.apk

import com.android.tools.idea.apk.ApkFacet
import com.android.tools.idea.apk.viewer.ApkAnalyzerToken
import com.android.tools.idea.projectsystem.apk.ApkProjectSystem
import com.android.tools.idea.projectsystem.apk.ApkToken
import com.android.tools.idea.util.toVirtualFile
import com.intellij.facet.ProjectFacetManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class ApkAnalyzerApkToken: ApkAnalyzerToken<ApkProjectSystem>, ApkToken {
  override fun getDefaultApkToAnalyze(projectSystem: ApkProjectSystem): VirtualFile? {
    return ProjectFacetManager.getInstance(projectSystem.project).getFacets(ApkFacet.getFacetTypeId())
      .firstNotNullOfOrNull { facet -> facet?.configuration?.APK_PATH?.let { File(it).toVirtualFile() } }
  }
}
