/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.project

import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.project.Project
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests for [DefaultProjectSystem].
 */
class DefaultProjectSystemTest {
  private lateinit var myProject: Project
  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
    projectRule.fixture.addFileToProject("AndroidManifest.xml", """
      <?xml version="1.0" encoding="utf-8"?>
      <manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.example.app">
        <application />
      </manifest>
    """.trimIndent())
  }

  @Test
  fun testIsNotGradleProjectSystem() {
    Truth.assertThat(myProject.getProjectSystem()).isInstanceOf(DefaultProjectSystem::class.java)
  }

  @Test
  fun testSameInstanceIsReturnedFromMultipleCalls() {
    Truth.assertThat(myProject.getProjectSystem()).isSameAs(myProject.getProjectSystem())
  }

  @Test
  fun testGetApplicationIds() {
    assertThat(myProject.getProjectSystem().getKnownApplicationIds()).containsExactly("com.example.app")
  }

  @Test
  fun testGetFindFacetByApplicationId() {
    fun doLookup(applicationId: String) = myProject.getProjectSystem().findModulesWithApplicationId(applicationId).map { it.name }
    assertThat(doLookup("com.example.app")).hasSize(1)
    assertThat(doLookup("com.example.invalid")).isEmpty()
  }
}