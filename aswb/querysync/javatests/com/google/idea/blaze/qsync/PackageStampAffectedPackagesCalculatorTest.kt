/*
 * Copyright 2026 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.qsync

import com.google.common.truth.Truth.assertThat
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PackageStampAffectedPackagesCalculatorTest {

  @Test
  fun testUnchangedPackages() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        packagesToUpdate = listOf(Path.of("a/b/c")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).isEmpty()
    assertThat(affected.deletedPackages).isEmpty()
  }

  @Test
  fun testModifiedPackages() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b/c") to 200L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        packagesToUpdate = listOf(Path.of("a/b/c")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b/c"))
  }

  @Test
  fun testDeletedPackages() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = emptyMap(),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        packagesToUpdate = emptyList(),
        projectScope = { true },
      )

    assertThat(affected.deletedPackages).containsExactly(Path.of("a/b/c"))
  }

  @Test
  fun testDeletedPackages_outsideProjectScope() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = emptyMap(),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        packagesToUpdate = emptyList(),
        projectScope = { false },
      )

    assertThat(affected.deletedPackages).isEmpty()
  }

  @Test
  fun testAddedPackages() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b/c") to 100L),
        latestBuildGraphDataPackageStamp = emptyMap(),
        packagesToUpdate = listOf(Path.of("a/b/c")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b/c"))
  }

  @Test
  fun testNewPackage_parentPackageAlsoUpdated() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b") to 100L, Path.of("a") to 100L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a") to 50L),
        packagesToUpdate = listOf(Path.of("a/b")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b"), Path.of("a"))
  }

  @Test
  fun testNewPackage_parentPackageForceUpdated() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b") to 100L, Path.of("a") to 50L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a") to 50L),
        packagesToUpdate = listOf(Path.of("a/b")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b"), Path.of("a"))
  }

  @Test
  fun testNewPackage_grandParentPackageAlsoUpdated() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b/c") to 100L, Path.of("a") to 100L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("a") to 50L),
        packagesToUpdate = listOf(Path.of("a/b/c")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b/c"), Path.of("a"))
  }

  @Test
  fun testNewPackage_rootPackageAsParent() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a") to 100L, Path.of("") to 100L),
        latestBuildGraphDataPackageStamp = mapOf(Path.of("") to 100L),
        packagesToUpdate = listOf(Path.of("a")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a"), Path.of(""))
  }

  @Test
  fun testNewPackage_parentPackageAlsoNew() {
    val affected =
      calculatePackageStampAffectedPackages(
        latestProjectDataPackageStamp = mapOf(Path.of("a/b") to 100L, Path.of("a") to 100L),
        latestBuildGraphDataPackageStamp = emptyMap(),
        packagesToUpdate = listOf(Path.of("a/b")),
        projectScope = { true },
      )

    assertThat(affected.modifiedPackages).containsExactly(Path.of("a/b"), Path.of("a"))
  }
}
