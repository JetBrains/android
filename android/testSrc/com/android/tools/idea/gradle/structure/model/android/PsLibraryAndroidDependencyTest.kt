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
package com.android.tools.idea.gradle.structure.model.android

import com.android.ide.common.repository.GradleVersion
import com.android.tools.idea.gradle.structure.model.meta.ValueDescriptor
import com.android.tools.idea.gradle.structure.model.repositories.search.FoundArtifact
import com.android.tools.idea.gradle.structure.model.repositories.search.SearchResult
import com.android.tools.idea.gradle.structure.model.repositories.search.combine
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assert.assertThat
import org.junit.Test

class PsLibraryAndroidDependencyTest {

  @Test
  fun toVersionValueDescriptors() {
    val searchResults =
      listOf(
        SearchResult(listOf(FoundArtifact("rep1", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("1.1"))))),
        SearchResult(listOf(FoundArtifact("rep2", "group", "name", listOf(GradleVersion.parse("1.0"), GradleVersion.parse("0.9"))))),
        SearchResult(listOf(), listOf(Exception("1"), Exception("2")))
      ).combine()

    assertThat(
      searchResults.toVersionValueDescriptors(),
      equalTo(
        listOf(ValueDescriptor("1.1"), ValueDescriptor("1.0"), ValueDescriptor("0.9"))))
  }
}