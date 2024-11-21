/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.npw.module

import com.android.ide.common.repository.AgpVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.module.recipes.kotlinMultiplatformLibrary.generateMultiplatformModule
import com.android.tools.idea.templates.recipe.DefaultRecipeExecutor
import com.android.tools.idea.templates.recipe.RenderingContext
import com.android.tools.idea.testing.AgpVersionSoftwareEnvironmentDescriptor
import com.android.tools.idea.testing.AndroidGradleProjectRule
import com.android.tools.idea.testing.TestProjectPaths
import com.android.tools.idea.wizard.template.ApiTemplateData
import com.android.tools.idea.wizard.template.ApiVersion
import com.android.tools.idea.wizard.template.Category
import com.android.tools.idea.wizard.template.FormFactor
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.ModuleTemplateData
import com.android.tools.idea.wizard.template.ProjectTemplateData
import com.android.tools.idea.wizard.template.ThemesData
import com.android.tools.idea.wizard.template.ViewBindingSupport
import com.android.utils.FileUtils
import com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class KotlinMultiplatformModuleTest {

  @get:Rule val projectRule = AndroidGradleProjectRule()

  @get:Rule var tmpFolderRule = TemporaryFolder()

  @Test
  fun generateMultiplatformTemplateWithGradleKts() {

    val rootDir =
      runTemplateGeneration(
        useKts = true,
        projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT,
      )

    val buildGradleContent = rootDir.resolve("build.gradle.kts").readText()
    assertThat(buildGradleContent).isEqualTo(EXPECTED_BUILD_GRADLE_FILE)

    val androidPlatformContent =
      rootDir.resolve("androidMain").resolve("Platform.android.kt").readText()
    assertThat(androidPlatformContent).isEqualTo(EXPECTED_ANDROID_MAIN_CONTENT)

    val commonPlatformContent = rootDir.resolve("commonMain").resolve("Platform.kt").readText()
    assertThat(commonPlatformContent).isEqualTo(EXPECTED_COMMON_MAIN_CONTENT)

    val iosPlatformContent = rootDir.resolve("iosMain").resolve("Platform.ios.kt").readText()
    assertThat(iosPlatformContent).isEqualTo(EXPECTED_IOS_MAIN_CONTENT)

    val androidTestOnJvmContent =
      rootDir.resolve("androidHostTest").resolve("ExampleUnitTest.kt").readText()
    assertThat(androidTestOnJvmContent).isEqualTo(EXPECTED_ANDROID_UNIT_TEST_CONTENT)

    val androidTestOnDeviceContent =
      rootDir.resolve("androidDeviceTest").resolve("ExampleInstrumentedTest.kt").readText()
    assertThat(androidTestOnDeviceContent).isEqualTo(EXPECTED_ANDROID_INSTRUMENTED_TEST_CONTENT)

    val gradlePropertiesContent = rootDir.resolve("gradle.properties").readText()
    assertThat(gradlePropertiesContent)
      .contains("kotlin.native.distribution.downloadFromMaven=true")

    val moduleFiles =
      rootDir
        .walk()
        .filter { !it.isDirectory }
        .map { FileUtils.toSystemIndependentPath(it.relativeTo(rootDir).path) }
        .toList()
    assertThat(moduleFiles).containsExactlyInAnyOrder(*EXPECTED_MODULE_FILES)
  }

  private fun runTemplateGeneration(
    useKts: Boolean,
    projectRuleAgpVersion: AgpVersionSoftwareEnvironmentDescriptor,
  ): File {
    val name = "shared"
    val buildApi =
      ApiVersion(StudioFlags.NPW_COMPILE_SDK_VERSION.get(), StudioFlags.NPW_COMPILE_SDK_VERSION.get().toString())
    val targetApi = buildApi
    val minApi = ApiVersion(34, "34")
    val kotlinVersion = "1.9.20"
    val agpVersion = AgpVersion(8, 3, 0)
    val packageName = "com.kmplib.packagename"
    val androidMainDir = tmpFolderRule.root.resolve("androidMain").also { it.mkdir() }
    val commonMainDir = tmpFolderRule.root.resolve("commonMain").also { it.mkdir() }
    val iosMainDir = tmpFolderRule.root.resolve("iosMain").also { it.mkdir() }
    val rootDir = tmpFolderRule.root

    projectRule.loadProject(
      projectPath = TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM,
      agpVersion = projectRuleAgpVersion,
    )

    val mockProjectTemplateData = mock<ProjectTemplateData>()
    whenever(mockProjectTemplateData.agpVersion).thenReturn(agpVersion)
    whenever(mockProjectTemplateData.rootDir).thenReturn(rootDir)
    val mockModuleTemplateData = mock<ModuleTemplateData>()
    whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)

    val renderingContext =
      RenderingContext(
        project = projectRule.project,
        module = projectRule.getModule(MODULE_NAME_APP),
        commandName = "New Kotlin Multiplatform Module",
        templateData = mockModuleTemplateData,
        outputRoot = rootDir,
        moduleRoot = rootDir,
        dryRun = false,
        showErrors = true,
      )

    val newModuleTemplateData =
      ModuleTemplateData(
        projectTemplateData =
          ProjectTemplateData(
            androidXSupport = true,
            agpVersion = agpVersion,
            sdkDir = null,
            language = Language.Kotlin,
            kotlinVersion = kotlinVersion,
            rootDir = rootDir,
            applicationPackage = packageName,
            includedFormFactorNames = mapOf(),
            debugKeystoreSha1 = null,
            overridePathCheck = null,
            isNewProject = false,
            additionalMavenRepos = listOf(),
          ),
        themesData = ThemesData("appname"),
        apis =
          ApiTemplateData(
            buildApi = buildApi,
            targetApi = targetApi,
            minApi = minApi,
            appCompatVersion = 0,
          ),
        srcDir = androidMainDir,
        resDir = rootDir.resolve("res").also { it.mkdir() },
        manifestDir = rootDir,
        testDir = rootDir.resolve("androidDeviceTest").also { it.mkdir() },
        unitTestDir = rootDir.resolve("androidHostTest").also { it.mkdir() },
        aidlDir = null,
        commonSrcDir = commonMainDir,
        iosSrcDir = iosMainDir,
        rootDir = rootDir,
        isNewModule = true,
        name = name,
        isLibrary = false,
        packageName = packageName,
        formFactor = FormFactor.Generic,
        baseFeature = null,
        viewBindingSupport = ViewBindingSupport.NOT_SUPPORTED,
        category = Category.Application,
        isMaterial3 = true,
        useGenericLocalTests = true,
        useGenericInstrumentedTests = true,
        isCompose = false,
      )

    runWriteCommandAction(projectRule.project) {
      DefaultRecipeExecutor(renderingContext)
        .generateMultiplatformModule(data = newModuleTemplateData, useKts = useKts)
    }

    return rootDir
  }

  companion object {
    private const val MODULE_NAME_APP = "app"

    val EXPECTED_BUILD_GRADLE_FILE =
      """
plugins {
}

    kotlin {

// Target declarations - add or remove as needed below. These define
// which platforms this KMP module supports.
// See: https://kotlinlang.org/docs/multiplatform-discover-project.html#targets
androidLibrary {
  namespace = "com.kmplib.packagename"
  compileSdk = ${StudioFlags.NPW_COMPILE_SDK_VERSION.get()}
  minSdk = 34

  withHostTestBuilder {
  }

  withDeviceTestBuilder {
      sourceSetTreeName = "test"
  }.configure {
    instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
}

// For iOS targets, this is also where you should
// configure native binary output. For more information, see:
// https://kotlinlang.org/docs/multiplatform-build-native-binaries.html#build-xcframeworks

// A step-by-step guide on how to include this library in an XCode
// project can be found here:
// https://developer.android.com/kotlin/multiplatform/migrate
val xcfName = "shared"

iosX64 {
  binaries.framework {
    baseName = xcfName
  }
}

iosArm64 {
  binaries.framework {
    baseName = xcfName
  }
}

iosSimulatorArm64 {
  binaries.framework {
    baseName = xcfName
  }
}

// Source set declarations.
// Declaring a target automatically creates a source set with the same name. By default, the
// Kotlin Gradle Plugin creates additional source sets that depend on each other, since it is
// common to share sources between related targets.
// See: https://kotlinlang.org/docs/multiplatform-hierarchy.html
sourceSets {
  commonMain {
    dependencies {
      // Add KMP dependencies here
    }
  }

  commonTest {
    dependencies {
    }
  }

  androidMain {
    dependencies {
      // Add Android-specific dependencies here. Note that this source set depends on
      // commonMain by default and will correctly pull the Android artifacts of any KMP
      // dependencies declared in commonMain.
    }
  }

  getByName("androidDeviceTest") {
    dependencies {
    }
  }

  iosMain {
    dependencies {
      // Add iOS-specific dependencies here. This a source set created by Kotlin Gradle
      // Plugin (KGP) that each specific iOS target (e.g., iosX64) depends on as
      // part of KMP’s default source set hierarchy. Note that this source set depends
      // on common by default and will correctly pull the iOS artifacts of any
      // KMP dependencies declared in commonMain.
    }
  }
}

}
"""
        .trimIndent()

    val EXPECTED_ANDROID_MAIN_CONTENT =
      """
package com.kmplib.packagename

  actual fun platform() = "Android"
    """
        .trimIndent()

    val EXPECTED_COMMON_MAIN_CONTENT =
      """
package com.kmplib.packagename

  expect fun platform(): String
    """
        .trimIndent()

    val EXPECTED_IOS_MAIN_CONTENT =
      """
package com.kmplib.packagename

  actual fun platform() = "iOS"
    """
        .trimIndent()

    val EXPECTED_ANDROID_UNIT_TEST_CONTENT =
      """
package com.kmplib.packagename

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
    """
        .trimIndent()

    val EXPECTED_ANDROID_INSTRUMENTED_TEST_CONTENT =
      """
package com.kmplib.packagename

  import androidx.test.platform.app.InstrumentationRegistry
  import androidx.test.ext.junit.runners.AndroidJUnit4

  import org.junit.Test
  import org.junit.runner.RunWith

  import org.junit.Assert.*

  /**
   * Instrumented test, which will execute on an Android device.
   *
   * See [testing documentation](http://d.android.com/tools/testing).
   */
  @RunWith(AndroidJUnit4::class)
  class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
      // Context of the app under test.
      val appContext = InstrumentationRegistry.getInstrumentation().targetContext
      assertEquals("com.kmplib.packagename.test", appContext.packageName)
    }
  }
    """
        .trimIndent()

    val EXPECTED_MODULE_FILES =
      arrayOf(
        "AndroidManifest.xml",
        "commonMain/Platform.kt",
        "androidHostTest/ExampleUnitTest.kt",
        "androidDeviceTest/ExampleInstrumentedTest.kt",
        "build.gradle.kts",
        ".gitignore",
        "androidMain/Platform.android.kt",
        "iosMain/Platform.ios.kt",
        "gradle.properties",
      )
  }
}
