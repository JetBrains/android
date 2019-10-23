/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.projectsystem

import com.android.SdkConstants
import com.android.projectmodel.ExternalLibrary
import com.android.testutils.truth.FileSubject.assertThat
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.testing.AndroidGradleTestCase
import com.google.common.truth.Truth.assertThat

/**
 * Integration tests for [GradleProjectSystem]; contains tests that require a working gradle project.
 */
class GradleProjectSystemIntegrationTest : AndroidGradleTestCase() {
  @Throws(Exception::class)
  fun testGetDependentLibraries() {
    loadSimpleApplication()
    val moduleSystem = project
      .getProjectSystem()
      .getModuleSystem(getModule("app"))
    val libraries = moduleSystem.getResolvedDependentLibraries()

    // There are many aars and jars in the loaded application. Here we just confirm that a few known ones are present
    val guava = libraries
      .filterIsInstance<ExternalLibrary>()
      .first { library -> library.address.startsWith("com.google.guava") }
    assertThat(guava.classJars.first().fileName).matches("guava-[\\.\\d]+.jar")

    val appcompat = libraries
      .filterIsInstance<com.android.projectmodel.ExternalLibrary>()
      .first { library -> library.address.startsWith("com.android.support:support-compat") }

    assertThat(appcompat.address).matches("com.android.support:support-compat:[\\.\\d]+@aar")
    assertThat(appcompat.manifestFile?.fileName).isEqualTo(SdkConstants.FN_ANDROID_MANIFEST_XML)
    assertThat(appcompat.resFolder!!.root.toFile()).isDirectory()
  }
}
