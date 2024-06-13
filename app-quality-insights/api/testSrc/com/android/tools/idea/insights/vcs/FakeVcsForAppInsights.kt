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
package com.android.tools.idea.insights.vcs

import com.android.tools.idea.insights.VCS_CATEGORY
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.changes.committed.MockAbstractVcs
import com.intellij.openapi.vcs.history.VcsRevisionNumber

class FakeVcsForAppInsights : VcsForAppInsights {
  override val key: VCS_CATEGORY = VCS_CATEGORY.TEST_VCS

  override fun isApplicable(vcs: AbstractVcs) = vcs is MockAbstractVcs

  override fun createVcsContent(
    localFilePath: FilePath,
    revision: String,
    project: Project,
  ): ContentRevision {
    return FakeContentRevision(localFilePath, revision) { vcsContentProvider(localFilePath) }
  }

  override fun createVcsRevision(revision: String): VcsRevisionNumber =
    FakeVcsRevisionNumber(revision)

  var vcsContentProvider = { filePath: FilePath -> FileUtil.loadFile(filePath.ioFile) }
}
