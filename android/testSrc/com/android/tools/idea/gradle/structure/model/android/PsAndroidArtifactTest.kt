/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.model.android

import com.android.tools.idea.gradle.model.IdeArtifactName
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests for [PsAndroidArtifact].
 */
class PsAndroidArtifactTest {

  @Test
  fun getPossibleConfigurationNamesWithMainArtifact() {
    var configurationNames = getPossibleConfigurationNames(IdeArtifactName.MAIN, "debug", listOf())
    assertThat(configurationNames).containsExactly("compile", "debugCompile",
                                                   "api", "debugApi",
                                                   "implementation", "debugImplementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.MAIN, "debug", listOf("flavor1"))
    assertThat(configurationNames).containsExactly("compile", "debugCompile", "flavor1Compile",
                                                   "api", "debugApi", "flavor1Api",
                                                   "implementation", "debugImplementation", "flavor1Implementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.MAIN, "debug", listOf("flavor1", "flavor2"))
    assertThat(configurationNames).containsExactly("compile", "debugCompile", "flavor1Compile", "flavor2Compile",
                                                   "api", "debugApi", "flavor1Api", "flavor2Api",
                                                   "implementation", "debugImplementation", "flavor1Implementation",
                                                   "flavor2Implementation")
  }

  @Test
  fun getPossibleConfigurationNamesWitTestArtifact() {
    var configurationNames = getPossibleConfigurationNames(IdeArtifactName.UNIT_TEST, "debug", listOf())
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile",
                                                   "testApi", "testDebugApi",
                                                   "testImplementation", "testDebugImplementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.UNIT_TEST, "debug", listOf("flavor1"))
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile", "testFlavor1Compile",
                                                   "testApi", "testDebugApi", "testFlavor1Api",
                                                   "testImplementation", "testDebugImplementation", "testFlavor1Implementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.UNIT_TEST, "debug", listOf("flavor1", "flavor2"))
    assertThat(configurationNames).containsExactly("testCompile", "testDebugCompile", "testFlavor1Compile", "testFlavor2Compile",
                                                   "testApi", "testDebugApi", "testFlavor1Api", "testFlavor2Api",
                                                   "testImplementation", "testDebugImplementation", "testFlavor1Implementation",
                                                   "testFlavor2Implementation")
  }

  @Test
  fun getPossibleConfigurationNamesWitAndroidTestArtifact() {
    var configurationNames = getPossibleConfigurationNames(IdeArtifactName.ANDROID_TEST, "debug", listOf())
    assertThat(configurationNames).containsExactly("androidTestCompile",
                                                   "androidTestApi",
                                                   "androidTestImplementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.ANDROID_TEST, "debug", listOf("flavor1"))
    assertThat(configurationNames).containsExactly("androidTestCompile", "androidTestFlavor1Compile",
                                                   "androidTestApi", "androidTestFlavor1Api",
                                                   "androidTestImplementation", "androidTestFlavor1Implementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.ANDROID_TEST, "debug", listOf("flavor1", "flavor2"))
    assertThat(configurationNames).containsExactly("androidTestCompile", "androidTestFlavor1Compile", "androidTestFlavor2Compile",
                                                   "androidTestApi", "androidTestFlavor1Api", "androidTestFlavor2Api",
                                                   "androidTestImplementation", "androidTestFlavor1Implementation",
                                                   "androidTestFlavor2Implementation")
  }

  @Test
  fun getPossibleConfigurationNamesWitTestFixturesArtifact() {
    var configurationNames = getPossibleConfigurationNames(IdeArtifactName.TEST_FIXTURES, "debug", listOf())
    assertThat(configurationNames).containsExactly("testFixturesCompile",
                                                   "testFixturesApi",
                                                   "testFixturesImplementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.TEST_FIXTURES, "debug", listOf("flavor1"))
    assertThat(configurationNames).containsExactly("testFixturesCompile", "testFixturesFlavor1Compile",
                                                   "testFixturesApi", "testFixturesFlavor1Api",
                                                   "testFixturesImplementation", "testFixturesFlavor1Implementation")

    configurationNames = getPossibleConfigurationNames(IdeArtifactName.TEST_FIXTURES, "debug", listOf("flavor1", "flavor2"))
    assertThat(configurationNames).containsExactly("testFixturesCompile", "testFixturesFlavor1Compile", "testFixturesFlavor2Compile",
                                                   "testFixturesApi", "testFixturesFlavor1Api", "testFixturesFlavor2Api",
                                                   "testFixturesImplementation", "testFixturesFlavor1Implementation",
                                                   "testFixturesFlavor2Implementation")
  }
}
