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
package com.android.tools.idea.apk.viewer.pagealign

import com.android.ide.common.pagealign.is16kAligned
import com.android.tools.apk.analyzer.ArchiveEntry
import com.android.tools.apk.analyzer.ArchiveNode
import com.android.tools.apk.analyzer.ZipEntryInfo.Alignment
import com.android.tools.idea.ndk.PageAlignConfig.isPageAlignMessageEnabled
import com.google.common.annotations.VisibleForTesting
import javax.swing.tree.TreePath

/**
 * Convenience shortcut to allow toggling this feature just for APK viewer
 */
@JvmField
val IS_PAGE_ALIGN_ENABLED = isPageAlignMessageEnabled()

/**
 * Information to display in the Alignment column.
 */
data class AlignmentFinding(
  val text : String,
  val hasWarning : Boolean)

/**
 * Give a human-readable page alignment.
 * If the value >= 1024L and is a multiple of 1024L then return in units of KB.
 * Otherwise, return in units of B.
 * In practice, bytes will be a power of 2 greater than 8 due to the way clang works.
 */
private fun getHumanReadablePageSize(sizeInBytes: Long): String {
  if (sizeInBytes == -1L) return ""
  if (sizeInBytes >= 1024L && (sizeInBytes % 1024L) == 0L) return "${sizeInBytes/1024} KB"
  return "$sizeInBytes B"
}

/**
 * Compute the value of the "Alignment" column.
 */
fun ArchiveEntry.getAlignmentFinding(extractNativeLibs: Boolean?) = getAlignmentFinding(
  "$path",
  extractNativeLibs,
  elfMinimumLoadSectionAlignment,
  selfOrChild16kbIncompatible,
  fileAlignment
)

@VisibleForTesting
fun getAlignmentFinding(
  path : String,
  extractNativeLibs : Boolean?,
  elfMinimumLoadSectionAlignment : Long,
  selfOrChildLoadSectionIncompatible : Boolean,
  zipAlignment : Alignment
) : AlignmentFinding {
  val sb = StringBuilder()
  var hasWarning = false
  val pageAlignText = getHumanReadablePageSize(elfMinimumLoadSectionAlignment)
  val zipAlignText = zipAlignment.text

  if (selfOrChildLoadSectionIncompatible) {
    val isLoadSectionAligned = is16kAligned(elfMinimumLoadSectionAlignment)
    val isZipAligned = zipAlignment != Alignment.ALIGNMENT_4K
    val warning = when {
        extractNativeLibs == true -> ""
        path == "/" -> "APK does not support 16 KB devices"
        elfMinimumLoadSectionAlignment == -1L -> ""
        !isLoadSectionAligned && !isZipAligned -> "$zipAlignText zip and $pageAlignText LOAD section, but 16 KB is required for both"
        !isLoadSectionAligned -> "$pageAlignText LOAD section alignment, but 16 KB is required"
        !isZipAligned -> "${zipAlignment.text} zip alignment, but 16 KB is required"
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
  return AlignmentFinding("$sb", hasWarning)
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