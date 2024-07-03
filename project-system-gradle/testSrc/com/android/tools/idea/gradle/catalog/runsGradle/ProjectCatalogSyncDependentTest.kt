/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.catalog.runsGradle

import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class ProjectCatalogSyncDependentTest {
  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @Before
  fun setUp() {
    projectRule.fixture.testDataPath = TestProjectPaths.TEST_DATA_PATH
  }
  @Test
  fun testAppliedFilesShared() {
    projectRule.loadProject(TestProjectPaths.PSD_IMPORTED_VERSION_CATALOG_SAMPLE_GROOVY)
    val pbm = ProjectBuildModel.get(projectRule.project)
    assertThat(pbm.versionCatalogsModel.catalogNames()).containsAllIn(setOf("libs", "libExample"))
    val catalog = pbm.versionCatalogsModel.getVersionCatalogModel("libExample")
    assertThat(catalog).isNotNull()
  }
}