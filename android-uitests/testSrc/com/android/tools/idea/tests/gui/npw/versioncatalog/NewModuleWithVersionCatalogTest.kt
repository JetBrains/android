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
package com.android.tools.idea.tests.gui.npw.versioncatalog

import com.android.SdkConstants.FN_BUILD_GRADLE_KTS
import com.android.SdkConstants.FN_VERSION_CATALOG
import com.android.sdklib.AndroidVersion
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
class NewModuleWithVersionCatalogTest {

  @get:Rule
  val guiTest = GuiTestRule()

  /**
   * Verifies that a new module, with version catalog, has the expected content in the toml file and the build file.
   */
  @Test
  fun addNewModule_withConventionalDefaultTomlFile() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplicationWithConventionalDefaultToml")

    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextPhoneAndTabletModule()
      .enterModuleName("mobile")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Kotlin)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      // Intentionally choose non-Compose Activity to avoid the version compatibility issue between Compose compiler and Kotlin
      .chooseActivity("Empty Views Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.getProjectFileText("gradle/$FN_VERSION_CATALOG")).apply {
      contains("[libraries]")
      contains("[versions]")
      contains("""group = "androidx.core"""")
    }
    assertThat(guiTest.getProjectFileText("mobile/$FN_BUILD_GRADLE_KTS")).apply {
      contains("dependencies {")
      contains("""implementation(libs.androidx.core.core.ktx)""")
    }

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  @Test
  fun addNewModules_withVersionCatalog_inBuildScript() {
    guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplicationVersionCatalogInBuildScript")

    guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(guiTest.ideFrame())
      .clickNextPhoneAndTabletModule()
      .enterModuleName("mobile")
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setSourceLanguage(Language.Kotlin)
      .setBuildConfigurationLanguage(BuildConfigurationLanguage.KTS)
      .wizard()
      .clickNext()
      // Intentionally choose non-Compose Activity to avoid the version compatibility issue between Compose compiler and Kotlin
      .chooseActivity("Empty Views Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()

    // Dependencies are not managed by using Version Catalogs
    assertThat(guiTest.getProjectFileText("mobile/$FN_BUILD_GRADLE_KTS")).apply {
      contains("dependencies {")
      contains("""implementation("androidx.core:core-ktx""")
    }
  }
}
