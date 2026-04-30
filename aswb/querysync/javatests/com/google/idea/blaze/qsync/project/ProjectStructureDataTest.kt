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
                  Path.of("java/com/example") to BuildPackage(Path.of("java/com/example"), emptyList()),
                  Path.of("java/com/example/sub") to BuildPackage(Path.of("java/com/example/sub"), emptyList()),
                ),
            )
          ),
        activeLanguages = emptySet(),
      )

    assertThat(data.getBuildPackage(Path.of("java/com/example/File.java"))?.path).isEqualTo(Path.of("java/com/example"))
    assertThat(data.getBuildPackage(Path.of("java/com/example/sub/File.java"))?.path).isEqualTo(Path.of("java/com/example/sub"))
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
              buildPackages = mapOf(Path.of("java/com/example") to BuildPackage(Path.of("java/com/example"), emptyList())),
            )
          ),
        activeLanguages = emptySet(),
      )

    assertThat(data.pathToLabel(Path.of("java/com/example/File.java"))).isEqualTo(Label.of("//java/com/example:File.java"))
    assertThat(data.pathToLabel(Path.of("java/com/example/sub/File.java"))).isEqualTo(Label.of("//java/com/example:sub/File.java"))
    assertThat(data.pathToLabel(Path.of("java/File.java"))).isNull()
  }
}
