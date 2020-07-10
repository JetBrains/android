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
package com.android.tools.idea.tests.gui.npw

import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.wizard.template.Language
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that newly created Android TV projects do not have errors in them
 */
@RunWith(GuiTestRemoteRunner::class)
class NewTVProjectTest {
  @get:Rule
  val guiTest = GuiTestRule()

  @Test
  fun testBuildBlankTVActivityWithoutCompanion() {
    createTVProject("Blank Activity", Language.Java, false)

    guiTest.getProjectFileText("app/build.gradle").apply {
      assertThat(this).contains("implementation 'androidx.leanback:leanback:")
      assertThat(this).contains("implementation 'com.github.bumptech.glide:glide:")

      assertThat(this).doesNotContain("constraintlayout")
    }

    guiTest.getProjectFileText("app/src/main/AndroidManifest.xml").apply {
      assertThat(this).contains("android:name=\"android.hardware.touchscreen\"")
      assertThat(this).contains("android:name=\"android.software.leanback\"")
    }

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  @Test
  fun testBuildBlankTVActivityWithCompanion() {
    createTVProject("Blank Activity", Language.Kotlin, true)

    guiTest.getProjectFileText("tv/build.gradle").apply {
      assertThat(this).contains("implementation 'androidx.leanback:leanback:")
      assertThat(this).contains("implementation 'com.github.bumptech.glide:glide:")
      assertThat(this).doesNotContain("+") // All dependencies should be resolved
    }

    guiTest.getProjectFileText("tv/src/main/AndroidManifest.xml").apply {
      assertThat(this).contains("android:name=\"android.hardware.touchscreen\"")
      assertThat(this).contains("android:name=\"android.software.leanback\"")
    }

    assertThat(guiTest.ideFrame().invokeProjectMake().isBuildSuccessful).isTrue()
  }

  private fun createTVProject(activityName: String, language: Language, pairWithPhone: Boolean) {
    guiTest.welcomeFrame()
      .createNewProject()
      .chooseAndroidProjectStep
      .selectTab(FormFactor.TV)
      .chooseActivity(activityName)
      .wizard()
      .clickNext()
      .configureNewAndroidProjectStep
      .setSourceLanguage(language)
      .selectMinimumSdkApi(AndroidVersion.VersionCodes.P)
      .setPairWithCompanionPhoneApp(pairWithPhone)
      .wizard()
      .clickFinishAndWaitForSyncToFinish()
  }
}
