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
import com.android.sdklib.SdkVersionInfo
import com.android.testutils.MockitoKt
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
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class KotlinMultiplatformModuleTest {

  @get:Rule
  val projectRule = AndroidGradleProjectRule()

  @get:Rule
  var tmpFolderRule = TemporaryFolder()

  @Test
  fun generateMultiplatformTemplateWithGradleKts() {

    val rootDir = runTemplateGeneration(
      useKts = true,
      projectRuleAgpVersion = AgpVersionSoftwareEnvironmentDescriptor.AGP_CURRENT
    )

    val buildGradleContent = rootDir.resolve("build.gradle.kts").readText()
    assertThat(buildGradleContent).isEqualTo(EXPECTED_BUILD_GRADLE_FILE)

    val androidPlatformContent = rootDir.resolve("androidMain").resolve("AndroidPlatform.kt").readText()
    assertThat(androidPlatformContent).isEqualTo(EXPECTED_ANDROID_MAIN_CONTENT)

    val commonPlatformContent = rootDir.resolve("commonMain").resolve("Platform.kt").readText()
    assertThat(commonPlatformContent).isEqualTo(EXPECTED_COMMON_MAIN_CONTENT)

    val androidUnitTestContent = rootDir.resolve("androidUnitTest").resolve("ExampleUnitTest.kt").readText()
    assertThat(androidUnitTestContent).isEqualTo(EXPECTED_ANDROID_UNIT_TEST_CONTENT)

    val androidInstrumentedTestContent = rootDir.resolve("androidInstrumentedTest").resolve("ExampleInstrumentedTest.kt").readText()
    assertThat(androidInstrumentedTestContent).isEqualTo(EXPECTED_ANDROID_INSTRUMENTED_TEST_CONTENT)

    val moduleFiles = rootDir
      .walk()
      .filter { !it.isDirectory }
      .map { FileUtils.toSystemIndependentPath(it.relativeTo(rootDir).path) }
      .toList()
    assertThat(moduleFiles).containsExactlyInAnyOrder(*EXPECTED_MODULE_FILES)
  }

  private fun runTemplateGeneration(
    useKts: Boolean,
    projectRuleAgpVersion: AgpVersionSoftwareEnvironmentDescriptor
  ): File {
    val name = "kmplibrary"
    val buildApi = ApiVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
    val targetApi = ApiVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
    val minApi = ApiVersion(SdkVersionInfo.HIGHEST_KNOWN_STABLE_API, SdkVersionInfo.HIGHEST_KNOWN_STABLE_API.toString())
    val kotlinVersion = "1.9.20"
    val agpVersion = AgpVersion(8, 3, 0)
    val packageName = "com.kmplib.packagename"
    val androidMainDir = tmpFolderRule.root.resolve("androidMain").also { it.mkdir() }
    val commonMainDir = tmpFolderRule.root.resolve("commonMain").also { it.mkdir() }
    val rootDir = tmpFolderRule.root

    projectRule.loadProject(projectPath = TestProjectPaths.ANDROID_KOTLIN_MULTIPLATFORM, agpVersion = projectRuleAgpVersion)

    val mockProjectTemplateData = MockitoKt.mock<ProjectTemplateData>()
    MockitoKt.whenever(mockProjectTemplateData.agpVersion).thenReturn(agpVersion)
    val mockModuleTemplateData = MockitoKt.mock<ModuleTemplateData>()
    MockitoKt.whenever(mockModuleTemplateData.projectTemplateData).thenReturn(mockProjectTemplateData)

    val renderingContext = RenderingContext(
      project = projectRule.project,
      module = projectRule.getModule(MODULE_NAME_APP),
      commandName = "New Kotlin Multiplatform Module",
      templateData = mockModuleTemplateData,
      outputRoot = rootDir,
      moduleRoot = rootDir,
      dryRun = false,
      showErrors = true
    )

    val newModuleTemplateData = ModuleTemplateData(
      projectTemplateData = ProjectTemplateData(
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
      apis = ApiTemplateData(
        buildApi = buildApi,
        targetApi = targetApi,
        minApi = minApi,
        appCompatVersion = 0
      ),
      srcDir = androidMainDir,
      resDir = rootDir.resolve("res").also { it.mkdir() },
      manifestDir = rootDir,
      testDir = rootDir.resolve("androidInstrumentedTest").also { it.mkdir() },
      unitTestDir = rootDir.resolve("androidUnitTest").also { it.mkdir() },
      aidlDir = null,
      commonSrcDir = commonMainDir,
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
      isCompose = false
    )

    runWriteCommandAction(projectRule.project) {
      DefaultRecipeExecutor(renderingContext).generateMultiplatformModule(
        data = newModuleTemplateData,
        useKts = useKts,
      )
    }

    return rootDir
  }

  companion object {
    private const val MODULE_NAME_APP = "app"

    val EXPECTED_BUILD_GRADLE_FILE = """
plugins {
}

        kotlin {
      androidLibrary {
  namespace = "com.kmplib.packagename"
  compileSdk = ${SdkVersionInfo.HIGHEST_KNOWN_STABLE_API}
  minSdk = 34

  withAndroidTestOnJvmBuilder {
      compilationName = "unitTest"
      defaultSourceSetName = "androidUnitTest"
  }

  withAndroidTestOnDeviceBuilder {
      compilationName = "instrumentedTest"
      defaultSourceSetName = "androidInstrumentedTest"
      sourceSetTreeName = "test"
  }
}
      sourceSets {
  getByName("androidMain") {
    dependencies {
      // put your android target dependencies here
    }
  }
  getByName("androidInstrumentedTest") {
    dependencies {
    }   
  }
  commonMain {
    dependencies {
      // put your common multiplatform dependencies here
    }
  }
  commonTest {
    dependencies {
    }
  }
}
    }
""".trimIndent()

    val EXPECTED_ANDROID_MAIN_CONTENT = """
package com.kmplib.packagename

  class AndroidPlatform : Platform {
    override val name: String = "Android ${'$'}{android.os.Build.VERSION.SDK_INT}"
  }

  actual fun getPlatform(): Platform = AndroidPlatform()
    """.trimIndent()

    val EXPECTED_COMMON_MAIN_CONTENT = """
package com.kmplib.packagename

  interface Platform {
    val name: String
  }

  expect fun getPlatform(): Platform
    """.trimIndent()

    val EXPECTED_ANDROID_UNIT_TEST_CONTENT = """
package com.kmplib.packagename

  import org.junit.Test

  import org.junit.Assert.*

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
    """.trimIndent()

    val EXPECTED_ANDROID_INSTRUMENTED_TEST_CONTENT = """
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
      assertEquals("com.kmplib.packagename", appContext.packageName)
    }
  }
    """.trimIndent()

    val EXPECTED_MODULE_FILES = arrayOf(
      "AndroidManifest.xml", "commonMain/Platform.kt", "androidUnitTest/ExampleUnitTest.kt",
      "androidInstrumentedTest/ExampleInstrumentedTest.kt", "build.gradle.kts", ".gitignore",
      "androidMain/AndroidPlatform.kt"
    )
  }
}