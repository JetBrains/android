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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsNewDocumentLoaderTest {

  private val fakeFiles =
    mapOf(
      "2025.3.1.md" to "# Title\nVersion 2025.3.1 content",
      "2026.1.1.md" to "# Title\nVersion 2026.1.1 content",
      "2026.1.2.md" to "# Title\nVersion 2026.1.2 content",
      "2026.2.1.md" to "# Title\nVersion 2026.2.1 content",
    )

  private fun createFakeZip(files: Map<String, String>): InputStream {
    val byteArrayOutputStream = ByteArrayOutputStream()
    ZipOutputStream(byteArrayOutputStream).use { zipOut ->
      for ((fileName, content) in files) {
        zipOut.putNextEntry(ZipEntry(fileName))
        zipOut.write(content.toByteArray(Charsets.UTF_8))
        zipOut.closeEntry()
      }
    }
    return ByteArrayInputStream(byteArrayOutputStream.toByteArray())
  }

  @Test
  fun loadDocuments_filtersOutRevisionsNewerThanCurrentStudioVersion(): Unit = runBlocking {
    val currentVersion = Revision.parseRevision("2026.1.1")
    val loader =
      WhatsNewDocumentLoaderImpl(
        currentVersionSupplier = { currentVersion },
        zipStreamSupplier = { createFakeZip(fakeFiles) },
      )

    val documents = loader.loadDocuments()

    assertEquals(2, documents.size)
    assertEquals(Revision.parseRevision("2026.1.1"), documents[0].productVersion)
    assertEquals(Revision.parseRevision("2025.3.1"), documents[1].productVersion)
  }

  @Test
  fun loadDocuments_includesAllRevisionsWhenCurrentVersionIsUnspecified(): Unit = runBlocking {
    val loader =
      WhatsNewDocumentLoaderImpl(
        currentVersionSupplier = { Revision.NOT_SPECIFIED },
        zipStreamSupplier = { createFakeZip(fakeFiles) },
      )

    val documents = loader.loadDocuments()

    assertEquals(4, documents.size)
    assertEquals(Revision.parseRevision("2026.2.1"), documents[0].productVersion)
    assertEquals(Revision.parseRevision("2026.1.2"), documents[1].productVersion)
    assertEquals(Revision.parseRevision("2026.1.1"), documents[2].productVersion)
    assertEquals(Revision.parseRevision("2025.3.1"), documents[3].productVersion)
  }
}
