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

import org.commonmark.node.Code
import org.commonmark.node.Image
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.renderer.html.CoreHtmlNodeRenderer
import org.commonmark.renderer.html.HtmlNodeRendererContext

/** Custom [CoreHtmlNodeRenderer] that renders [Image], [Text] and [Code] [Node]s. */
class AqiHtmlRenderer(context: HtmlNodeRendererContext) : CoreHtmlNodeRenderer(context) {
  override fun getNodeTypes() = setOf(Image::class.java, Text::class.java, Code::class.java)

  override fun render(node: Node) {
    var title: String? = null
    when (node) {
      is Image -> {
        title = node.title
      }
      is Text -> {
        title = BreakMarkerInserter.insertBreakMarkersInLongTokens(node.literal)
      }
      is Code -> {
        context.writer.tag("code", context.extendAttributes(node, "code", mutableMapOf()))
        context.writer.text(BreakMarkerInserter.insertBreakMarkersInLongTokens(node.literal))
        context.writer.tag("/code")
      }
    }

    if (title != null) {
      context.writer.text(title)
    }

    visitChildren(node)
  }
}
