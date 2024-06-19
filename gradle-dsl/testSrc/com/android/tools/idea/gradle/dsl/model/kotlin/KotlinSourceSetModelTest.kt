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
package com.android.tools.idea.gradle.dsl.model.kotlin

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.google.common.truth.Truth
import org.jetbrains.annotations.SystemDependent
import org.junit.Test
import java.io.File

class KotlinSourceSetModelTest : GradleFileModelTestCase() {

  @Test
  fun testDependenciesAddAndApply() {
    writeToBuildFile(TestFile.DEPENDENCIES_ADD_AND_APPLY)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    assertEquals("name", "set", sourceSet.name())
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(0)

    sourceSet.dependencies().addArtifact("implementation", "org.junit:junit:4.11")
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.DEPENDENCIES_ADD_AND_APPLY_EXPECTED)
    buildModel.reparse()

    kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)
    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
  }

  @Test
  fun testSingleDependencyAddAndApply() {
    writeToBuildFile(TestFile.SINGLE_DEPENDENCY_ADD_AND_APPLY)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
    sourceSet.dependencies().addArtifact("api", "org.junit:junit:4.11")

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.SINGLE_DEPENDENCY_ADD_AND_APPLY_EXPECTED)
    buildModel.reparse()

    kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)
    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(2)
    Truth.assertThat(sourceSet.dependencies().artifacts()[0].spec.toString()).isEqualTo("org.junit:junit:4.11")
    Truth.assertThat(sourceSet.dependencies().artifacts()[1].spec.toString()).isEqualTo("com.example:bar:1.0")
  }

  @Test
  fun testDependenciesEditAndApply() {
    writeToBuildFile(TestFile.DEPENDENCIES_EDIT_AND_APPLY)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
    sourceSet.dependencies().artifacts()[0].version().setValue("2.0")

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.DEPENDENCIES_EDIT_AND_APPLY_EXPECTED)
    buildModel.reparse()

    kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)
    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
    Truth.assertThat(sourceSet.dependencies().artifacts()[0].spec.toString()).isEqualTo("com.example:bar:2.0")
  }

  @Test
  fun testSingleDependencyRemoveAndApply() {
    writeToBuildFile(TestFile.SINGLE_DEPENDENCY_REMOVE_AND_APPLY)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(2)
    val dependencyToRemove = sourceSet.dependencies().artifacts()[0]
    sourceSet.dependencies().remove(dependencyToRemove)

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.SINGLE_DEPENDENCY_REMOVE_AND_APPLY_EXPECTED)

    buildModel.reparse()

    kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)
    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
    Truth.assertThat(sourceSet.dependencies().artifacts()[0].spec.toString()).isEqualTo("com.example:bar:2.0")
  }

  @Test
  fun testDependenciesBlockRemoveAndApply() {
    writeToBuildFile(TestFile.DEPENDENCIES_BLOCK_REMOVE_AND_APPLY)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    val sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    val sourceSet = sourceSets[0]
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)
    sourceSet.removeDependencies()

    applyChangesAndReparse(buildModel)

    // source set is removed due to empty block
    kotlinModel = buildModel.kotlin()
    Truth.assertThat(kotlinModel.sourceSets()).hasSize(0)
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    DEPENDENCIES_ADD_AND_APPLY("dependenciesAddAndApply"),
    DEPENDENCIES_ADD_AND_APPLY_EXPECTED("dependenciesAddAndApplyExpected"),
    SINGLE_DEPENDENCY_ADD_AND_APPLY("singleDependencyAddAndApply"),
    SINGLE_DEPENDENCY_ADD_AND_APPLY_EXPECTED("singleDependencyAddAndApplyExpected"),
    DEPENDENCIES_EDIT_AND_APPLY("dependenciesEditAndApply"),
    DEPENDENCIES_EDIT_AND_APPLY_EXPECTED("dependenciesEditAndApplyExpected"),
    SINGLE_DEPENDENCY_REMOVE_AND_APPLY("singleDependencyRemoveAndApply"),
    SINGLE_DEPENDENCY_REMOVE_AND_APPLY_EXPECTED("singleDependencyRemoveAndApplyExpected"),
    DEPENDENCIES_BLOCK_REMOVE_AND_APPLY("dependenciesBlockRemoveAndApply"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/kotlinSourceSetModel/$path", extension)
    }
  }
}