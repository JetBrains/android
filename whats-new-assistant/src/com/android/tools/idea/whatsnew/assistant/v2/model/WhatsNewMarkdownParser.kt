/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.whatsnew.assistant.v2.model

import com.android.repository.Revision

class WhatsNewMarkdownDocument(val productVersion: Revision, val shortName: String, val fullMarkdownContents: String)

object WhatsNewMarkdownParser {
  /** Parses the provided Markdown to extract the version name from the H1 tag, and returns all the info as a [WhatsNewMarkdownDocument]. */
  fun parseMarkdown(revision: Revision, markdown: String): WhatsNewMarkdownDocument {
    val h1Match = Regex("^#\\s+(.*)$", RegexOption.MULTILINE).find(markdown)
    val productName = h1Match?.groupValues?.get(1)?.trim() ?: "Android Studio $revision"
    val shortName = productName.removePrefix("What's New in Android Studio ").trim()
    return WhatsNewMarkdownDocument(productVersion = revision, shortName = shortName, fullMarkdownContents = markdown)
  }
}
