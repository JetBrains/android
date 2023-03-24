/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.insights.enhance

import com.android.tools.idea.insights.VCS_CATEGORY
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision

interface VcsForAppInsights {
  val key: VCS_CATEGORY

  fun isApplicable(vcs: AbstractVcs): Boolean

  fun createVcsContent(
    vcsKey: VCS_CATEGORY,
    localFilePath: FilePath,
    revision: String,
    project: Project
  ): ContentRevision

  fun getShortRevisionFromString(vcsKey: VCS_CATEGORY, revision: String): String

  companion object {
    @JvmField
    val EP_NAME =
      ExtensionPointName<VcsForAppInsights>(
        "com.android.tools.idea.insights.enhance.vcsForAppInsights"
      )

    fun getExtensionByKey(key: VCS_CATEGORY): VcsForAppInsights? {
      return EP_NAME.extensionList.firstOrNull { it.key == key }
    }
  }
}
