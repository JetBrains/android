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

import com.android.tools.idea.gradle.model.IdeModuleWellKnownSourceSet
import com.android.tools.idea.gradle.model.impl.IdeModuleSourceSetImpl
import com.google.common.truth.Expect
import org.junit.Rule
import org.junit.Test
import java.io.File

class GradleProjectPathTest {
  private val buildRoot = "/foo"

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @Test
  fun `simple root`() {
    expect.that(createGradleProjectPath("project", false, File(buildRoot)))
      .isEqualTo(GradleHolderProjectPath(buildRoot, ":"))
    expect.that(createGradleProjectPath("project:main", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleWellKnownSourceSet.MAIN))
    expect.that(createGradleProjectPath("project:androidTest", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleWellKnownSourceSet.ANDROID_TEST))
    expect.that(createGradleProjectPath("project:other", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleSourceSetImpl("other", true)))
  }

  @Test
  fun `simple child`() {
    expect.that(createGradleProjectPath(":app", false, File(buildRoot)))
      .isEqualTo(GradleHolderProjectPath(buildRoot, ":app"))
    expect.that(createGradleProjectPath(":app:main", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleWellKnownSourceSet.MAIN))
    expect.that(createGradleProjectPath(":app:androidTest", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleWellKnownSourceSet.ANDROID_TEST))
    expect.that(createGradleProjectPath(":app:other", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleSourceSetImpl("other", true)))
  }

  @Test
  fun `composite nested root`() {
    // Note: this is supposed to be a copy of `simple root`.
    expect.that(createGradleProjectPath("project", false, File(buildRoot)))
      .isEqualTo(GradleHolderProjectPath(buildRoot, ":"))
    expect.that(createGradleProjectPath("project:main", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleWellKnownSourceSet.MAIN))
    expect.that(createGradleProjectPath("project:androidTest", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleWellKnownSourceSet.ANDROID_TEST))
    expect.that(createGradleProjectPath("project:other", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":", IdeModuleSourceSetImpl("other", true)))
  }

  @Test
  fun `composite nested child`() {
    expect.that(createGradleProjectPath("project:app", false, File(buildRoot)))
      .isEqualTo(GradleHolderProjectPath(buildRoot, ":app"))
    expect.that(createGradleProjectPath("project:app:main", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleWellKnownSourceSet.MAIN))
    expect.that(createGradleProjectPath("project:app:androidTest", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleWellKnownSourceSet.ANDROID_TEST))
    expect.that(createGradleProjectPath("project:app:other", true, File(buildRoot)))
      .isEqualTo(GradleSourceSetProjectPath(buildRoot, ":app", IdeModuleSourceSetImpl("other", true)))
  }
}