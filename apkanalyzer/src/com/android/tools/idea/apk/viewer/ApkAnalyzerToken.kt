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
package com.android.tools.idea.apk.viewer

import com.android.tools.idea.projectsystem.AndroidProjectSystem
import com.android.tools.idea.projectsystem.Token
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.getTokenOrNull
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

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
interface ApkAnalyzerToken<P: AndroidProjectSystem> : Token {
  fun getDefaultApkToAnalyze(projectSystem: P): VirtualFile?

  companion object {
    val EP_NAME =
      ExtensionPointName<ApkAnalyzerToken<AndroidProjectSystem>>(
        "com.android.tools.idea.apk.viewer.apkAnalyzerToken"
      )

    /**
     * Uses build-system-specific heuristics to locate a reasonable default APK file produced by the given project
     * for selecting when picking an APK to analyze.
     */
    @JvmStatic
    fun getDefaultApkToAnalyze(project: Project): VirtualFile? {
      val projectSystem = project.getProjectSystem()
      return projectSystem.getTokenOrNull(EP_NAME)?.getDefaultApkToAnalyze(projectSystem)
    }
  }
}