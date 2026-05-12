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

import com.android.annotations.concurrency.WorkerThread
import com.android.repository.Revision
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Loads the list of [WhatsNewMarkdownDocument] to be displayed by the "What's New" window. */
interface WhatsNewDocumentLoader {
  suspend fun loadDocuments(): List<WhatsNewMarkdownDocument>
}

internal class WhatsNewDocumentLoaderImpl : WhatsNewDocumentLoader {
  override suspend fun loadDocuments(): List<WhatsNewMarkdownDocument> {
    return withContext(Dispatchers.IO) {
      val documents = mutableListOf<Pair<Revision, String>>()

      this::class.java.getResourceAsStream("/v2/wna-markdown.zip")?.use { stream ->
        ZipInputStream(stream).use { zipStream ->
          while (true) {
            val entry = zipStream.nextEntry ?: break
            val fileName = entry.name

            if (fileName.endsWith(".md")) {
              val revisionStr = fileName.substringBeforeLast('.')
              val revision = runCatching { Revision.parseRevision(revisionStr) }.getOrNull()
              if (revision != null) {
                val content = zipStream.readBytes().toString(Charsets.UTF_8)
                documents.add(Pair(revision, content))
              }
            }
          }
        }
      } ?: throw IllegalArgumentException("Cannot load wna-markdown.zip")

      documents.sortedByDescending { (revision, _) -> revision }.map { (revision, content) -> loadMarkdownDocument(revision, content) }
    }
  }

  @WorkerThread
  private fun loadMarkdownDocument(revision: Revision, content: String): WhatsNewMarkdownDocument {
    return WhatsNewMarkdownParser.parseMarkdown(revision, content)
  }
}
