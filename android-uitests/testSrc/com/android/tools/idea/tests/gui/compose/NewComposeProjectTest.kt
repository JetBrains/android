/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.compose

import com.android.tools.adtui.device.FormFactor
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.android.tools.idea.tests.util.WizardUtils
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class NewComposeProjectTest {
  @get:Rule
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.MINUTES)
  var selectMobileTab = FormFactor.MOBILE


  /**
   * Verifies that user is able to create a new Compose Activity Project through the
   * new project wizard.
   * <p>TT ID: f1c58981-0704-40be-9794-7f61e425a8d5
   * Test steps:
   * 1. Create new default "Empty Compose Activity" Project
   * Verify:
   * 1. Check that app/build.gradle.kts has dependencies for "androidx.compose.ui:ui-framework" and "androidx.compose.ui:ui-tooling"
   * 2. Check that the main activity has functions annotated with @Composable and @Preview
   * 3. Check Gradle Sync to success
   */
  @Test
  fun newComposeProject() {
    //WizardUtils.createNewProject(guiTest, "Empty Views Activity", null)
    WizardUtils.createNewProject(guiTest, selectMobileTab, "Empty Activity")
    guiTest.getProjectFileText("app/build.gradle.kts").run {
      assertThat(this).contains("implementation(libs.ui")
      assertThat(this).contains("implementation(libs.material3")
      assertThat(this).contains("implementation(libs.ui.tooling.preview")
      assertThat(this).contains("debugImplementation(libs.ui.tooling")
    }
    guiTest.getProjectFileText("app/src/main/java/com/example/myapplication/MainActivity.kt").run {
      assertThat(this).contains("@Composable")
      assertThat(this).contains("@Preview")
      assertThat(this).contains("fun GreetingPreview(")
      assertThat(this).contains("fun Greeting(")
    }

    guiTest.ideFrame().focus().projectView
      .selectAndroidPane()
      .clickPath("app")

    // Check if we can add another Compose Activity (will need to de-duplicate compose function names)
    NewActivityWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "Compose", "Empty Activity"))
      .getConfigureActivityStep("Empty Activity")
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    guiTest.getProjectFileText("app/src/main/java/com/example/myapplication/MainActivity2.kt").run {
      assertThat(this).contains("fun GreetingPreview2(")
      assertThat(this).contains("fun Greeting2(")
    }
  }
}