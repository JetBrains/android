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
package com.android.tools.idea.gradle.project

import com.intellij.testFramework.LightPlatformTestCase
import junit.framework.TestCase
import org.junit.Test

class ProjectMigrationsPersistentStateTest : LightPlatformTestCase() {

  private val migrations by lazy { ProjectMigrationsPersistentState.getInstance(project) }

  override fun tearDown() {
    migrations.loadState(ProjectMigrationsPersistentState())
    super.tearDown()
  }

  @Test
  fun `test Given persistent state When update values Then state has been updated`() {
    assertEmpty(migrations.state.migratedGradleRootsToGradleLocalJavaHome)

    migrations.migratedGradleRootsToGradleLocalJavaHome.add("test")
    assertSize(1, migrations.state.migratedGradleRootsToGradleLocalJavaHome)
    assertEquals("test", migrations.state.migratedGradleRootsToGradleLocalJavaHome.first())

    migrations.migratedGradleRootsToGradleLocalJavaHome.clear()
    assertEmpty(migrations.state.migratedGradleRootsToGradleLocalJavaHome)
  }

  @Test
  fun `test Given persistent state When load a new state Then values have been updated`() {
    migrations.loadState(ProjectMigrationsPersistentState())
    assertEmpty(migrations.migratedGradleRootsToGradleLocalJavaHome)

    val migratedProject = mutableSetOf<String>()
    migratedProject.addAll(listOf("test1", "test2"))
    migrations.loadState(ProjectMigrationsPersistentState().apply { migratedGradleRootsToGradleLocalJavaHome.addAll(migratedProject) })
    TestCase.assertEquals(migratedProject, migrations.migratedGradleRootsToGradleLocalJavaHome)

    migratedProject.add("test3")
    migrations.loadState(ProjectMigrationsPersistentState().apply { migratedGradleRootsToGradleLocalJavaHome.addAll(migratedProject) })
    TestCase.assertEquals(migratedProject, migrations.migratedGradleRootsToGradleLocalJavaHome)
  }
}