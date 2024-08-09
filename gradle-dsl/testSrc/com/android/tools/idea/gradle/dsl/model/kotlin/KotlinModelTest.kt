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

class KotlinModelTest : GradleFileModelTestCase() {
  @Test
  fun addToolchain() {
    writeToBuildFile(TestFile.ADD_TOOLCHAIN)
    val buildModel = gradleBuildModel
    val kotlin = buildModel.kotlin()
    assertMissingProperty(kotlin.jvmToolchain())
    kotlin.jvmToolchain().setValue(17)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_TOOLCHAIN_EXPECTED)
    checkForValidPsiElement(gradleBuildModel.kotlin(), KotlinModelImpl::class.java)
    assertEquals("jvmToolchain", Integer.valueOf(17), kotlin.jvmToolchain().toInt())
  }

  @Test
  fun removeToolchain() {
    writeToBuildFile(TestFile.REMOVE_TOOLCHAIN)
    val buildModel = gradleBuildModel
    var kotlin = buildModel.kotlin()
    kotlin.jvmToolchain().delete()
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.REMOVE_TOOLCHAIN_EXPECTED)
    kotlin = gradleBuildModel.kotlin()
    checkForInvalidPsiElement(kotlin, KotlinModelImpl::class.java)
    assertMissingProperty(kotlin.jvmToolchain())
  }

  @Test
  fun updateToolchain() {
    writeToBuildFile(TestFile.REMOVE_TOOLCHAIN)
    val buildModel = gradleBuildModel
    var kotlin = buildModel.kotlin()
    assertEquals(Integer.valueOf(17), kotlin.jvmToolchain().toInt())
    kotlin.jvmToolchain().setValue(21)
    applyChangesAndReparse(buildModel)
    verifyFileContents(myBuildFile, TestFile.UPDATE_TOOLCHAIN_EXPECTED)
    kotlin = gradleBuildModel.kotlin()
    assertEquals(Integer.valueOf(21), kotlin.jvmToolchain().toInt())
  }

  @Test
  fun readToolchainVersionAsReference() {
    writeToBuildFile(TestFile.READ_TOOLCHAIN_VERSION_AS_REFERENCE)
    val buildModel = gradleBuildModel
    val kotlin = buildModel.kotlin()
    assertEquals(Integer.valueOf(21), kotlin.jvmToolchain().toInt())
  }

  @Test
  fun testAddAndApplySourceSetBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    kotlinModel.addSourceSet("set")
    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    sourceSet.dependencies().addArtifact("implementation", "org.junit:junit:4.11")

    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    sourceSet = sourceSets[0]
    assertEquals("sourceSets", "set", sourceSet.name())
    Truth.assertThat(sourceSet.dependencies().artifacts()).hasSize(1)

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
  fun testAddAndApplyExistingSourceSetBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    val kotlinModel = buildModel.kotlin()
    assertNotNull(kotlinModel)

    kotlinModel.addSourceSet("commonMain")
    var sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    var sourceSet = sourceSets[0]
    sourceSet.dependencies().addArtifact("implementation", "org.junit:junit:4.11")

    kotlinModel.addSourceSet("commonTest")
    sourceSets = kotlinModel.sourceSets()
    Truth.assertThat(sourceSets).hasSize(2)
    sourceSet = sourceSets[1]
    sourceSet.dependencies().addArtifact("implementation", "org.junit:junit:4.11")

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EXISTING_SOURCE_SET_BLOCK_EXPECTED)
  }

  @Test
  fun testAddAndApplyEmptySourceSetBlock() {
    writeToBuildFile(TestFile.ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var kotlin = buildModel.android()
    assertNotNull(kotlin)

    kotlin.addSourceSet("set")
    val sourceSets = kotlin.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set", sourceSets[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK)

    buildModel.reparse()
    kotlin = buildModel.android()
    assertNotNull(kotlin)
    Truth.assertThat(kotlin.sourceSets()).isEmpty() // Empty blocks are not saved to the file.
  }

  @Test
  fun testRemoveAndApplySourceSetBlock() {
    writeToBuildFile(TestFile.REMOVE_AND_APPLY_SOURCE_SET_BLOCK)
    val buildModel = gradleBuildModel
    var kotlin = buildModel.android()
    assertNotNull(kotlin)

    var sourceSets = kotlin.sourceSets()
    Truth.assertThat(sourceSets).hasSize(2)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
    assertEquals("sourceSets", "set2", sourceSets[1].name())

    kotlin.removeSourceSet("set2")
    sourceSets = kotlin.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    applyChanges(buildModel)
    verifyFileContents(myBuildFile, TestFile.REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED)

    sourceSets = kotlin.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())

    buildModel.reparse()
    kotlin = buildModel.android()
    assertNotNull(kotlin)

    sourceSets = kotlin.sourceSets()
    Truth.assertThat(sourceSets).hasSize(1)
    assertEquals("sourceSets", "set1", sourceSets[0].name())
  }

  internal enum class TestFile(private val path: @SystemDependent String) : TestFileName {
    ADD_TOOLCHAIN("addToolchain"),
    ADD_TOOLCHAIN_EXPECTED("addToolchainExpected"),
    REMOVE_TOOLCHAIN("removeToolchain"),
    REMOVE_TOOLCHAIN_EXPECTED("removeToolchainExpected"),
    UPDATE_TOOLCHAIN_EXPECTED("updateToolchainExpected"),
    READ_TOOLCHAIN_VERSION_AS_REFERENCE("readToolchainVersionArgumentReference"),
    ADD_AND_APPLY_EMPTY_SOURCE_SET_BLOCK("addAndApplyEmptySourceSetBlock"),
    ADD_AND_APPLY_SOURCE_SET_BLOCK("addAndApplySourceSetBlock"),
    ADD_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED("addAndApplySourceSetBlockExpected"),
    ADD_AND_APPLY_EXISTING_SOURCE_SET_BLOCK_EXPECTED("addAndApplyExistingSourceSetBlockExpected"),
    REMOVE_AND_APPLY_SOURCE_SET_BLOCK("removeAndApplySourceSetBlock"),
    REMOVE_AND_APPLY_SOURCE_SET_BLOCK_EXPECTED("removeAndApplySourceSetBlockExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/kotlinModel/$path", extension)
    }
  }
}