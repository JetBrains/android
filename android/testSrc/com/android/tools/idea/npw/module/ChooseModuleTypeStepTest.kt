/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.tools.idea.npw.benchmark.NewBenchmarkModuleDescriptionProvider
import com.android.tools.idea.npw.dynamicapp.NewDynamicAppModuleDescriptionProvider
import com.android.tools.idea.npw.importing.ImportModuleGalleryEntryProvider
import com.android.tools.idea.npw.java.NewLibraryModuleDescriptionProvider
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.mockito.Mockito
import org.mockito.Mockito.`when`

class ChooseModuleTypeStepTest : AndroidGradleTestCase() {
  fun testSortSingleModuleEntries() {
    assertThat(sort("Phone & Tablet Module")).containsExactly("Phone & Tablet Module").inOrder()
  }

  fun testSortTwoModuleEntries() {
    assertThat(sort("Android Library", "Phone & Tablet Module")).containsExactly("Phone & Tablet Module", "Android Library").inOrder()
    assertThat(sort("A", "Phone & Tablet Module")).containsExactly("Phone & Tablet Module", "A").inOrder()
    assertThat(sort("Wear OS Module", "A")).containsExactly("Wear OS Module", "A").inOrder()
    assertThat(sort("C", "A")).containsExactly("A", "C").inOrder()
  }

  fun testSortFullModuleEntries() {
    assertThat(sort("Z", "Import .JAR/.AAR Package", "Android Library", "Phone & Tablet Module", "Wear OS Module", "Android TV Module",
                    "Import Gradle Project", "Import Eclipse ADT Project", "Google Cloud Module", "Java or Kotlin Library", "Benchmark Module",
                    "A")).containsExactly("Phone & Tablet Module", "Android Library", "Wear OS Module", "Android TV Module",
                                          "Import Gradle Project", "Import Eclipse ADT Project", "Import .JAR/.AAR Package",
                                          "Java or Kotlin Library", "Google Cloud Module", "Benchmark Module", "A", "Z").inOrder()
  }

  fun testSortExistingModuleEntries() {
    val providers = listOf(ImportModuleGalleryEntryProvider(), NewAndroidModuleDescriptionProvider(),
                           NewDynamicAppModuleDescriptionProvider(), NewLibraryModuleDescriptionProvider(),
                           NewBenchmarkModuleDescriptionProvider())
    val moduleDescriptions = providers.flatMap { it.getDescriptions(project) }

    val sortedEntries = sortModuleEntries(moduleDescriptions).map { it.name }

    val expectedEntries = listOf(
      "Phone & Tablet Module", "Android Library", "Dynamic Feature Module", "Instant Dynamic Feature Module",
      "Automotive Module", "Wear OS Module", "Android TV Module", "Android Things Module", "Import Gradle Project",
      "Import Eclipse ADT Project", "Import .JAR/.AAR Package", "Java or Kotlin Library", "Benchmark Module")

    assertThat(sortedEntries).containsExactlyElementsIn(expectedEntries).inOrder()
  }

  private fun sort(vararg entries: String): List<String> {
    val moduleDescriptions = entries.map {
      Mockito.mock(ModuleGalleryEntry::class.java).apply {
        `when`(name).thenReturn(it)
      }
    }

    val sortedEntries = sortModuleEntries(moduleDescriptions)
    assertEquals(entries.size, sortedEntries.size)

    return sortedEntries.map { it.name }
  }
}