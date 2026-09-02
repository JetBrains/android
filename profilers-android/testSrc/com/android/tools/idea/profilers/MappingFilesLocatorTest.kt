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
package com.android.tools.idea.profilers

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito

class MappingFilesLocatorTest {

  @get:Rule val temporaryFolder = TemporaryFolder()

  @Test
  fun getMappingsReturnsExistingFilesWithApplicationId() {
    val map1 = temporaryFolder.newFile("mapping1.txt")
    val map2 = temporaryFolder.newFile("mapping2.txt")
    val nonExistentMap = temporaryFolder.root.toPath().resolve("non_existent_mapping.txt")

    val dynamicSource =
      DynamicR8MappingSource()
        .add(text = map1.toPath(), applicationId = "com.example.app1")
        .add(text = map2.toPath(), applicationId = "com.example.app2")
        .add(text = nonExistentMap, applicationId = "com.example.app3")

    val locator = MappingFilesLocator(dynamicSource)
    val mappings = locator.getMappings()

    assertThat(mappings).hasSize(2)
    assertThat(mappings["com.example.app1"]).isEqualTo(map1.absolutePath)
    assertThat(mappings["com.example.app2"]).isEqualTo(map2.absolutePath)
    assertThat(mappings.containsKey("com.example.app3")).isFalse()
  }

  @Test
  fun getMappingsHandlesNullApplicationId() {
    val map = temporaryFolder.newFile("global_mapping.txt")

    val dynamicSource = DynamicR8MappingSource().add(text = map.toPath(), applicationId = null)

    val locator = MappingFilesLocator(dynamicSource)
    val mappings = locator.getMappings()

    assertThat(mappings).hasSize(1)
    assertThat(mappings[""]).isEqualTo(map.absolutePath)
  }

  @Test
  fun getMappingsPrioritizesSelectedVariantWhenMultipleExistForSamePackage() {
    val releaseMap = temporaryFolder.newFile("release_mapping.txt")
    val customMap = temporaryFolder.newFile("custom_mapping.txt")

    val dynamicSource =
      DynamicR8MappingSource()
        .add(text = releaseMap.toPath(), applicationId = "com.example.app", isSelected = false)
        .add(text = customMap.toPath(), applicationId = "com.example.app", isSelected = true)

    val locator = MappingFilesLocator(dynamicSource)
    val mappings = locator.getMappings()

    assertThat(mappings).hasSize(1)
    assertThat(mappings["com.example.app"]).isEqualTo(customMap.absolutePath)
  }

  @Test
  fun getMappingsPicksMostRecentlyModifiedWhenNoneIsSelected() {
    val olderMap = temporaryFolder.newFile("older_mapping.txt")
    val newerMap = temporaryFolder.newFile("newer_mapping.txt")

    olderMap.setLastModified(1000000L)
    newerMap.setLastModified(2000000L)

    val dynamicSource =
      DynamicR8MappingSource()
        .add(text = olderMap.toPath(), applicationId = "com.example.app", isSelected = false)
        .add(text = newerMap.toPath(), applicationId = "com.example.app", isSelected = false)

    val locator = MappingFilesLocator(dynamicSource)
    val mappings = locator.getMappings()

    assertThat(mappings).hasSize(1)
    assertThat(mappings["com.example.app"]).isEqualTo(newerMap.absolutePath)
  }

  @Test
  fun getMappingsWhenSelectedVariantFileDoesNotExistFallsBackToExistingVariant() {
    val releaseMap = temporaryFolder.newFile("release_mapping.txt")
    val nonExistentDebugMap = temporaryFolder.root.toPath().resolve("debug_mapping.txt")

    val dynamicSource =
      DynamicR8MappingSource()
        .add(text = nonExistentDebugMap, applicationId = "com.example.app", isSelected = true)
        .add(text = releaseMap.toPath(), applicationId = "com.example.app", isSelected = false)

    val locator = MappingFilesLocator(dynamicSource)
    val mappings = locator.getMappings()

    assertThat(mappings).hasSize(1)
    assertThat(mappings["com.example.app"]).isEqualTo(releaseMap.absolutePath)
  }

  @Test
  fun testProjectR8MappingSourceReturnsEmptyWhenProjectIsDisposed() {
    val project = Mockito.mock(Project::class.java)
    Mockito.`when`(project.isDisposed).thenReturn(true)

    val source = ProjectR8MappingSource(project)
    assertThat(source.getMappings()).isEmpty()
  }

  /** Helper to supply mapping files for testing. */
  private class DynamicR8MappingSource : R8MappingSource {
    private val mappings = mutableListOf<ProfilerR8MappingToken.R8Mapping>()

    fun add(
      text: Path,
      applicationId: String? = null,
      isSelected: Boolean = false,
    ): DynamicR8MappingSource {
      mappings.add(ProfilerR8MappingToken.R8Mapping(text, applicationId, isSelected))
      return this
    }

    override fun getMappings(): List<ProfilerR8MappingToken.R8Mapping> = mappings
  }
}
