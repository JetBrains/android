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
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureNewAndroidProjectStepFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewProjectWizardFixture
import com.android.tools.idea.wizard.template.BuildConfigurationLanguage
import com.android.tools.idea.wizard.template.Language
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

  /**
   * Verifies that a new Java Project, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Create New Project, with language Java and "Kotlin script (Recommended)" item selected in the Build configuration language
   * combo box
   * - "settings.gradle.kts" should have an entry -> include("app")
   * - "app/build.gradle.kts" should have versionCode(1)/versionName("1.0")/minifyEnabled=false instead of
   * "versionCode 1"/versionName "1.0"/"minifyEnabled false"
   */
  @Test
  fun createNewJavaKtsProject() {
    createNewMobileKtsProject(Language.Java)

    assertThat(guiTest.getProjectFileText("app/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.application")""")
      contains("applicationId =")
      contains("minSdkVersion(")
      contains("isMinifyEnabled =")
      doesNotContainMatch("""buildToolsVersion ['"]""")
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
    createNewMobileKtsProject(Language.Kotlin)

    guiTest.getProjectFileText(FN_BUILD_GRADLE_KTS).apply {
      assertThat(this).contains("val kotlin_version by extra(")
    }
    assertThat(guiTest.getProjectFileText("app/$FN_BUILD_GRADLE_KTS")).apply {
      contains("""id("kotlin-android")""")
      contains("""org.jetbrains.kotlin:kotlin-stdlib:${'$'}{rootProject.extra["kotlin_version"]}"""")
    }
  }

  @Test
  fun createNewWearWithCompanionKotlinKtsProject() {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .selectTab(FormFactor.WEAR)
      .wizard()
      .clickNext() // Select default Wear Activity
      .configureNewAndroidProjectStep
      .setPairWithPhoneApp(true)
      .configureProjectWithDefaultsAndFinish(Language.Kotlin)

    assertThat(guiTest.getProjectFileText(FN_SETTINGS_GRADLE_KTS)).apply {
      contains("""include(":wear")""")
      contains("""include(":mobile")""")
    }
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  @Test
  fun createNewTVKotlinKtsProject() {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .selectTab(FormFactor.TV)
      .wizard()
      .clickNext() // Select default TV Activity
      .configureNewAndroidProjectStep
      .configureProjectWithDefaultsAndFinish(Language.Kotlin)

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  @Test
  fun createNewAutomotiveKotlinKtsProject() {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .selectTab(FormFactor.AUTOMOTIVE)
      .wizard()
      .clickNext() // Select default Automotive Activity
      .configureNewAndroidProjectStep
      .configureProjectWithDefaultsAndFinish(Language.Kotlin)

    assertThat(guiTest.getProjectFileText(FN_SETTINGS_GRADLE_KTS)).apply {
      contains("""include(":automotive")""")
      contains("""include(":mobile")""")
    }
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  private fun createNewMobileKtsProject(language: Language) {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .chooseActivity("Empty Activity")
      .wizard()
      .clickNext()
      .configureNewAndroidProjectStep
      .configureProjectWithDefaultsAndFinish(language)

    assertThat(guiTest.getProjectFileText(FN_SETTINGS_GRADLE_KTS)).contains("""include(":app")""")
    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  private fun ConfigureNewAndroidProjectStepFixture<NewProjectWizardFixture>.configureProjectWithDefaultsAndFinish(language: Language) :
    ConfigureNewAndroidProjectStepFixture<NewProjectWizardFixture> {

    setSourceLanguage(language)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    return this
  }
}
