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

import com.google.common.truth.Truth.assertThat
import org.jetbrains.android.AndroidTestCase
import org.junit.Test

class VersionCatalogFileIndexContributorTest: AndroidTestCase() {

  @Test
  fun testCatalogInIndex() {
    val tomlFile = myFixture.addFileToProject("gradle/libs.versions.toml", "")
    myFixture.configureFromExistingVirtualFile(tomlFile.virtualFile)

    val contributor = VersionCatalogFileIndexContributor()
    val files = contributor.getAdditionalProjectRootsToIndex(project)

    assertThat(files).containsExactly(tomlFile.virtualFile)
  }

  @Test
  fun testNoCatalogsOutsideOfGradleFolder() {
    val rootTomlFile = myFixture.addFileToProject("./libs.versions.toml", "")
    myFixture.configureFromExistingVirtualFile(rootTomlFile.virtualFile)

    val subdirectoryTomlFile = myFixture.addFileToProject("./gradle/test/libs.versions.toml", "")
    myFixture.configureFromExistingVirtualFile(subdirectoryTomlFile.virtualFile)

    val contributor = VersionCatalogFileIndexContributor()
    val files = contributor.getAdditionalProjectRootsToIndex(project)

    assertThat(files).isEmpty()
  }
}