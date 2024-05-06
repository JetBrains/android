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
package com.android.tools.idea.insights.inspection

import com.android.tools.idea.insights.AppInsight
import com.android.tools.idea.insights.AppVcsInfo
import com.android.tools.idea.insights.vcs.createVcsDocument
import com.android.tools.idea.insights.vcs.getVcsManager
import com.android.tools.idea.insights.vcs.locateRepository
import com.intellij.diff.comparison.iterables.FairDiffIterable
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.util.Range
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ex.compareLines
import com.intellij.openapi.vfs.VirtualFile

fun AppInsight.tryCreateVcsDocumentOrNull(contextVFile: VirtualFile, project: Project): Document? {
  return try {
    createVcsDocument(contextVFile, project)
  } catch (exception: VcsException) {
    thisLogger().warn("Error when creating VCS document from $this for $contextVFile: $exception.")
    null
  }
}

fun AppInsight.createVcsDocument(contextVFile: VirtualFile, project: Project): Document? {
  val appVcsInfo = issue.sampleEvent.appVcsInfo as? AppVcsInfo.ValidInfo ?: return null

  val associatedVcs = contextVFile.getVcsManager(project) ?: return null
  val matchedRepoInfo =
    appVcsInfo.repoInfo.firstOrNull { it.locateRepository(project)?.vcs == associatedVcs }
      ?: return null

  return createVcsDocument(matchedRepoInfo.vcsKey, contextVFile, matchedRepoInfo.revision, project)
}

/**
 * Returns a mapped line number in the current [document] for a given [oldLineNumber] in the
 * historical [vcsDocument].
 *
 * This is achieved by calculating the unchanged line shifts from diffing [vcsDocument] and
 * [document].
 */
fun getUpToDateLineNumber(oldLineNumber: Int, vcsDocument: Document, document: Document): Int? {
  val diffIterator: FairDiffIterable =
    compareLines(
      vcsDocument.immutableCharSequence,
      document.immutableCharSequence,
      LineOffsetsUtil.create(vcsDocument),
      LineOffsetsUtil.create(document),
    )

  return diffIterator.iterateChanges().toList().inferLineNumber(oldLineNumber)
}

/**
 * Returns a mapped line number by calculating line shifts from those changed blocks/ranges or null
 */
fun List<Range>.inferLineNumber(lineNumber: Int): Int? {
  var mappedLineNumber = lineNumber

  onEach {
    if (lineNumber < it.start1) return mappedLineNumber else if (lineNumber < it.end1) return null

    val delta1 = it.end1 - it.start1
    val delta2 = it.end2 - it.start2
    mappedLineNumber = mappedLineNumber + delta2 - delta1
  }

  return mappedLineNumber
}
