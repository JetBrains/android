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
import com.android.flags.junit.RestoreFlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture.Tab.EDITOR
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class NewKtsModuleTest {

  @get:Rule
  val guiTest = GuiTestRule()

  @get:Rule
  val myRestoreFlagRule = RestoreFlagRule(StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION)

  @Before
  fun setup() {
    StudioFlags.NPW_SHOW_GRADLE_KTS_OPTION.override(true)
  }

  /**
   * Verifies that adding new Mobile/Tablet Java Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Phone/Tablet" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaMobileModuleWithKts() {
    guiTest.importSimpleApplication()
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextPhoneAndTabletModule()
      .enterModuleName("mobile")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickNext()
      .chooseActivity("No Activity")
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("mobile/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.application")""")
      contains("applicationId =")
      contains("minSdkVersion(")
      contains("isMinifyEnabled =")
      doesNotContainMatch("""buildToolsVersion ['"]""")
    }
  }

  /**
   * Verifies that adding new Kotlin "Android Library" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Android Library" module, with Kotlin Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewKotlinAndroidLibraryWithKtsModule() {
    guiTest.importSimpleApplication()
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToAndroidLibrary()
      .enterModuleName("android_lib")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Kotlin)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("android_lib/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.library")""")
      contains("consumerProguardFiles(")
      contains("isMinifyEnabled =")
    }
  }

  /**
   * Verifies that adding new Java "Automotive" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Automotive" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaAutomotiveWithKtsModule() {
    guiTest.importSimpleApplication()
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextAutomotiveModule()
      .enterModuleName("automotive")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickNext()
      .chooseActivity("Media service")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("automotive/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""implementation("com.android.support:support-media-compat:""")
    }
  }

  /**
   * Verifies that adding new Java "Wear OS" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Wear OS" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  // @Test b/159103915
  fun addNewJavaWearWithKtsModule() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextWearModule()
      .enterModuleName("wear")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickNext()
      .chooseActivity("Blank Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    // HACK for b/159103915 - Not enough
    guiTest.ideFrame().editor.open("wear/src/main/res/layout/activity_main.xml", EDITOR).select("(dark_grey)").typeText("purple_200")

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("wear/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""implementation("androidx.wear:wear:""")
    }
  }

  /**
   * Verifies that adding new Java "Android Tv" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Android Tv" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaAndroidTvWithKtsModule() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextAndroidTvModule()
      .enterModuleName("tv")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickNext()
      .chooseActivity("Blank Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("tv/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""androidx.leanback:leanback:""")
    }
  }

  /**
   * Verifies that adding new Java "Android Things" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Android Things" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaAndroidThingsWithKtsModule() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextAndroidThingsModule()
      .enterModuleName("things")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickNext()
      .chooseActivity("Empty Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("things/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.application")""")
    }
  }
}
