/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.upgrade

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.gradle.extensions.getPropertyPath
import com.android.tools.idea.gradle.toolchain.GradleDaemonJvmCriteriaTemplatesManager
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.intellij.openapi.project.guessProjectDir
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.VfsTestUtil
import com.intellij.util.lang.JavaVersion
import org.jetbrains.plugins.gradle.properties.GradleDaemonJvmPropertiesFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.io.path.readText

/*
 * Copyright (C) 2025 The Android Open Source Project
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

@RunsInEdt
class GradleDaemonJvmCriteriaRefactoringProcessorTest {

  @get:Rule
  val projectRule = AndroidProjectRule.withSdk().onEdt()

  val project by lazy { projectRule.project }

  @Test
  fun `Given project with incompatible Daemon JVM criteria When running AUA Then upgrade criteria is required`() {
    createDaemonJvmPropertiesFile("11")

    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.11.0")).run {
      run()
      assertDaemonJvmCriteriaFor(21)
    }
  }

  @Test
  fun `Given project with invalid Daemon JVM criteria When running AUA Then upgrade criteria is required`() {
    createDaemonJvmPropertiesFile("invalid")

    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.11.0")).run {
      run()
      assertDaemonJvmCriteriaFor(21)
    }
  }

  @Test
  fun `Given project empty Daemon JVM criteria When running AUA Then upgrade criteria is required`() {
    createDaemonJvmPropertiesFile(null)

    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.11.0")).run {
      run()
      assertDaemonJvmCriteriaFor(21)
    }
  }

  @Test
  fun `Given project with incompatible Daemon JVM criteria but not supported criteria recommended Gradle version When running AUA Then upgrade criteria isn't required`() {
    createDaemonJvmPropertiesFile("11")

    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.0.0")).run {
      assertRefactorProcessorIsNoOp()
    }
  }

  @Test
  fun `Given project with compatible Daemon JVM criteria When running AUA Then upgrade criteria isn't required`() {
    createDaemonJvmPropertiesFile("17")

    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.11.0")).run {
      assertRefactorProcessorIsNoOp()
    }
  }

  @Test
  fun `Given project without Daemon JVM criteria When running AUA Then upgrade criteria isn't required`() {
    GradleDaemonJvmCriteriaRefactoringProcessor(project, AgpVersion.parse("7.0.0"), AgpVersion.parse("8.11.0")).run {
      assertRefactorProcessorIsNoOp()
    }
  }

  private fun createDaemonJvmPropertiesFile(version: String?) {
    VfsTestUtil.createFile(project.guessProjectDir()!!, "gradle/gradle-daemon-jvm.properties", version?.let { "toolchainVersion=$version" }.orEmpty())
  }

  private fun AgpUpgradeComponentRefactoringProcessor.assertRefactorProcessorIsNoOp() {
    assertTrue(findUsages().isEmpty())
  }

  private fun assertDaemonJvmCriteriaFor(version: Int) {
    val currentProperties = GradleDaemonJvmPropertiesFile.getPropertyPath(project.basePath!!).readText()
    val expectedTemplateProperties = GradleDaemonJvmCriteriaTemplatesManager.getTemplateCriteriaPropertiesContent(JavaVersion.parse(version.toString()))
    assertEquals(expectedTemplateProperties, currentProperties)
  }
}