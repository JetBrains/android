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
package com.android.tools.idea.npw.templateengine.services

import com.android.testutils.TestUtils
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Rule
import org.junit.Test

class TemplateRegistryServiceTest {

  @get:Rule val projectRule = AndroidProjectRule.inMemory()

  @Test
  fun testTemplatesLoaded() {
    val zipFile = TestUtils.resolveWorkspacePath("tools/vendor/google/android/create/templates/android-project-templates.zip")
    val service = TemplateRegistryService.createForTest(zipPathProvider = { zipFile })
    assertThat(service).isNotNull()
    service.loadTemplatesAndResources()
    val templateDefinitions = service.getTemplateDefinitions()
    assertThat(templateDefinitions).isNotEmpty()

    // Verify we have loaded some expected templates
    val shortNames = templateDefinitions.map { it.metadata.shortName }
    assertThat(shortNames).contains("empty-activity")
  }

  @Test
  fun testFiltering() {
    var tempFile: Path? = null
    try {
      val jsonContent1 = """{"name": "Template 1", "short-name": "template-1", "tags": ["agp-9"]}"""
      val jsonContent2 = """{"name": "Template 2", "short-name": "template-2", "tags": ["gradle"]}"""
      val jsonContent3 = """{"name": "Template 3", "short-name": "template-3", "tags": ["agp-9", "other"]}"""
      val jsonContent4 = """{"name": "Template 4", "short-name": "template-4", "tags": ["lume"]}"""

      val zipBytes =
        createZipBytes(
          mapOf(
            "studio-template/.template/template-definition.json" to jsonContent1.toByteArray(),
            "cli-template/.template/template-definition.json" to jsonContent2.toByteArray(),
            "both-template/.template/template-definition.json" to jsonContent3.toByteArray(),
            "no-targets-template/.template/template-definition.json" to jsonContent4.toByteArray(),
          )
        )

      tempFile = Files.createTempFile("test_templates", ".zip")
      Files.write(tempFile, zipBytes)

      val customPath = tempFile
      val service = TemplateRegistryService.createForTest(zipPathProvider = { customPath })
      service.loadTemplatesAndResources()
      val templateDefinitions = service.getTemplateDefinitions()

      val names = templateDefinitions.map { it.metadata.shortName }
      assertThat(names).containsExactly("template-1", "template-2", "template-3")
    } finally {
      tempFile?.let { Files.deleteIfExists(it) }
    }
  }

  @Test
  fun testFailedInitialLoadAndSubsequentSuccessfulReload() {
    var shouldFail = true
    val zipFile = TestUtils.resolveWorkspacePath("tools/vendor/google/android/create/templates/android-project-templates.zip")
    val service =
      TemplateRegistryService.createForTest(
        zipPathProvider = {
          if (shouldFail) throw IllegalStateException("Templates not available yet")
          zipFile
        }
      )

    service.loadTemplatesAndResources()
    assertThat(service.getTemplateDefinitions()).isEmpty()
    assertThat(service.getLastErrorMessage()).contains("Templates not available yet")

    // Simulate package becoming installed / available
    shouldFail = false
    service.loadTemplatesAndResources()
    assertThat(service.getTemplateDefinitions()).isNotEmpty()
    assertThat(service.getLastErrorMessage()).isNull()
  }

  private fun createZipBytes(files: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zos ->
      for ((name, content) in files) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content)
        zos.closeEntry()
      }
    }
    return out.toByteArray()
  }
}
