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
package com.android.tools.idea.run.deployment.liveedit

import com.android.tools.idea.testing.AndroidProjectRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import junit.framework.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PrebuildChecksTest {
  private lateinit var myProject: Project

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory()

  @Before
  fun setUp() {
    myProject = projectRule.project
  }

  @Test
  fun bailOnBuildSrc() {
    var file = projectRule.fixture.addFileToProject(
      "buildSrc/src/java/com/example/Version.kt", "package com.example\n class Version {}")
    try {
      checkSupportedFiles(file)
      Assert.fail("Expecting Exception")
    }
    catch (e: LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.UNSUPPORTED_BUILD_SRC_CHANGE, e.error)
    }
  }

  @Test
  fun bailOnGradleSource() {
    var file = projectRule.fixture.addFileToProject(
      "build.gradle.kts", "plugins {}")
    try {
      checkSupportedFiles(file)
      Assert.fail("Expecting Exception")
    } catch (e : LiveEditUpdateException) {
      Assert.assertEquals(LiveEditUpdateException.Error.GRADLE_BUILD_FILE, e.error)
    }
  }

  /**
   * Legacy check for non-compose module check. This is now supported and should not expect an exception.
   */
  @Test
  fun testNonComposeModule() {
    var file = projectRule.fixture.addFileToProject(
      "src/java/com/example/NonCompose.kt", "package com.example\n class NonCompose {}")
    ApplicationManager.getApplication().runReadAction {
      prebuildChecks(myProject, listOf(file))
    }
  }
}