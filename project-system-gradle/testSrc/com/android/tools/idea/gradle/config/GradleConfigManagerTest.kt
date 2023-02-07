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

import com.android.tools.idea.gradle.util.GradleConfigProperties
import com.android.tools.idea.sdk.GradleDefaultJdkPathStore
import com.android.tools.idea.testing.JdkConstants.JDK_1_8_PATH
import com.android.tools.idea.testing.JdkConstants.JDK_EMBEDDED_PATH
import com.intellij.testFramework.LightPlatformTestCase
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class GradleConfigManagerTest: LightPlatformTestCase() {

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  override fun setUp() {
    super.setUp()
    temporaryFolder.create()
  }

  override fun tearDown() {
    GradleDefaultJdkPathStore.jdkPath = null
    super.tearDown()
  }

  fun `test Given undefined default JDK When initialize jdkDir Then embedded JDK path was used`() {
    GradleDefaultJdkPathStore.jdkPath = null
    GradleConfigManager.initializeJavaHome(temporaryFolder.root)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_EMBEDDED_PATH, properties.javaHome?.path)
  }

  fun `test Given invalid default JDK When initialize jdkDir Then embedded JDK path was used`() {
    GradleDefaultJdkPathStore.jdkPath = "invalid/jdk/path"
    GradleConfigManager.initializeJavaHome(temporaryFolder.root)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_EMBEDDED_PATH, properties.javaHome?.path)
  }

  fun `test Given valid default JDK When initialize jdkDir Then default JDK path was used`() {
    GradleDefaultJdkPathStore.jdkPath = JDK_1_8_PATH
    GradleConfigManager.initializeJavaHome(temporaryFolder.root)

    val properties = GradleConfigProperties(temporaryFolder.root)
    assertEquals(JDK_1_8_PATH, properties.javaHome?.path)
  }
}