/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.index

import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test

class VersionCatalogFileIndexContributorTest{

  @get:Rule
  val projectRule = AndroidProjectRule.onDisk()
  private val fixture get() = projectRule.fixture
  private val project get() = projectRule.project
  @Test
  fun testCatalogInIndex() {
    val tomlFile = fixture.addFileToProject("gradle/libs.versions.toml", "")
    fixture.configureFromExistingVirtualFile(tomlFile.virtualFile)

    val contributor = VersionCatalogFileIndexContributor()
    val files = contributor.getAdditionalProjectRootsToIndex(project)

    assertThat(files).containsExactly(tomlFile.virtualFile)
  }

  @Test
  fun testNoCatalogsOutsideOfGradleFolder() {
    val rootTomlFile = fixture.addFileToProject("./libs.versions.toml", "")
    fixture.configureFromExistingVirtualFile(rootTomlFile.virtualFile)

    val subdirectoryTomlFile = fixture.addFileToProject("./gradle/test/libs.versions.toml", "")
    fixture.configureFromExistingVirtualFile(subdirectoryTomlFile.virtualFile)

    val contributor = VersionCatalogFileIndexContributor()
    val files = contributor.getAdditionalProjectRootsToIndex(project)

    assertThat(files).isEmpty()
  }
}