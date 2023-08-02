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
package com.android.tools.idea.gradle.util

import com.intellij.psi.PsiElement
import com.intellij.psi.util.findParentOfType
import org.toml.lang.psi.TomlHeaderOwner
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlTableHeader

// returns path from root to a given TOML Segment
fun generateExistingPath(psiElement: TomlKeySegment): List<String> {
  /**
   * Segment can be at multiple points in toml file.
   * 1) In table header
   * - TomlTable or TomlArrayTable
   * -- TomlTableHeader
   * --- TomlKey
   * ---- List<TomlKeySegment>
   * 2) In key as part of table content. This can be recursive as a.b = { c.d = "..." }
   * - TomlKeyValue
   * -- TomlKey
   * --- List<TomlKeySegment>
   * -- TomlValue
   */
  val result = mutableListOf<String>()
  var key: TomlKey?
  var nextElement: PsiElement = psiElement
  if (psiElement.parent.parent !is TomlTableHeader) {
    do {
      // bubble up via inline tables to root/file
      key = nextElement.findParentOfType<TomlKey>()
      if (key != null) {
        nextElement = key
        result.appendReversedSegments(key, psiElement)
      }
    }
    while (key != null)
  }
  val parentTableHeaderKey = nextElement.findParentOfType<TomlHeaderOwner>()?.header?.key
  result.appendReversedSegments(parentTableHeaderKey, psiElement)
  return result.reversed()
}

private fun MutableList<String>.appendReversedSegments(key: TomlKey?, startElement: PsiElement) {
  key?.segments?.reversed()?.forEach { segment -> if (segment != startElement) this += segment.text }
}