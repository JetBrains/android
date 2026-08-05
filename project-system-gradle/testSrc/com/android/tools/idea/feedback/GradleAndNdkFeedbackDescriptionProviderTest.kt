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
package com.android.tools.idea.feedback

import com.google.common.truth.Truth.assertThat
import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class GradleAndNdkFeedbackDescriptionProviderTest {

  @get:Rule val projectRule = ProjectRule()

  private val project: Project
    get() = projectRule.project

  private var wasTrusted: Boolean = true

  private val provider = GradleAndNdkFeedbackDescriptionProvider()

  @Before
  fun setUp() {
    wasTrusted = TrustedProjects.isProjectTrusted(projectRule.project)
  }

  @After
  fun tearDown() {
    TrustedProjects.setProjectTrusted(project, wasTrusted)
  }

  @Test
  fun testDescriptionTrusted() {
    runBlocking {
      TrustedProjects.setProjectTrusted(project, true)
      val description = provider.getDescription(project)

      assertThat(description).contains("Gradle JDK:")
      assertThat(description).doesNotContain("Gradle JDK: (default)")

      assertThat(description).contains("NDK: from local.properties:")

      assertThat(description).contains("CMake: from local.properties:")
    }
  }

  @Test
  fun testDescriptionUntrusted() {
    runBlocking {
      TrustedProjects.setProjectTrusted(project, false)
      val description = provider.getDescription(project)

      assertThat(description).contains("Gradle JDK: (default)")

      assertThat(description).contains("NDK: ")
      assertThat(description).doesNotContain("NDK: from local.properties:")

      assertThat(description).contains("CMake: ")
      assertThat(description).doesNotContain("CMake: from local.properties:")
    }
  }
}
