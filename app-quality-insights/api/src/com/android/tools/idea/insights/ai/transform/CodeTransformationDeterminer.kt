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
package com.android.tools.idea.insights.ai.transform

import com.android.tools.idea.insights.ai.codecontext.CodeContextResolver
import com.android.tools.idea.insights.client.FILE_PHRASE
import com.intellij.openapi.project.Project

interface CodeTransformationDeterminer {
  /** Determines the applicable transformation to be applied to the given insight [text] */
  suspend fun getApplicableTransformation(text: String): CodeTransformation
}

class CodeTransformationDeterminerImpl(
  private val project: Project,
  private val codeContextResolver: CodeContextResolver,
) : CodeTransformationDeterminer {
  override suspend fun getApplicableTransformation(text: String): CodeTransformation {
    if (text.isEmpty()) return NoopTransformation

    val hasFixIndex = text.indexOf(FILE_PHRASE)
    if (hasFixIndex == -1) {
      return NoopTransformation
    }
    val startIndex = hasFixIndex + FILE_PHRASE.length
    var i = startIndex
    val n = text.length
    while (i < n && text[i].isWhitespace() || text[i] == '`' || text[i] == '"' || text[i] == '\'') {
      i++
    }
    val fileName = buildString {
      while (i < n) {
        val c = text[i++]
        if (c.isLetterOrDigit() || c == '.' || c == '/') {
          append(c)
        } else if (c == '$') {
          append('/')
        } else if (c == '\\') {
          append('/')
        } else {
          break
        }
      }
    }
    val files = codeContextResolver.getSourceVirtualFiles(fileName.toString().removeSuffix("."))

    if (files.isEmpty()) return NoopTransformation
    return CodeTransformationImpl(project, text, files)
  }
}
