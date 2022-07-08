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

class RootBuildRelativeGradleProjectPathTest {

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  private fun makePath(buildName: String, projectPath: String) = RootBuildRelativeGradleProjectPath(File("/"), buildName, projectPath)

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
}