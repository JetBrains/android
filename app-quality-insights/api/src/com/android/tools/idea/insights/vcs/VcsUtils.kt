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

import com.android.tools.idea.insights.ABOVE_PROJECT_ROOT_PREFIX
import com.android.tools.idea.insights.PROJECT_ROOT_PREFIX
import com.android.tools.idea.insights.RepoInfo
import com.android.tools.idea.insights.VCS_CATEGORY
import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil

fun VirtualFile.getVcsManager(project: Project): AbstractVcs? {
  return ProjectLevelVcsManager.getInstance(project).getVcsFor(this)
}

/** Returns the first matched [Repository] for a given [RepoInfo]. */
fun RepoInfo.locateRepository(project: Project): Repository? {
  return VcsRepositoryManager.getInstance(project).repositories.firstOrNull { repoCandidate ->
    // 1. Check if vcs category is matching or not.
    if (VcsForAppInsights.getExtensionByKey(vcsKey)?.isApplicable(repoCandidate.vcs) != true)
      return@firstOrNull false

    // 2. Check vcs root path. TODO: update this once we expand to support multi-repo case.
    rootPath == PROJECT_ROOT_PREFIX || rootPath == ABOVE_PROJECT_ROOT_PREFIX
  }
}

fun VirtualFile.toVcsFilePath(): FilePath = VcsUtil.getFilePath(this)

fun createRevisionNumber(vcsKey: VCS_CATEGORY, revision: String): VcsRevisionNumber? {
  return VcsForAppInsights.getExtensionByKey(vcsKey)?.createVcsRevision(revision)
}

fun createShortRevisionString(vcsKey: VCS_CATEGORY, revision: String): String? {
  val revisionNumber = createRevisionNumber(vcsKey, revision) ?: return null

  return VcsUtil.getShortRevisionString(revisionNumber)
}

fun createVcsDocument(
  vcsKey: VCS_CATEGORY,
  virtualFile: VirtualFile,
  revision: String,
  project: Project
): Document? {
  // There's underlying cache layer: `ContentRevisionCache`.
  val vcsContentText =
    VcsForAppInsights.getExtensionByKey(vcsKey)
      ?.createVcsContent(virtualFile.toVcsFilePath(), revision, project)
      ?.content
      ?.let { StringUtilRt.convertLineSeparators(it, "\n") } ?: return null

  return DocumentImpl(vcsContentText)
}
