/*
 * Copyright (C) 2022 The Android Open Source Project
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

import org.junit.Test
import kotlin.test.assertEquals

class AndroidGradleProjectResolverTest {

  @Test
  fun `Given sample app project When merge artifactsToModuleMaps Then expect same provided artifacts`() {
    val mergedArtifactsModuleIdMap = mergeProjectResolvedArtifacts(
      kmpArtifactToModuleIdMap = mutableMapOf(),
      artifactToModuleIdMap = mutableMapOf(
        "app/build/classes/java/main" to ":app:main",
        "app/build/classes/java/test" to ":app:test",
      )
    )

    assertEquals(
      actual = mergedArtifactsModuleIdMap,
      expected = mutableMapOf(
        "app/build/classes/java/main" to listOf(":app:main"),
        "app/build/classes/java/test" to listOf(":app:test")
      )
    )
  }

  @Test
  fun `Given kmp module When merge artifactsToModuleMaps Then expect same provided artifacts`() {
    val mergedArtifactsModuleIdMap = mergeProjectResolvedArtifacts(
      kmpArtifactToModuleIdMap = mutableMapOf(
        "desktop/build/libs/desktop-jvm.jar" to listOf(":desktop:jvmMain", ":desktop:commonMain")
      ),
      artifactToModuleIdMap = mutableMapOf()
    )

    assertEquals(
      actual = mergedArtifactsModuleIdMap,
      expected = mutableMapOf(
        "desktop/build/libs/desktop-jvm.jar" to listOf(":desktop:jvmMain", ":desktop:commonMain")
      )
    )
  }

  @Test
  fun `Given kmp project When merge artifactsToModuleMaps Then expect merged modulesIds for same artifact`() {
    val mergedArtifactsModuleIdMap = mergeProjectResolvedArtifacts(
      kmpArtifactToModuleIdMap = mutableMapOf(
        "desktop/build/libs/desktop-jvm.jar" to listOf(":desktop:jvmMain", ":desktop:commonMain")
      ),
      artifactToModuleIdMap = mutableMapOf(
        "desktop/build/classes/java/main" to ":desktop:main",
        "desktop/build/classes/java/test" to ":desktop:test",
        "desktop/build/libs/desktop-jvm.jar" to ":desktop:jvmMain",
        "desktop/build/libs/desktop-jvm.jar-MPP" to ":desktop:commonMain"
      )
    )

    assertEquals(
      actual = mergedArtifactsModuleIdMap,
      expected = mutableMapOf(
        "desktop/build/classes/java/main" to listOf(":desktop:main"),
        "desktop/build/classes/java/test" to listOf(":desktop:test"),
        "desktop/build/libs/desktop-jvm.jar" to listOf(":desktop:jvmMain", ":desktop:commonMain"),
        "desktop/build/libs/desktop-jvm.jar-MPP" to listOf(":desktop:commonMain")
      )
    )
  }

  @Test
  fun `Given completely different artifacts When merge artifactsToModuleMaps Then expect to have both maps combined`() {
    val mergedArtifactsModuleIdMap = mergeProjectResolvedArtifacts(
      kmpArtifactToModuleIdMap = mutableMapOf(
        "common/build/libs/common-jvm.jar" to listOf(":common:jvmMain", ":common:commonMain")
      ),
      artifactToModuleIdMap = mutableMapOf(
        "common/build/classes/java/main" to ":common:main",
        "common/build/classes/java/test" to ":common:test",
      )
    )

    assertEquals(
      actual = mergedArtifactsModuleIdMap,
      expected = mutableMapOf(
        "common/build/classes/java/main" to listOf(":common:main"),
        "common/build/classes/java/test" to listOf(":common:test"),
        "common/build/libs/common-jvm.jar" to listOf(":common:jvmMain", ":common:commonMain")
      )
    )
  }

  @Test(expected = IllegalStateException::class)
  fun `Given same artifact with different values When merge artifactsToModuleMaps Then an exception happened`() {
    mergeProjectResolvedArtifacts(
      kmpArtifactToModuleIdMap = mutableMapOf(
        "desktop/build/libs/desktop-jvm.jar" to listOf(":desktop:jvmMain", ":desktop:commonMain")
      ),
      artifactToModuleIdMap = mutableMapOf(
        "desktop/build/classes/java/main" to ":desktop:main",
        "desktop/build/classes/java/test" to ":desktop:test",
        "desktop/build/libs/desktop-jvm.jar" to ":desktop:XYZ",
        "desktop/build/libs/desktop-jvm.jar-MPP" to ":desktop:commonMain"
      )
    )
  }
}
