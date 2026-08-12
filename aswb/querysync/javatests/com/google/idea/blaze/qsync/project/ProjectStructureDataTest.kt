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
package com.google.idea.blaze.qsync.project

import com.google.common.truth.Truth.assertThat
import com.google.idea.blaze.common.Label
import java.nio.file.Path
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ProjectStructureDataTest {

  @Test
  fun testGetProjectStructureRoot() {
    val data =
      ProjectStructureData.create(
        roots = listOf(ProjectStructureRoot(Path.of("java"), emptyMap()), ProjectStructureRoot(Path.of("java/com/google"), emptyMap())),
        activeLanguages = emptySet(),
      )

    assertThat(data.getProjectStructureRoot(Path.of("java/File.java"))?.projectStructureRootPath).isEqualTo(Path.of("java"))
    assertThat(data.getProjectStructureRoot(Path.of("java/com/google/File.java"))?.projectStructureRootPath)
      .isEqualTo(Path.of("java/com/google")) // Most specific match
    assertThat(data.getProjectStructureRoot(Path.of("other/File.java"))).isNull()
  }

  @Test
  fun testGetBuildPackage() {
    val data =
      ProjectStructureData.create(
        roots =
          listOf(
            ProjectStructureRoot(
              Path.of("java"),
              buildPackages =
                mapOf(
                  Path.of("java/com/example") to BuildPackage(Path.of("java/com/example"), emptyList(), 100L),
                  Path.of("java/com/example/sub") to BuildPackage(Path.of("java/com/example/sub"), emptyList(), 200L),
                ),
            )
          ),
        activeLanguages = emptySet(),
      )

    assertThat(data.getBuildPackage(Path.of("java/com/example/File.java"))?.path).isEqualTo(Path.of("java/com/example"))
    assertThat(data.getBuildPackage(Path.of("java/com/example/File.java"))?.stamp).isEqualTo(100L)
    assertThat(data.getBuildPackage(Path.of("java/com/example/sub/File.java"))?.path).isEqualTo(Path.of("java/com/example/sub"))
    assertThat(data.getBuildPackage(Path.of("java/com/example/sub/File.java"))?.stamp).isEqualTo(200L)
    assertThat(data.getBuildPackage(Path.of("java/com/example/othersub/File.java"))?.path)
      .isEqualTo(Path.of("java/com/example")) // Ancestor match
    assertThat(data.getBuildPackage(Path.of("java/File.java"))).isNull() // No build package ancestor
  }

  @Test
  fun testPathToLabel() {
    val data =
      ProjectStructureData.create(
        roots =
          listOf(
            ProjectStructureRoot(
              Path.of("java"),
              buildPackages = mapOf(Path.of("java/com/example") to BuildPackage(Path.of("java/com/example"), emptyList(), 123L)),
            )
          ),
        activeLanguages = emptySet(),
      )

    assertThat(data.pathToLabel(Path.of("java/com/example/File.java"))).isEqualTo(Label.of("//java/com/example:File.java"))
    assertThat(data.pathToLabel(Path.of("java/com/example/sub/File.java"))).isEqualTo(Label.of("//java/com/example:sub/File.java"))
    assertThat(data.pathToLabel(Path.of("java/File.java"))).isNull()
  }

  @Test
  fun testComputePackageStamp_determinism() {
    val sourceSets =
      listOf(
        SourceSet(
          rootPath = Path.of("pkg"),
          javaSourceFiles = listOf(Path.of("A.java"), Path.of("B.java")),
          nonJavaSourceFiles = listOf(Path.of("proto.proto")),
          javaPackage = "com.example",
        )
      )
    val subpackages = listOf(Path.of("pkg/sub1"), Path.of("pkg/sub2"))

    val stamp1 = computePackageStamp(buildFileTimestamp = 123456789L, sourceSets = sourceSets, directSubpackages = subpackages)
    val stamp2 = computePackageStamp(buildFileTimestamp = 123456789L, sourceSets = sourceSets, directSubpackages = subpackages)

    assertThat(stamp1).isEqualTo(stamp2)
    assertThat(stamp1).isNotEqualTo(0L)
  }

  @Test
  fun testComputePackageStamp_canonicalOrderStability() {
    val ss1 =
      SourceSet(
        rootPath = Path.of("pkg/a"),
        javaSourceFiles = listOf(Path.of("Z.java"), Path.of("A.java")),
        nonJavaSourceFiles = listOf(Path.of("z.proto"), Path.of("a.proto")),
        javaPackage = "com.example.a",
      )
    val ss2 =
      SourceSet(
        rootPath = Path.of("pkg/b"),
        javaSourceFiles = listOf(Path.of("Y.java"), Path.of("B.java")),
        nonJavaSourceFiles = listOf(Path.of("y.proto"), Path.of("b.proto")),
        javaPackage = "com.example.b",
      )

    // Order 1: ss1 then ss2, unordered inner lists, unordered subpackages
    val stamp1 =
      computePackageStamp(
        buildFileTimestamp = 1000L,
        sourceSets = listOf(ss1, ss2),
        directSubpackages = listOf(Path.of("pkg/z"), Path.of("pkg/a")),
      )

    // Order 2: ss2 then ss1, reverse inner lists, reverse subpackages
    val ss1Permuted =
      SourceSet(
        rootPath = Path.of("pkg/a"),
        javaSourceFiles = listOf(Path.of("A.java"), Path.of("Z.java")),
        nonJavaSourceFiles = listOf(Path.of("a.proto"), Path.of("z.proto")),
        javaPackage = "com.example.a",
      )
    val ss2Permuted =
      SourceSet(
        rootPath = Path.of("pkg/b"),
        javaSourceFiles = listOf(Path.of("B.java"), Path.of("Y.java")),
        nonJavaSourceFiles = listOf(Path.of("b.proto"), Path.of("y.proto")),
        javaPackage = "com.example.b",
      )
    val stamp2 =
      computePackageStamp(
        buildFileTimestamp = 1000L,
        sourceSets = listOf(ss2Permuted, ss1Permuted),
        directSubpackages = listOf(Path.of("pkg/a"), Path.of("pkg/z")),
      )

    assertThat(stamp1).isEqualTo(stamp2)
  }

  @Test
  fun testComputePackageStamp_sensitivityToTimestamp() {
    val sourceSets = listOf(SourceSet(rootPath = Path.of("pkg"), javaPackage = "com.example"))
    val stamp1 = computePackageStamp(buildFileTimestamp = 1000L, sourceSets = sourceSets, directSubpackages = emptyList())
    val stamp2 = computePackageStamp(buildFileTimestamp = 2000L, sourceSets = sourceSets, directSubpackages = emptyList())

    assertThat(stamp1).isNotEqualTo(stamp2)
  }

  @Test
  fun testComputePackageStamp_sensitivityToSources() {
    val ss1 = SourceSet(rootPath = Path.of("pkg"), javaSourceFiles = listOf(Path.of("A.java")), javaPackage = "com.example")
    val ss2 =
      SourceSet(rootPath = Path.of("pkg"), javaSourceFiles = listOf(Path.of("A.java"), Path.of("B.java")), javaPackage = "com.example")

    val stamp1 = computePackageStamp(buildFileTimestamp = 1000L, sourceSets = listOf(ss1), directSubpackages = emptyList())
    val stamp2 = computePackageStamp(buildFileTimestamp = 1000L, sourceSets = listOf(ss2), directSubpackages = emptyList())

    assertThat(stamp1).isNotEqualTo(stamp2)
  }

  @Test
  fun testComputePackageStamp_sensitivityToSubpackages() {
    val sourceSets = listOf(SourceSet(rootPath = Path.of("pkg"), javaPackage = "com.example"))
    val stamp1 = computePackageStamp(buildFileTimestamp = 1000L, sourceSets = sourceSets, directSubpackages = emptyList())
    val stamp2 = computePackageStamp(buildFileTimestamp = 1000L, sourceSets = sourceSets, directSubpackages = listOf(Path.of("pkg/sub")))

    assertThat(stamp1).isNotEqualTo(stamp2)
  }
}
