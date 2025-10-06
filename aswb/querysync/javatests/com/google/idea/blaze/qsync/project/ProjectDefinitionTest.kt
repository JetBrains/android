/*
 * Copyright 2023 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync.project

import com.google.common.truth.Truth
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectDefinitionTest {
  @Test
  fun testGetIncludingContentRoot_returnsContentRoot() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("contentroot1"), Path.of("contentroot2")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = emptySet(),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    Truth.assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot1/some/path")))
      .isEqualTo(Path.of("contentroot1"))
    Truth.assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot2")))
      .isEqualTo(Path.of("contentroot2"))
  }

  @Test
  fun testGetIncludingContentRoot_externalPath_returnsEmpty() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("contentroot1"), Path.of("contentroot2")),
        projectExcludes = emptySet(),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = emptySet(),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    Truth.assertThat(projectDefinition.getIncludingContentRoot(Path.of("anotherRoot/some/path")))
      .isNull()
  }

  @Test
  fun testGetIncludingContentRoot_excludedPath_returnsEmpty() {
    val projectDefinition =
      ProjectDefinition(
        projectIncludes = setOf(Path.of("contentroot1"), Path.of("contentroot2")),
        projectExcludes = setOf(Path.of("contentroot1/excluded")),
        deriveTargetsFromDirectories = false,
        targetPatterns = emptyList(),
        isAndroidWorkspace = false,
        languageClasses = emptySet(),
        testSources = emptySet(),
        systemExcludes = emptySet(),
      )
    Truth.assertThat(projectDefinition.getIncludingContentRoot(Path.of("contentroot1/excluded/path")))
      .isNull()
  }
}
