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

import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.npw.baselineprofiles.NewBaselineProfilesModuleDescriptionProvider
import com.android.tools.idea.npw.benchmark.NewBenchmarkModuleDescriptionProvider
import com.android.tools.idea.npw.dynamicapp.NewDynamicAppModuleDescriptionProvider
import com.android.tools.idea.npw.importing.ImportModuleGalleryEntryProvider
import com.android.tools.idea.npw.java.NewLibraryModuleDescriptionProvider
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.util.AndroidBundle.message
import org.mockito.Mockito

class ChooseModuleTypeStepTest : AndroidGradleTestCase() {
  fun testSortSingleModuleEntries() {
    assertThat(sort(message("android.wizard.module.new.mobile"))).containsExactly(message("android.wizard.module.new.mobile")).inOrder()
  }

  fun testSortTwoModuleEntries() {
    assertThat(sort(message("android.wizard.module.new.library"), message("android.wizard.module.new.mobile")))
      .containsExactly(message("android.wizard.module.new.mobile"), message("android.wizard.module.new.library")).inOrder()
    assertThat(sort("A", message("android.wizard.module.new.mobile")))
      .containsExactly(message("android.wizard.module.new.mobile"), "A").inOrder()
    assertThat(sort(message("android.wizard.module.new.wear"), "A"))
      .containsExactly(message("android.wizard.module.new.wear"), "A").inOrder()
    assertThat(sort("C", "A")).containsExactly("A", "C").inOrder()
  }

  fun testSortFullModuleEntries() {
    assertThat(
      sort("Z", message("android.wizard.module.new.library"), message("android.wizard.module.new.mobile"),
           message("android.wizard.module.new.wear"), message("android.wizard.module.new.tv"),
           message("android.wizard.module.import.gradle.title"), message("android.wizard.module.import.eclipse.title"),
           message("android.wizard.module.new.google.cloud"), message("android.wizard.module.new.java.or.kotlin.library"),
           message("android.wizard.module.new.benchmark.module.app"), "A"))
      .containsExactly(
        message("android.wizard.module.new.mobile"), message("android.wizard.module.new.library"),
        message("android.wizard.module.new.wear"), message("android.wizard.module.new.tv"),
        message("android.wizard.module.import.gradle.title"), message("android.wizard.module.import.eclipse.title"),
        message("android.wizard.module.new.java.or.kotlin.library"), message("android.wizard.module.new.google.cloud"),
        message("android.wizard.module.new.benchmark.module.app"), "A", "Z")
      .inOrder()
  }

  fun testSortExistingModuleEntries() {
    val providers = listOf(
      ImportModuleGalleryEntryProvider(),
      NewAndroidModuleDescriptionProvider(),
      NewDynamicAppModuleDescriptionProvider(),
      NewLibraryModuleDescriptionProvider(),
      NewBenchmarkModuleDescriptionProvider(),
      NewBaselineProfilesModuleDescriptionProvider()
    )
    val moduleDescriptions = providers.flatMap { it.getDescriptions(project) }

    val sortedEntries = sortModuleEntries(moduleDescriptions).map { it.name }

    val expectedEntries = listOf(
      message("android.wizard.module.new.mobile"),
      message("android.wizard.module.new.library"),
      message("android.wizard.module.new.native.library"),
      message("android.wizard.module.new.dynamic.module"),
      message("android.wizard.module.new.dynamic.module.instant"),
      message("android.wizard.module.new.automotive"),
      message("android.wizard.module.new.wear"),
      message("android.wizard.module.new.tv"),
      message("android.wizard.module.import.gradle.title"),
      message("android.wizard.module.import.eclipse.title"),
      message("android.wizard.module.new.java.or.kotlin.library"),
      message("android.wizard.module.new.baselineprofiles.module.app"),
      message("android.wizard.module.new.benchmark.module.app")
    ).filterNot {
      it == message("android.wizard.module.import.gradle.title") || it == message("android.wizard.module.import.eclipse.title")
    }

    assertThat(sortedEntries).containsExactlyElementsIn(expectedEntries).inOrder()
  }

  private fun sort(vararg entries: String): List<String> {
    val moduleDescriptions = entries.map {
      Mockito.mock(ModuleGalleryEntry::class.java).apply {
        whenever(name).thenReturn(it)
      }
    }

    val sortedEntries = sortModuleEntries(moduleDescriptions)
    assertEquals(entries.size, sortedEntries.size)

    return sortedEntries.map { it.name }
  }
}