/*
 * Copyright (C) 2021 The Android Open Source Project
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
package org.jetbrains.kotlin.android.configure

import com.android.tools.idea.gradle.dsl.TestFileName
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase
import com.intellij.openapi.command.WriteCommandAction
import org.jetbrains.annotations.SystemDependent
import org.jetbrains.kotlin.config.LanguageFeature.InlineClasses
import org.jetbrains.kotlin.config.LanguageFeature.MultiPlatformProjects
import org.jetbrains.kotlin.config.LanguageFeature.State.DISABLED
import org.jetbrains.kotlin.config.LanguageFeature.State.ENABLED
import org.junit.Test
import java.io.File

class ChangeProjectLanguageFeatureTest : GradleFileModelTestCase("tools/adt/idea/android-kotlin/idea-android/testData") {
  @Test
  fun testEnableInlineClasses() {
    writeToBuildFile(TestFile.ROOT)
    writeToSubModuleBuildFile(TestFile.APP)
    writeToSettingsFile(subModuleSettingsText)

    val configurator = KotlinAndroidGradleModuleConfigurator()
    WriteCommandAction.runWriteCommandAction(project) {
      configurator.changeGeneralFeatureConfiguration(mySubModule, InlineClasses, ENABLED, false)
    }
    verifyFileContents(myBuildFile, TestFile.ROOT)
    verifyFileContents(mySubModuleBuildFile, TestFile.APP_ENABLE_INLINE_CLASSES_EXPECTED)
  }

  @Test
  fun testDisableInlineClasses() {
    writeToBuildFile(TestFile.ROOT)
    writeToSubModuleBuildFile(TestFile.APP)
    writeToSettingsFile(subModuleSettingsText)

    val configurator = KotlinAndroidGradleModuleConfigurator()
    WriteCommandAction.runWriteCommandAction(project) {
      configurator.changeGeneralFeatureConfiguration(mySubModule, InlineClasses, DISABLED, false)
    }
    verifyFileContents(myBuildFile, TestFile.ROOT)
    verifyFileContents(mySubModuleBuildFile, TestFile.APP_DISABLE_INLINE_CLASSES_EXPECTED)
  }

  @Test
  fun testEnableMultiPlatformProjects() {
    writeToBuildFile(TestFile.ROOT)
    writeToSubModuleBuildFile(TestFile.APP)
    writeToSettingsFile(subModuleSettingsText)

    val configurator = KotlinAndroidGradleModuleConfigurator()
    WriteCommandAction.runWriteCommandAction(project) {
      configurator.changeGeneralFeatureConfiguration(mySubModule, MultiPlatformProjects, ENABLED, false)
    }
    verifyFileContents(myBuildFile, TestFile.ROOT)
    verifyFileContents(mySubModuleBuildFile, TestFile.APP_ENABLE_MULTI_PLATFORM_PROJECTS_EXPECTED)
  }

  @Test
  fun testDisableMultiPlatformProjects() {
    writeToBuildFile(TestFile.ROOT)
    writeToSubModuleBuildFile(TestFile.APP)
    writeToSettingsFile(subModuleSettingsText)

    val configurator = KotlinAndroidGradleModuleConfigurator()
    WriteCommandAction.runWriteCommandAction(project) {
      configurator.changeGeneralFeatureConfiguration(mySubModule, MultiPlatformProjects, DISABLED, false)
    }
    verifyFileContents(myBuildFile, TestFile.ROOT)
    verifyFileContents(mySubModuleBuildFile, TestFile.APP_DISABLE_MULTI_PLATFORM_PROJECTS_EXPECTED)
  }

  enum class TestFile(val path: @SystemDependent String): TestFileName {
    ROOT("root"),
    APP("app"),
    APP_DISABLE_INLINE_CLASSES_EXPECTED("appDisableInlineClassesExpected"),
    APP_ENABLE_INLINE_CLASSES_EXPECTED("appEnableInlineClassesExpected"),
    APP_DISABLE_MULTI_PLATFORM_PROJECTS_EXPECTED("appDisableMultiPlatformProjectsExpected"),
    APP_ENABLE_MULTI_PLATFORM_PROJECTS_EXPECTED("appEnableMultiPlatformProjectsExpected"),
    ;

    override fun toFile(basePath: @SystemDependent String, extension: String): File {
      return super.toFile("$basePath/change/$path", extension)
    }

  }
}