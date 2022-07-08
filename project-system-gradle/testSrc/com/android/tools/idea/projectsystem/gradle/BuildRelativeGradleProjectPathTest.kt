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
package com.android.tools.idea.projectsystem.gradle

import com.google.common.truth.Expect
import org.junit.Rule

import org.junit.Test
import java.io.File

class BuildRelativeGradleProjectPathTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private fun makePath(rootBuildId: String = "/project", buildName: String, projectPath: String): BuildRelativeGradleProjectPath {
    return BuildRelativeGradleProjectPath(
      rootBuildId = File(rootBuildId),
      buildName = buildName,
      gradleProjectPath = projectPath
    )
  }

  private fun makeCompositeBuildMap(gradleSupportsDirectTaskInvocation: Boolean): CompositeBuildMap {
    return object : CompositeBuildMap {
      override fun buildIdToName(buildId: File): String {
        return when (buildId.path) {
          "/project" -> ":"
          "/project/included" -> "included"
          else -> error("Unexpected build id: $buildId")
        }
      }

      override fun buildNameToId(buildName: String): File {
        return when (buildName) {
          ":" -> File("/project")
          "included" -> File("/project/included")
          else -> error("Unexpected build name: $buildName")
        }
      }

      override val gradleSupportsDirectTaskInvocation: Boolean = gradleSupportsDirectTaskInvocation
    }
  }

  @Test
  fun buildNamePrefixedGradleProjectPath() {
    expect.that(makePath(buildName = ":", projectPath = ":").buildNamePrefixedGradleProjectPath())
      .isEqualTo(":")

    expect.that(makePath(buildName = ":", projectPath = ":apps:app").buildNamePrefixedGradleProjectPath())
      .isEqualTo(":apps:app")

    expect.that(makePath(buildName = "included", projectPath = ":").buildNamePrefixedGradleProjectPath())
      .isEqualTo(":included")

    expect.that(makePath(buildName = "included", projectPath = ":apps:app").buildNamePrefixedGradleProjectPath())
      .isEqualTo(":included:apps:app")
  }

  @Test
  fun translateToBuildAndRelativeProjectPath() {
    with(makeCompositeBuildMap(gradleSupportsDirectTaskInvocation = true)) {
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project", ":")))
        .isEqualTo(makePath(buildName = ":", projectPath = ":"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project", ":app")))
        .isEqualTo(makePath(buildName = ":", projectPath = ":app"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project/included", ":")))
        .isEqualTo(makePath(buildName = "included", projectPath = ":"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project/included", ":lib")))
        .isEqualTo(makePath(buildName = "included", projectPath = ":lib"))
    }
    // Gradle versions before 6.8 do not support invoking tasks from included builds directly.
    with(makeCompositeBuildMap(gradleSupportsDirectTaskInvocation = false)) {
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project", ":")))
        .isEqualTo(makePath(rootBuildId = "/project", buildName = ":", projectPath = ":"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project", ":app")))
        .isEqualTo(makePath(rootBuildId = "/project", buildName = ":", projectPath = ":app"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project/included", ":")))
        .isEqualTo(makePath(rootBuildId = "/project/included", buildName = ":", projectPath = ":"))
      expect.that(translateToBuildAndRelativeProjectPath(GradleHolderProjectPath("/project/included", ":lib")))
        .isEqualTo(makePath(rootBuildId = "/project/included", buildName = ":", projectPath = ":lib"))
    }
  }
}