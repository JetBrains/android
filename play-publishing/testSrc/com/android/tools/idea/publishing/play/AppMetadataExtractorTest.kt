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
package com.android.tools.idea.publishing.play

import com.android.tools.apk.analyzer.Archives
import com.google.common.truth.Truth.assertThat
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AppMetadataExtractorTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun testIsSigned_noMetaInf_returnsFalse() {
    val zipFile = temporaryFolder.newFile("test_no_meta_inf.zip").toPath()
    createZip(zipFile, mapOf("some_file.txt" to "hello".toByteArray()))

    Archives.open(zipFile).use { archiveContext ->
      val archive = archiveContext.archive
      assertThat(isSigned(archive)).isFalse()
    }
  }

  @Test
  fun testIsSigned_emptyMetaInf_returnsFalse() {
    val zipFile = temporaryFolder.newFile("test_empty_meta_inf.zip").toPath()
    createZip(zipFile, mapOf("META-INF/" to null))

    Archives.open(zipFile).use { archiveContext ->
      val archive = archiveContext.archive
      assertThat(isSigned(archive)).isFalse()
    }
  }

  @Test
  fun testIsSigned_hasSigningFile_returnsTrue() {
    val zipFile = temporaryFolder.newFile("test_signed_rsa.zip").toPath()
    createZip(zipFile, mapOf("META-INF/" to null, "META-INF/CERT.RSA" to "dummy_signature".toByteArray()))

    Archives.open(zipFile).use { archiveContext ->
      val archive = archiveContext.archive
      assertThat(isSigned(archive)).isTrue()
    }
  }

  @Test
  fun testIsSigned_hasNonSigningFile_returnsFalse() {
    val zipFile = temporaryFolder.newFile("test_not_signed.zip").toPath()
    createZip(zipFile, mapOf("META-INF/" to null, "META-INF/MANIFEST.MF" to "manifest info".toByteArray()))

    Archives.open(zipFile).use { archiveContext ->
      val archive = archiveContext.archive
      assertThat(isSigned(archive)).isFalse()
    }
  }

  @Test
  fun testIsSigned_directoryWithSigningExtension_returnsFalse() {
    val zipFile = temporaryFolder.newFile("test_dir_as_signing_ext.zip").toPath()
    createZip(zipFile, mapOf("META-INF/" to null, "META-INF/some_dir.rsa/" to null))

    Archives.open(zipFile).use { archiveContext ->
      val archive = archiveContext.archive
      assertThat(isSigned(archive)).isFalse()
    }
  }

  private fun createZip(path: Path, entries: Map<String, ByteArray?>) {
    ZipOutputStream(FileOutputStream(path.toFile())).use { zos ->
      for ((name, content) in entries) {
        val entry = ZipEntry(name)
        zos.putNextEntry(entry)
        if (content != null) {
          zos.write(content)
        }
        zos.closeEntry()
      }
    }
  }
}
