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
package com.android.tools.idea.ui.resourcemanager.importer.runsGradle

import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.Companion.prepareTestProject
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.findAppModule
import com.android.tools.idea.ui.resourcemanager.importer.getOrCreateDefaultResDirectory
import com.android.tools.idea.util.androidFacet
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.RunsInEdt
import org.junit.Rule
import org.junit.Test
import java.io.File

@RunsInEdt
class DefaultResDirectoryTest {
  @get:Rule
  val projectRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @Test
  fun testGetOrCreateDefaultResDirectoryExists() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    assertThat(preparedProject.root.resolve("app/src/main/res").exists()).isTrue()
    preparedProject.open { p ->
      val facet = p.findAppModule().androidFacet!!
      val dir = getOrCreateDefaultResDirectory(facet)
      assertThat(dir.exists()).isTrue()
      assertThat(dir.isDirectory).isTrue()
      assertThat(dir.relativeTo(preparedProject.root)).isEqualTo(File("app/src/main/res"))
    }
  }

  @Test
  fun testGetOrCreateDefaultResDirectoryCreate() {
    val preparedProject = projectRule.prepareTestProject(AndroidCoreTestProject.SIMPLE_APPLICATION)
    assertThat(preparedProject.root.resolve("app/src/main/res").exists()).isTrue()
    assertThat(preparedProject.root.resolve("app/src/main/res").deleteRecursively()).isTrue()
    preparedProject.open { p ->
      val facet = p.findAppModule().androidFacet!!
      val dir = getOrCreateDefaultResDirectory(facet)
      assertThat(dir.exists()).isTrue()
      assertThat(dir.isDirectory).isTrue()
      assertThat(dir.relativeTo(preparedProject.root)).isEqualTo(File("app/src/main/res"))
    }
  }
}