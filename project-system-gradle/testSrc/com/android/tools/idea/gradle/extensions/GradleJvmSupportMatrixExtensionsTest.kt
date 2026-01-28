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
package com.android.tools.idea.gradle.extensions

import com.android.tools.idea.gradle.util.CompatibleGradleVersion
import com.android.tools.idea.jdk.JavaVersionLts
import com.android.tools.idea.sdk.IdeSdks
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.lang.JavaVersion
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.jvmcompat.GradleJvmSupportMatrix
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class GradleJvmSupportMatrixExtensionsTest(private val gradleVersion: GradleVersion) : LightPlatformTestCase() {

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun data(): List<GradleVersion> {
      return CompatibleGradleVersion.entries.map { it.version }
    }
  }

  override fun setUp() {
    super.setUp()
    IdeSdks.removeJdksOn(testRootDisposable)
  }

  @Test
  fun `Gradle recommended java version is project JDK when compatible`() {
    val jdk9 = IdeaTestUtil.getMockJdk9()

    ExternalSystemApiUtil.executeProjectChangeAction(true, project) {
      ProjectJdkTable.getInstance().addJdk(IdeaTestUtil.getMockJdk9())
      JavaSdkUtil.applyJdkToProject(project, jdk9)
    }

    val recommendedJdkVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(project, gradleVersion)
    val expectedProjectJdkVersion = JavaSdk.getInstance().getVersion(jdk9)!!.maxLanguageLevel.toJavaVersion()
    if (GradleJvmSupportMatrix.isSupported(gradleVersion, expectedProjectJdkVersion)) {
      assertEquals(expectedProjectJdkVersion, recommendedJdkVersion)
    } else {
      assertEquals(gradleVersion.expectedLtsVersion, recommendedJdkVersion)
    }
  }

  @Test
  fun `Gradle recommended java version considering only LTS`() {
    val recommendedJdkVersion = GradleJvmSupportMatrix.getRecommendedJavaVersion(project, gradleVersion)
    assertEquals(gradleVersion.expectedLtsVersion, recommendedJdkVersion)
  }

  private val GradleVersion.expectedLtsVersion: JavaVersion
    get() =
      GradleJvmSupportMatrix.getSupportedJavaVersions(this)
        .filter { it.feature <= IdeSdks.DEFAULT_JDK_VERSION.maxLanguageLevel.feature() }
        .last { JavaVersionLts.isLtsVersion(it) }
}
