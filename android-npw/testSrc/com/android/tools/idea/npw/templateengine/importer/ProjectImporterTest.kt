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
package com.android.tools.idea.npw.templateengine.importer

import com.android.tools.idea.npw.templateengine.ui.TemplateEngineTestUtils.createTestTemplateMetadata
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProjectImporterTest {

  @Test
  fun testCreateImporter_gradleTag() {
    val metadata = createTestTemplateMetadata(tags = listOf("gradle"))

    val importer = ProjectImporter.createImporter(metadata)
    assertThat(importer.javaClass.simpleName).isEqualTo("GradleImporter")
  }

  @Test
  fun testCreateImporter_agpTag() {
    val metadata = createTestTemplateMetadata(tags = listOf("agp"))

    val importer = ProjectImporter.createImporter(metadata)
    assertThat(importer.javaClass.simpleName).isEqualTo("GradleImporter")
  }

  @Test
  fun testCreateImporter_lightbuildTag() {
    val metadata = createTestTemplateMetadata(tags = listOf("lightbuild"))

    val importer = ProjectImporter.createImporter(metadata)
    assertThat(importer.javaClass.simpleName).isEqualTo("LightbuildImporter")
  }

  @Test
  fun testCreateImporter_lumeTag() {
    val metadata = createTestTemplateMetadata(tags = listOf("lume"))

    val importer = ProjectImporter.createImporter(metadata)
    assertThat(importer.javaClass.simpleName).isEqualTo("LightbuildImporter")
  }

  @Test(expected = IllegalArgumentException::class)
  fun testCreateImporter_noMatchingTag() {
    val metadata = createTestTemplateMetadata(tags = listOf("unknown"))
    ProjectImporter.createImporter(metadata)
  }
}
