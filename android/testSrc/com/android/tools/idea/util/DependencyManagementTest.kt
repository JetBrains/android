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
package com.android.tools.idea.util

import com.android.tools.idea.projectsystem.GoogleMavenArtifactId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [DependencyManagement].
 */
class DependencyManagementTest {
  @Test
  fun testUserConfirmationMultipleArtifactsMessage() {
    var artifacts = listOf(GoogleMavenArtifactId.DESIGN_LIB, GoogleMavenArtifactId.APPCOMPAT_V7)
    var correctMessage = "This operation requires the libraries ${GoogleMavenArtifactId.DESIGN_LIB.artifactCoordinate}, " +
        "${GoogleMavenArtifactId.APPCOMPAT_V7.artifactCoordinate}. \n\nWould you like to add these now?"

    assertEquals(createAddDependencyMessage(artifacts), correctMessage)
  }

  @Test
  fun testUserConfirmationSingleArtifactsMessage() {
    var artifacts = listOf(GoogleMavenArtifactId.DESIGN_LIB)
    var correctMessage = "This operation requires the library ${GoogleMavenArtifactId.DESIGN_LIB.artifactCoordinate}. \n\n" +
        "Would you like to add this now?"

    assertEquals(createAddDependencyMessage(artifacts), correctMessage)
  }
}