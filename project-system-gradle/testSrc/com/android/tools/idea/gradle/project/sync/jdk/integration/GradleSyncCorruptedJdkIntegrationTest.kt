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
package com.android.tools.idea.gradle.project.sync.jdk.integration

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradleLocalJavaHome
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.utils.FileUtils
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertTrue

@RunsInEdt
@Suppress("UnstableApiUsage")
class GradleSyncCorruptedJdkIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)
  private lateinit var jdkTemporaryDir: File

  @Before
  fun setUp() {
    val embeddedJdkDir = IdeSdks.getInstance().embeddedJdkPath.toFile()
    jdkTemporaryDir = temporaryFolder.newFolder("jdkTemporary")
    FileUtils.copyDirectory(embeddedJdkDir, jdkTemporaryDir)
  }

  @Test
  fun `Given a project using JDK without java executable When sync project Then throw exception with expected message`() {
    deleteExecutableFromJdk(jdkTemporaryDir, "java")
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = jdkTemporaryDir.absolutePath
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradleLocalJavaHome(jdkTemporaryDir.toPath()))
        }
      )
    }
  }

  @Test
  fun `Given a project using JDK without javac executable When sync project Then throw exception with expected message`() {
    deleteExecutableFromJdk(jdkTemporaryDir, "javac")
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = jdkTemporaryDir.absolutePath
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradleLocalJavaHome(jdkTemporaryDir.toPath()))
        }
      )
    }
  }

  private fun deleteExecutableFromJdk(jdkDir: File, name: String) {
    val jdkBinDir = jdkDir.resolve("bin")
    val executableName = if (SystemInfo.isWindows) "{$name}.exe" else name
    assertTrue(jdkBinDir.resolve(executableName).delete())
  }
}
