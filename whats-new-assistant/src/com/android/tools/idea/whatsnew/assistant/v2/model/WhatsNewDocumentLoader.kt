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
import com.intellij.openapi.application.ApplicationInfo
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** @property assetId Arbitrary identifier used for fast equals/hashCode in recomposition. */
data class WhatsNewAssets(val assetId: String = "empty") {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as WhatsNewAssets

    return assetId == other.assetId
  }

  override fun hashCode(): Int {
    return assetId.hashCode()
  }
}

/** Loads the list of [WhatsNewMarkdownDocument] to be displayed by the "What's New" window. */
interface WhatsNewDocumentLoader {
  suspend fun loadDocuments(): List<WhatsNewMarkdownDocument>

  suspend fun loadAssets(): WhatsNewAssets
}

class WhatsNewDocumentLoaderImpl(
  private val currentVersionSupplier: () -> Revision = {
    runCatching { Revision.safeParseRevision(ApplicationInfo.getInstance().strictVersion) }.getOrDefault(Revision.NOT_SPECIFIED)
  },
  private val zipStreamSupplier: () -> InputStream? = {
    WhatsNewDocumentLoaderImpl::class.java.getResourceAsStream("/whats-new.zip")
  },
) : WhatsNewDocumentLoader {

  override suspend fun loadDocuments(): List<WhatsNewMarkdownDocument> {
    val currentVersion = currentVersionSupplier()
    return withContext(Dispatchers.IO) {
      val documents = mutableListOf<Pair<Revision, String>>()

      val zipStream = zipStreamSupplier()

      zipStream?.use { stream ->
        ZipInputStream(stream).use { zipStream ->
          while (true) {
            val entry = zipStream.nextEntry ?: break
            val fileName = entry.name

            if (fileName.endsWith(".md")) {
              val revisionStr = fileName.substringAfterLast('/').substringBeforeLast('.')
              val revision = runCatching { Revision.parseRevision(revisionStr) }.getOrNull()
              if (revision != null && (currentVersion == Revision.NOT_SPECIFIED || revision <= currentVersion)) {
                val content = zipStream.readBytes().toString(Charsets.UTF_8)
                documents.add(Pair(revision, content))
              }
            }
          }
        }
      } ?: throw IllegalArgumentException("Cannot load whats-new.zip")

      documents.sortedByDescending { (revision, _) -> revision }.map { (revision, content) -> loadMarkdownDocument(revision, content) }
    }
  }

  override suspend fun loadAssets(): WhatsNewAssets {
    return WhatsNewAssets("loaded")
  }

  @WorkerThread
  private fun loadMarkdownDocument(revision: Revision, content: String): WhatsNewMarkdownDocument {
    return WhatsNewMarkdownParser.parseMarkdown(revision, content)
  }
}
