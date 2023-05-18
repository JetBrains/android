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

import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidEnvironmentVariableJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidEnvironmentVariableStudioGradleJdk
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradleJvmTableEntryJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradleLocalJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.InvalidGradlePropertiesJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedEnvironmentVariableJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedEnvironmentVariableStudioGradleJdk
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradleJvmTableEntry
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradleJvmTableEntryJavaHome
import com.android.tools.idea.gradle.project.sync.jdk.exceptions.cause.InvalidGradleJdkCause.UndefinedGradlePropertiesJavaHome
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest
import com.android.tools.idea.gradle.project.sync.snapshots.JdkIntegrationTest.TestEnvironment
import com.android.tools.idea.gradle.project.sync.snapshots.JdkTestProject.SimpleApplication
import com.android.tools.idea.gradle.project.sync.utils.JdkTableUtils.Jdk
import com.android.tools.idea.sdk.IdeSdks.JDK_LOCATION_ENV_VARIABLE_NAME
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule
import com.android.tools.idea.testing.JdkConstants.JDK_INVALID_PATH
import com.google.common.truth.Expect
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkException
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.JAVA_HOME
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil.USE_JAVA_HOME
import com.intellij.testFramework.RunsInEdt
import org.jetbrains.plugins.gradle.util.USE_GRADLE_JAVA_HOME
import org.jetbrains.plugins.gradle.util.USE_GRADLE_LOCAL_JAVA_HOME
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.io.path.Path

@RunsInEdt
@Suppress("UnstableApiUsage")
class GradleSyncInvalidJdkConfigurationIntegrationTest {

  @get:Rule
  val projectRule: IntegrationTestEnvironmentRule = AndroidProjectRule.withIntegrationTestEnvironment()

  @get:Rule
  val expect: Expect = Expect.createAndEnableStackTrace()

  @get:Rule
  val temporaryFolder = TemporaryFolder()

  private val jdkIntegrationTest = JdkIntegrationTest(projectRule, temporaryFolder, expect)

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with invalid java home property When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = JDK_INVALID_PATH
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradleLocalJavaHome(Path(JDK_INVALID_PATH)))
        }
      )
    }

  @Test
  fun `Given gradleJdk GRADLE_LOCAL_JAVA_HOME with empty java home property When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_LOCAL_JAVA_HOME,
        gradleLocalJavaHome = ""
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradleLocalJavaHome(Path("")))
        }
      )
    }

  @Test
  fun `Given gradleJdk USE_GRADLE_JAVA_HOME without java home property When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_JAVA_HOME
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(UndefinedGradlePropertiesJavaHome)
        }
      )
    }

  @Test
  fun `Given gradleJdk USE_GRADLE_JAVA_HOME with invalid java home property When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_GRADLE_JAVA_HOME,
        gradlePropertiesJavaHome = JDK_INVALID_PATH
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradlePropertiesJavaHome(Path(JDK_INVALID_PATH)))
        }
      )
    }

  @Test
  fun `Given gradleJdk USE_JAVA_HOME without environment variable java home When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME,
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JAVA_HOME to null)
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(UndefinedEnvironmentVariableJavaHome)
        }
      )
    }

  @Test
  fun `Given gradleJdk USE_JAVA_HOME with invalid environment variable java home When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = USE_JAVA_HOME
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JAVA_HOME to JDK_INVALID_PATH)
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidEnvironmentVariableJavaHome(Path(JDK_INVALID_PATH)))
        }
      )
    }

  @Test
  fun `Given gradleJdk STUDIO_GRADLE_JDK without environment variable jdk path When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_LOCATION_ENV_VARIABLE_NAME
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JDK_LOCATION_ENV_VARIABLE_NAME to null)
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(UndefinedEnvironmentVariableStudioGradleJdk)
        }
      )
    }

  @Test
  fun `Given gradleJdk STUDIO_GRADLE_JDK with invalid environment variable jdk path When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = JDK_LOCATION_ENV_VARIABLE_NAME
      ),
      environment = TestEnvironment(
        environmentVariables = mapOf(JDK_LOCATION_ENV_VARIABLE_NAME to JDK_INVALID_PATH)
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidEnvironmentVariableStudioGradleJdk(Path(JDK_INVALID_PATH)))
        }
      )
    }

  @Test
  fun `Given gradleJdk using undefined jdk table entry name When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = "undefined-jdk-table-entry",
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(UndefinedGradleJvmTableEntry("undefined-jdk-table-entry"))
        }
      )
    }

  @Test
  fun `Given gradleJdk using defined jdk table entry name without path When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = "undefined-jdk-table-path",
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("undefined-jdk-table-path", null))
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(UndefinedGradleJvmTableEntryJavaHome("undefined-jdk-table-path"))
        }
      )
    }

  @Test
  fun `Given gradleJdk using defined jdk table entry name with invalid path When sync project Then throw exception with expected message`() =
    jdkIntegrationTest.run(
      project = SimpleApplication(
        ideaGradleJdk = "invalid-jdk-table-entry",
      ),
      environment = TestEnvironment(
        jdkTable = listOf(Jdk("invalid-jdk-table-entry", JDK_INVALID_PATH))
      )
    ) {
      sync(
        assertOnFailure = {
          assertException(ExternalSystemJdkException::class)
        },
        assertSyncEvents = {
          assertInvalidGradleJdkMessage(InvalidGradleJvmTableEntryJavaHome(Path(JDK_INVALID_PATH), "invalid-jdk-table-entry"))
        }
      )
    }
}