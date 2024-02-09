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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.run.deployment.liveedit.analysis.FunctionKeyMeta
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrMethod
import com.android.tools.idea.run.deployment.liveedit.analysis.parseKeyMeta
import com.intellij.psi.PsiDocumentManager
import org.jetbrains.kotlin.psi.KtFile
import kotlin.math.max
import kotlin.math.min

private val debug = LiveEditLogger("ComposeGroups")

class ComposeGroup(keyMeta: FunctionKeyMeta, val lines: IntRange) {
  val key = keyMeta.key
  val range = keyMeta.range
}

fun extractComposeGroups(sourceFile: KtFile, keyMetaClass: IrClass): List<ComposeGroup> {
  debug.log("parsing key meta file:")
  return parseKeyMeta(keyMetaClass).map { group ->
    val doc = PsiDocumentManager.getInstance(sourceFile.project).getDocument(sourceFile)!!
    val startLine = doc.getLineNumber(group.range.startOffset)
    val endLine = doc.getLineNumber(group.range.endOffset)

    // Lines in the class file are 1-indexed, whereas lines in the document are 0-indexed. The IDE
    // presents them as 1-indexed, so we maintain that convention.
    val lines = IntRange(startLine + 1, endLine + 1)
    debug.log("\tgroup ${group.key} - lines $lines")

    ComposeGroup(group, lines)
  }
}

fun selectComposeGroups(sourceFile: KtFile, groups: List<ComposeGroup>, changedMethods: List<IrMethod>): List<ComposeGroup> {
  val doc = PsiDocumentManager.getInstance(sourceFile.project).getDocument(sourceFile)!!
  return changedMethods.mapNotNull { changedMethod ->
    val lines = changedMethod.instructions.lines

    // If the method doesn't directly correspond to any lines in the file, it can't be associated with a Compose group.
    if (lines.isEmpty()) {
      return@mapNotNull null
    }

    var min = Int.MAX_VALUE
    var max = Int.MIN_VALUE
    for (line in changedMethod.instructions.lines) {
      // Inlined methods are mapped to a line range past the end of the file; ignore those for group selection.
      if (line <= doc.lineCount) {
        min = min(min, line)
        max = max(max, line)
      }
    }

    val bestGroup = selectComposeGroup(groups, IntRange(min, max))
    if (bestGroup != null) {
      debug.log("group for ${changedMethod.name}${changedMethod.desc}\n\ton lines [$min, $max]\n\t${bestGroup.key} on lines ${bestGroup.lines}")
    }
    return@mapNotNull bestGroup
  }
}

fun selectComposeGroup(groups: List<ComposeGroup>, lineRange: IntRange) = groups.filter {
  // Ensure all lines are contained within the group
  lineRange.first in it.lines && lineRange.last in it.lines
}.minByOrNull {
  // Choose the smallest group that includes all relevant lines
  it.lines.last - it.lines.first
}
