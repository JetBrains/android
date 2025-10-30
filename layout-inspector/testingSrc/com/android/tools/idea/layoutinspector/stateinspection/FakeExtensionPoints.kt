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
package com.android.tools.idea.layoutinspector.stateinspection

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ExtensionTestUtil

private const val EXPLAIN_WITH_AI = "(Explain with AI)"

/** Installs 2 EP for state inspection that simulates the presence of ML. */
fun installFakeExtensionPoints(disposable: Disposable) {
  val hyperLinkInfo =
    object : HyperlinkInfo, LayoutInspectorExplainWithAIHyperLinkInfo {
      override fun navigate(project: Project) {}
    }
  val provider = FakeFilterProvider(hyperLinkInfo)
  ExtensionTestUtil.maskExtensions(
    LayoutInspectorStateInspectionFilterProvider.EP_NAME,
    listOf(provider),
    disposable,
  )
  val rewriter =
    object : LayoutInspectorStateReadRewriter {
      override fun rewriteStateRead(project: Project, read: String): String {
        return "$read $EXPLAIN_WITH_AI"
      }
    }
  ExtensionTestUtil.maskExtensions(
    LayoutInspectorStateReadRewriter.EP_NAME,
    listOf(rewriter),
    disposable,
  )
}

/**
 * [LayoutInspectorStateInspectionFilterProvider] that adds a (Ask Gemini) text to each state read.
 */
private class FakeFilterProvider(private val link: HyperlinkInfo) :
  LayoutInspectorStateInspectionFilterProvider {
  override fun create(editor: EditorEx): Filter {
    return FilterForTest(link)
  }
}

private class FilterForTest(private val link: HyperlinkInfo) : Filter {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val offset = entireLength - line.length
    val start = line.indexOf(EXPLAIN_WITH_AI)
    if (start < 0) {
      return null
    }

    val end = start + EXPLAIN_WITH_AI.length
    val item = Filter.ResultItem(start + offset, end + offset, link)
    return Filter.Result(listOf(item))
  }
}
