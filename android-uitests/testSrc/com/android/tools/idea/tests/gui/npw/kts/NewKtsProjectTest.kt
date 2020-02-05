/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.npw.kts

import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.SdkConstants.FN_SETTINGS_GRADLE_KTS
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.platform.Language

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class NewKtsProjectTest {

  @get:Rule
  val guiTest = GuiTestRule()

  @Before
  fun setup() {
    StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.override(true)
  }

  @After
  fun tearDown() {
    StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.clearOverride()
  }

  /**
   * Verifies that a new Java Project, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Create New Project, with language Java and "Use Kotlin script" checkbox checked
   * Verification
   * - "settings.gradle.kts" should have an entry -> include("app")
   * - Project level build.gradle.kts should have "tasks.register("clean", Delete::class)"
   * instead of "task clean(type: Delete)"
   * - "app/build.gradle.kts" should have versionCode(1)/versionName("1.0")/minifyEnabled=false instead of
   * "versionCode 1"/versionName "1.0"/"minifyEnabled false"
   */
  @Test
  fun createNewJavaKtsProject() {
    createNewKtsProject(Language.JAVA)

    assertThat(guiTest.getProjectFileText(FN_BUILD_GRADLE_KTS)).contains("""tasks.register("clean", Delete::class)""")
    assertThat(guiTest.getProjectFileText("app/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.application")""")
      contains("applicationId =")
      contains("minSdkVersion(")
      contains("isMinifyEnabled =")
    }
  }

  /**
   * Verifies that a new Kotlin Project, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Create New Project, with language Kotlin and "Use Kotlin script" checkbox checked
   * Verification
   * - "settings.gradle.kts" should have an entry -> include("app")
   * - "app/build.gradle.kts" should have val kotlin_version by extra(xxx)
   */
  @Test
  fun createNewKotlinKtsProject() {
    createNewKtsProject(Language.KOTLIN)

    guiTest.getProjectFileText(FN_BUILD_GRADLE_KTS).apply {
      assertThat(this).contains("val kotlin_version by extra(")
      assertThat(this).contains("""tasks.register("clean", Delete::class)""")
    }
    assertThat(guiTest.getProjectFileText("app/$FN_BUILD_GRADLE_KTS")).apply {
      contains("""id("kotlin-android")""")
      contains("""org.jetbrains.kotlin:kotlin-stdlib:${'$'}{rootProject.extra["kotlin_version"]}"""")
    }
  }

  private fun createNewKtsProject(language: Language) {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .chooseActivity("Empty Activity")
      .wizard()
      .clickNext()
      .configureNewAndroidProjectStep
      .setSourceLanguage(language)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickFinish()

    guiTest.ideFrame()
      .waitForGradleProjectSyncToFinish()

    assertThat(guiTest.getProjectFileText(FN_SETTINGS_GRADLE_KTS)).contains("""include(":app")""")
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }
}
