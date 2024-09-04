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
package com.android.tools.idea.insights.ui

import com.intellij.openapi.util.text.HtmlBuilder
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlNodeRendererFactory
import org.commonmark.renderer.html.HtmlRenderer

// This code was duplicated from Gemini plugin's MarkdownConverter.

/**
 * Converts MarkDown text to corresponding HTML representation.
 *
 * Replaces [BreakMarkerInserter.BREAK_MARKER] with <wbr> for easier word/line wrapping.
 */
class MarkDownConverter(nodeRendererFactory: HtmlNodeRendererFactory) {

  private val parser = Parser.builder().build()

  private val renderer =
    HtmlRenderer.builder().escapeHtml(true).nodeRendererFactory(nodeRendererFactory).build()

  fun toHtml(markdown: String): String {
    val node = parser.parse(markdown)
    val html = renderer.render(node)
    return HtmlBuilder()
      .appendRaw(html)
      .wrapWithHtmlBody()
      .toString()
      .replace(BreakMarkerInserter.BREAK_MARKER, "<wbr>")
  }
}
