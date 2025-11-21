/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.apk.viewer.pagealign

import com.android.ide.common.pagealign.AlignmentProblem
import com.android.ide.common.pagealign.AlignmentProblem.LoadSectionNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroEndNotAligned
import com.android.ide.common.pagealign.AlignmentProblem.RelroStartNotAligned
import com.android.ide.common.pagealign.PAGE_ALIGNMENT_16K
import com.android.ide.common.pagealign.getHumanReadablePageSize
import com.android.tools.apk.analyzer.ArchiveEntry
import com.android.tools.apk.analyzer.ArchiveNode
import com.android.tools.apk.analyzer.ZipEntryInfo.Alignment
import com.google.common.annotations.VisibleForTesting
import javax.swing.tree.TreePath

/**
 * Convenience shortcut to allow toggling this feature just for APK viewer.
 * Enabled by default, no server flag required.
 * To re-enable dependency on the server flag, set:
 * val IS_PAGE_ALIGN_ENABLED =
 * com.android.tools.idea.ndk.PageAlignConfig.isPageAlignMessageEnabled()
 */
const val IS_PAGE_ALIGN_ENABLED = true

/**
 * Information to display in the Alignment column.
 */
data class AlignmentFinding(
  val text : String,
  val hasWarning : Boolean,
  val problems: List<AlignmentProblem>?)

/**
 * Compute the value of the "Alignment" column.
 */
fun ArchiveEntry.getAlignmentFinding(extractNativeLibs: Boolean?) = getAlignmentFinding(
  "$path",
  extractNativeLibs ?: false,
  elfAlignmentProblems,
  selfOrChild16kbIncompatible,
  fileAlignment
)

@VisibleForTesting
fun getAlignmentFinding(
  path: String,
  extractNativeLibs: Boolean,
  elfAlignmentProblems: List<AlignmentProblem>?,
  selfOrChildLoadSectionIncompatible: Boolean,
  zipAlignment: Alignment
) : AlignmentFinding {
  val sb = StringBuilder()
  var hasWarning = false

  // Analyze specific problems from the list
  val loadSectionIssue = elfAlignmentProblems?.filterIsInstance<LoadSectionNotAligned>()?.firstOrNull()
  val relroStartIssue = elfAlignmentProblems?.filterIsInstance<RelroStartNotAligned>()?.firstOrNull()
  val relroEndIssue = elfAlignmentProblems?.filterIsInstance<RelroEndNotAligned>()?.firstOrNull()

  // Determine human-readable page alignment text.
  // If there is a LOAD problem, use the actual (bad) alignment found.
  // If there is no LOAD problem, but it is an ELF (list is not null), assume 16 KB.
  val pageAlignVal = loadSectionIssue?.ph?.align ?: PAGE_ALIGNMENT_16K
  val pageAlignText = if (elfAlignmentProblems != null) getHumanReadablePageSize(pageAlignVal) else ""

  val zipAlignText = zipAlignment.text

  if (selfOrChildLoadSectionIncompatible) {
    // If not 4K aligned, it is definitely not 16K aligned.
    // We maintain the existing heuristic: !isZipAligned triggers if alignment is 4K (or worse).
    val isZipAligned = zipAlignment != Alignment.ALIGNMENT_4K || extractNativeLibs

    val warning = when {
      // Don't say "APK" in this message because it could be AAB, ZIP, or even AAR.
      path == "/" -> "Does not support 16 KB devices"
      elfAlignmentProblems == null -> "" // Not a valid ELF context

      // Mixed Zip + Load failure
      loadSectionIssue != null && !isZipAligned -> "$zipAlignText zip and $pageAlignText LOAD section, but 16 KB is required for both"

      // Specific failures
      loadSectionIssue != null -> loadSectionIssue.toString()
      !isZipAligned -> "${zipAlignment.text} zip alignment, but 16 KB is required"

      // RELRO failures (checked after Load/Zip as they are less common blocking issues)
      (relroStartIssue != null) && (relroEndIssue != null) -> "RELRO start and end are not 16 KB aligned"
      relroStartIssue != null -> relroStartIssue.toString()
      relroEndIssue != null -> relroEndIssue.toString()

      else -> ""
    }
    if (warning.isNotEmpty()) {
      sb.append(warning)
      hasWarning = true
    }
  } else if (pageAlignText == zipAlignText || pageAlignText.isEmpty()) {
    // If there are no LOAD problems && zip alignment is the same as page alignment
    // then show that common value.
    sb.append(zipAlignText)
  } else if (zipAlignText.isNotEmpty()) {
    // If zip alignment is different from page alignment then show the distinct values
    sb.append("$zipAlignText zip|$pageAlignText LOAD section")
  }
  return AlignmentFinding("$sb", hasWarning, elfAlignmentProblems)
}

/**
 * Find paths with warns so that they can be expanded.
 */
fun findPageAlignWarningsPaths(root : ArchiveNode, extractNativeLibs : Boolean?): List<TreePath> {
  val expand = mutableListOf<TreePath>()
  fun findPageAlignWarningsPaths(path : TreePath, node : ArchiveNode) {
    val alignment = node.data.getAlignmentFinding(extractNativeLibs)
    if (alignment.hasWarning) expand.add(path)
    for(child in node.children) findPageAlignWarningsPaths(path.pathByAddingChild(child), child)
  }
  findPageAlignWarningsPaths(TreePath(root), root)
  return expand
}