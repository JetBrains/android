/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.gradle.config

import com.android.testutils.AssumeUtil
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils
import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.testing.JdkConstants.JDK_1_8_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.intellij.openapi.application.runWriteActionAndWait
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GradleConfigManagerTest: LightPlatformTestCase() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  override fun setUp() {
    super.setUp()
    temporaryFolder.create()
  }

  override fun tearDown() {
    GradleDefaultJdkPathStore.jdkPath = null
    runWriteActionAndWait {
      JdkTableUtils.removeAllSdkFromJdkTable()
    }
    super.tearDown()
  }

  @Test
  fun `test Given undefined default JDK When initialize javaHome Then embedded JDK path was used`() {
    GradleDefaultJdkPathStore.jdkPath = null
    GradleConfigManager.initializeJavaHome(project, temporaryFolder.root.path)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_EMBEDDED_PATH, properties.javaHome?.path)
  }

  @Test
  fun `test Given valid project JDK and valid default JDK When initialize javaHome Then project JDK path was used`() {
    AssumeUtil.assumeNotWindows() // TODO (b/399625141): fix on windows
    setProjectJdk(JDK_EMBEDDED_PATH)
    GradleDefaultJdkPathStore.jdkPath = JDK_1_8_PATH
    GradleConfigManager.initializeJavaHome(project, temporaryFolder.root.path)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_EMBEDDED_PATH, properties.javaHome?.path)
  }

  @Test
  fun `test Given invalid project JDK and valid default JDK When initialize javaHome Then default JDK path was used`() {
    setProjectJdk(JDK_INVALID_PATH)
    GradleDefaultJdkPathStore.jdkPath = JDK_1_8_PATH
    GradleConfigManager.initializeJavaHome(project, temporaryFolder.root.path)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_1_8_PATH, properties.javaHome?.path)
  }

  @Test
  fun `test Given invalid project JDK and invalid default JDK When initialize javaHome Then embedded JDK path was used`() {
    setProjectJdk(JDK_INVALID_PATH)
    GradleDefaultJdkPathStore.jdkPath = JDK_INVALID_PATH
    GradleConfigManager.initializeJavaHome(project, temporaryFolder.root.path)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_EMBEDDED_PATH, properties.javaHome?.path)
  }

  private fun setProjectJdk(homePath: String) {
    runWriteActionAndWait {
      val jdkName = "test"
      JdkTableUtils.populateJdkTableWith(JdkTableUtils.Jdk(jdkName, homePath), temporaryFolder.root)
      val jdk = ProjectJdkTable.getInstance().findJdk(jdkName)
      assertNotNull(jdk)
      assertEquals(jdk?.homePath, homePath)
      ProjectRootManager.getInstance(project).projectSdk = jdk
    }
  }
}