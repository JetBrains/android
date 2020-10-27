/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.idea

import com.android.builder.model.AndroidProject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Tests for [is3Dot5OrNewer].
 */
class AdditionalClassifierArtifactsModelCollectorTest {
  @Test
  fun verifyModelVersion() {
    assertFalse(is3Dot5OrNewer(getAndroidProject("1.5.0")))
    assertFalse(is3Dot5OrNewer(getAndroidProject("3.4.1")))
    assertFalse(is3Dot5OrNewer(getAndroidProject("invalidVersion")))
    assertFalse(is3Dot5OrNewer(getAndroidProject("3.5.0-rc1")))
    assertTrue(is3Dot5OrNewer(getAndroidProject("3.5.0")))
    assertTrue(is3Dot5OrNewer(getAndroidProject("3.5.1")))
    assertTrue(is3Dot5OrNewer(getAndroidProject("3.6.0-dev")))
    assertTrue(is3Dot5OrNewer(getAndroidProject("3.6.0")))
    assertTrue(is3Dot5OrNewer(getAndroidProject("4.0.0")))
  }

  @Test
  fun modelVersionNotExist() {
    val project = mock(AndroidProject::class.java)
    `when`(project.modelVersion).thenThrow(UnsupportedOperationException())
    assertFalse(is3Dot5OrNewer(project))
  }

  private fun getAndroidProject(version: String): AndroidProject {
    val project = mock(AndroidProject::class.java)
    `when`(project.modelVersion).thenReturn(version)
    return project
  }
}