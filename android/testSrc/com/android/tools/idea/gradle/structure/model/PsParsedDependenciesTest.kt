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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import org.hamcrest.CoreMatchers.hasItems
import org.junit.Assert.assertThat
import org.junit.Test

class PsParsedDependenciesTest : GradleFileModelTestCase() {
  @Test
  fun testParsedDependencies() {
    writeToBuildFile(
      """
        dependencies {
          api 'com.android.support:appcompat-v7:+'
          api fileTree(dir: 'libs', include: ['*.jar'])
          implementation 'com.example.libs:lib1:1.0'
          debugImplementation 'com.example.libs:lib1:1.0'
          releaseImplementation 'com.example.libs:lib1:0.9.1'
        }"""
    )

    val parsedDependencies = PsParsedDependencies(gradleBuildModel)
    val dependencies = mutableListOf<ArtifactDependencyModel>()
    parsedDependencies.forEachLibraryDependency { dependencies.add(it) }
    assertThat(
      dependencies.map { it.compactNotation() to it.configurationName() },
      hasItems(
        "com.android.support:appcompat-v7:+" to "api",
        "com.example.libs:lib1:1.0" to "implementation",
        "com.example.libs:lib1:1.0" to "debugImplementation",
        "com.example.libs:lib1:0.9.1" to "releaseImplementation"
      )
    )
  }

  @Test
  fun testFindLibraries() {
    writeToBuildFile(
      """
        dependencies {
          api 'com.android.support:appcompat-v7:+'
          api fileTree(dir: 'libs', include: ['*.jar'])
          implementation 'com.example.libs:lib1:1.0'
          debugImplementation 'com.example.libs:lib1:1.0'
          releaseImplementation 'com.example.libs:lib1:0.9.1'
        }"""
    )

    val parsedDependencies = PsParsedDependencies(gradleBuildModel)
    val lib1 = parsedDependencies.findLibraryDependencies("com.example.libs", "lib1")
    assertThat(
      lib1.map { it.compactNotation() to it.configurationName() },
      hasItems(
        "com.example.libs:lib1:1.0" to "implementation",
        "com.example.libs:lib1:1.0" to "debugImplementation",
        "com.example.libs:lib1:0.9.1" to "releaseImplementation"
      )
    )
  }
}