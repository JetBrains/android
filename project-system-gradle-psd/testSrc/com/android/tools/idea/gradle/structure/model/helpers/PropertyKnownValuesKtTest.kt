/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.helpers

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.gradle.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.repositories.search.SearchResult
import com.android.tools.idea.gradle.repositories.search.combine
import com.android.tools.idea.gradle.structure.model.PsProjectImpl
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.testFramework.RunsInEdt
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.core.IsEqual
import org.junit.Assert.assertThat
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class PropertyKnownValuesKtTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withAndroidModels().onEdt()

  @Test
  fun testBuildTypeMatchingFallbackValuesCore() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)

      assertThat(
        buildTypeMatchingFallbackValuesCore(project),
        equalTo(listOf(ValueDescriptor("debug"), ValueDescriptor("release"), ValueDescriptor("specialRelease")))
      )
    }
  }

  @Test
  fun testProductFlavorMatchingFallbackValuesCore() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.PSD_SAMPLE_GROOVY, "p")
    preparedProject.open { resolvedProject ->
      val project = PsProjectImpl(resolvedProject)

      assertThat(
        productFlavorMatchingFallbackValuesCore(project, "foo"),
        equalTo(listOf(ValueDescriptor("basic"), ValueDescriptor("paid")))
      )
      assertThat(
        productFlavorMatchingFallbackValuesCore(project, "bar"),
        equalTo(listOf(ValueDescriptor("bar"), ValueDescriptor("otherBar")))
      )
    }
  }

  @Test
  fun testToVersionValueDescriptors() {
    val searchResults =
      listOf(
        SearchResult(listOf(FoundArtifact("rep1", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("1.1"))))),
        SearchResult(listOf(FoundArtifact("rep2", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("0.9"))))),
        SearchResult(listOf(), listOf(Exception("1"), Exception("2")))
      ).combine()

    assertThat(
      searchResults.toVersionValueDescriptors(),
      IsEqual.equalTo(
        listOf(ValueDescriptor("1.1"), ValueDescriptor("1.0"), ValueDescriptor("0.9"))))
  }

  @Test
  fun testToVersionValueDescriptorsWithMinimum() {
    val searchResults =
      listOf(
        SearchResult(listOf(FoundArtifact("rep1", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("1.1"))))),
        SearchResult(listOf(FoundArtifact("rep2", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("0.9"))))),
        SearchResult(listOf(), listOf(Exception("1"), Exception("2")))
      ).combine()

    assertThat(
      searchResults.toVersionValueDescriptors { it >= GradleVersion(1, 0) },
      IsEqual.equalTo(
        listOf(ValueDescriptor("1.1"), ValueDescriptor("1.0"))))
  }
}
