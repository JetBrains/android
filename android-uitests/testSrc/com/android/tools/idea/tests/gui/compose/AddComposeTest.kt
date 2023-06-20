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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureBasicActivityStepFixture.ActivityTextField.NAME
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.tests.util.WizardUtils.createNewProject
import com.android.tools.idea.wizard.template.Language.Kotlin
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class AddComposeTest {
  @get:Rule
  val guiTest = GuiTestRule()

  /**
   * Verifies that user is able to create a new Compose Activity Project through the
   * new project wizard.
   *
   * Test steps:
   * 1. Create new default "Empty Activity" Project
   * 2. Add new "Empty Compose Activity" Module, with name: "compose"
   * Verify:
   * 1. Check that app/build.gradle does NOT have dependencies for "androidx.compose.ui:ui-framework" and "androidx.compose.ui:ui-tooling"
   * 2. Check that compose/build.gradle has dependencies for "androidx.compose.ui:ui-framework" and "androidx.compose.ui:ui-tooling"
   * 3. Check that the "compose" main activity has functions annotated with @Composable and @Preview
   */
  @Test
  fun newComposeModule() {
    createNewProject(guiTest, "Empty Activity")
    guiTest.getProjectFileText("app/build.gradle").run {
      assertThat(this).doesNotContain("androidx.compose.ui:ui:")
      assertThat(this).doesNotContain("androidx.compose.ui:ui-tooling:")
    }

    NewModuleWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "New Module\u2026"))
      .clickNextPhoneAndTabletModule()
      .setSourceLanguage(Kotlin)
      .selectMinimumSdkApi(28)
      .enterModuleName("compose")
      .wizard()
      .clickNext()
      .chooseActivity("Empty Compose Activity")
      .clickNext()
      .clickFinishAndWaitForSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("compose")

    guiTest.getProjectFileText("compose/build.gradle").run {
      assertThat(this).contains("implementation \"androidx.compose.ui:ui:")
      assertThat(this).contains("implementation \"androidx.compose.material:material:")
      assertThat(this).contains("implementation \"androidx.compose.ui:ui-tooling:")
    }
    guiTest.getProjectFileText("compose/src/main/java/com/google/compose/MainActivity.kt").run {
      assertThat(this).contains("@Composable")
      assertThat(this).contains("@Preview")
    }
  }

  /**
   * Verifies that user is able to create a new Compose Activity Project through the
   * new project wizard.
   *
   * Test`   steps:
   * 1. Create new default "Empty Activity" Project
   * 2. Add new "Empty Compose Activity" Activity to "app" module, with name "ComposeActivity"
   * Verify:
   * 1. Check that app/build.gradle has dependencies for "androidx.compose.ui:ui-framework" and "androidx.compose.ui:ui-tooling"
   * 2. Check that ComposeActivity has functions annotated with @Composable and @Preview
   */
  @Test
  fun newComposeActivity() {
    createNewProject(guiTest, "Empty Activity")
    guiTest.getProjectFileText("app/build.gradle").run {
      assertThat(this).doesNotContain("androidx.compose.ui:ui-framework:")
      assertThat(this).doesNotContain("androidx.compose.ui:ui-tooling:")
    }

    fun addComposeActivity(name: String) {
      NewActivityWizardFixture.find(guiTest.ideFrame().invokeMenuPath("File", "New", "Compose", "Empty Compose Activity"))
        .getConfigureActivityStep("Empty Compose Activity")
        .enterTextFieldValue(NAME, name)
        .wizard()
        .clickFinishAndWaitForSyncToFinish()
        .projectView
        .selectAndroidPane()
        .clickPath("app")
    }

    val activityTitle = "ComposeActivity"
    addComposeActivity(activityTitle)

    guiTest.getProjectFileText("app/build.gradle").run {
      assertThat(this).contains("implementation \"androidx.compose.ui:ui:")
      assertThat(this).contains("implementation \"androidx.compose.material:material:")
      assertThat(this).contains("implementation \"androidx.compose.ui:ui-tooling:")
    }
    guiTest.getProjectFileText("app/src/main/java/com/google/myapplication/$activityTitle.kt").run {
      assertThat(this).contains("Greeting")
      assertThat(this).contains("DefaultPreview")
      assertThat(this).contains("@Composable")
      assertThat(this).contains("@Preview")
    }

    val activityTitle2 = "ComposeActivity2"
    addComposeActivity(activityTitle2)

    guiTest.getProjectFileText("app/src/main/java/com/google/myapplication/$activityTitle2.kt").run {
      assertThat(this).doesNotContain("Greeting(")
      assertThat(this).doesNotContain("DefaultPreview(")
      assertThat(this).contains("Greeting2")
      assertThat(this).contains("DefaultPreview2")
      assertThat(this).contains("@Composable")
      assertThat(this).contains("@Preview")
    }
  }
}
