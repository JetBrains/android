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
import com.android.flags.junit.FlagRule
import com.android.sdklib.AndroidVersion
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.benchmark.BenchmarkModuleType
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.wizard.template.BuildConfigurationLanguage
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class NewKtsModuleTest {

  @get:Rule
  val guiTest = GuiTestRule()

  @get:Rule
  val macroBenchmarkFlagRule = FlagRule(StudioFlags.NPW_NEW_MACRO_BENCHMARK_MODULE, true)

  /**
   * Verifies that adding new Mobile/Tablet Java Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic java project)
   * 2. Add a new Java "Phone/Tablet" Module and selected "Kotlin script (Recommended)".
   * 3. Add a new Kotlin "Android Library" Module and select "Kotlin script (Recommended)".
   * 4. Add a new Java "Automotive" Module and selected "Kotlin script (Recommended)".
   * 5. Add a new Java "Wear OS" Module and selected "Kotlin script (Recommended)".
   * 6. Add a new Java "Android Tv" Module and selected "Kotlin script (Recommended)".
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewMobileModulesWithKts() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")

    addNewJavaMobileModuleWithKts()
    addNewKotlinAndroidLibraryWithKtsModule()
    addNewJavaAutomotiveWithKtsModule()
    addNewJavaWearWithKtsModule()
    addNewJavaAndroidTvWithKtsModule()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  private fun addNewJavaMobileModuleWithKts() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextPhoneAndTabletModule()
      .enterModuleName("mobile")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      .chooseActivity("No Activity")
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("mobile/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.application")""")
      contains("applicationId =")
      contains("minSdkVersion(")
      contains("isMinifyEnabled =")
      doesNotContainMatch("""buildToolsVersion ['"]""")
    }
  }

  private fun addNewKotlinAndroidLibraryWithKtsModule() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToAndroidLibrary()
      .enterModuleName("android_lib")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Kotlin)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("android_lib/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("com.android.library")""")
      contains("consumerProguardFiles(")
      contains("isMinifyEnabled =")
    }
  }

  private fun addNewJavaAutomotiveWithKtsModule() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextAutomotiveModule()
      .enterModuleName("automotive")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      .chooseActivity("Media Service")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("automotive/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""implementation("androidx.media:media:""")
    }
  }

  private fun addNewJavaWearWithKtsModule() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextWearModule()
      .enterModuleName("wear")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      .chooseActivity("Blank Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("wear/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""implementation("androidx.wear:wear:""")
    }
  }

  private fun addNewJavaAndroidTvWithKtsModule() {
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextAndroidTvModule()
      .enterModuleName("tv")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      .chooseActivity("Blank Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("tv/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""androidx.leanback:leanback:""")
    }
  }

  /**
   * Verifies that adding new Kotlin "Java or Kotlin Library" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Java or Kotlin Library" module, with Kotlin Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewPureJavaLibraryWithKtsModule() {
    guiTest.importSimpleApplication()
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToPureLibrary()
      .enterLibraryName("pure_lib")
      .setSourceLanguage(Language.Java)
      .setUseKtsBuildFiles(true)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("pure_lib/$FN_BUILD_GRADLE_KTS")).apply {
      contains("plugins {")
      contains("""id("java-library")""")
      contains("java {")
      contains("sourceCompatibility = JavaVersion")
      contains("targetCompatibility = JavaVersion")
    }
  }

  /**
   * Verifies that adding new Java "Dynamic Feature" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Dynamic Feature" module, with Java Language and "Kotlin script (Recommended)" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaDynamicFeatureWithKtsModule() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("DynamicFeature")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .clickNextToConfigureConditionalDelivery()
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("DynamicFeature/$FN_BUILD_GRADLE_KTS")).apply {
      contains("""id("com.android.dynamic-feature")""")
    }
  }

  /**
   * Verifies that adding new Java "Instant Dynamic Feature" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Instant Dynamic Feature" module, with Java Language and "Kotlin script (Recommended)" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaInstantDynamicFeatureWithKtsModule() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToInstantDynamicFeature()
      .enterFeatureModuleName("InstantFeature")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("InstantFeature/$FN_BUILD_GRADLE_KTS")).apply {
      contains("""id("com.android.dynamic-feature")""")
    }
  }

  /**
   * Verifies that adding new Java "Benchmark" Module, with kts, has the expected content and builds.
   *
   * Test Steps
   * 1. Import "Simple Application" (a basic non kts java project)
   * 2. Add a new "Benchmark" module, with Java Language and "Use kts script" selected.
   * - Make sure the projects can build ("Build" > "Make Project")
   * - The new Module build.gradle.kts should be in "Kotlin Script", ie "applicationId = xxx", instead of "applicationId xxx"
   */
  @Test
  fun addNewJavaBenchmarkModuleWithKts() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")
    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextToBenchmarkModule()
      .selectBenchmarkType(BenchmarkModuleType.MICROBENCHMARK)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Java)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    assertThat(guiTest.getProjectFileText("benchmark/$FN_BUILD_GRADLE_KTS")).apply {
      contains("""id("com.android.library")""")
      contains("""id("androidx.benchmark")""")
    }
  }
}
