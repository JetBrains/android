/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.dynamicfeature

import com.android.tools.idea.npw.dynamicapp.DeviceFeatureKind
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureDynamicFeatureDeliveryStepFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureDynamicFeatureStepFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.android.tools.idea.wizard.template.Language
import com.android.tools.idea.wizard.template.Language.Java
import com.android.tools.idea.wizard.template.Language.Kotlin
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.matcher.JLabelMatcher
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class AddDynamicFeatureTest {
  @get:Rule
  val guiTest = GuiTestRule()

  /**
   * Verifies that user is able to add a Instant Dynamic Feature Module through the
   * new module wizard. The app module (base) does not contain the "dist:module" tag.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new Dynamic Feature Module (Instant App), accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Dynamic Feature Module is shown in the project explorer pane.
   * 2. Open the Dynamic Feature Module manifest and check that "dist:onDemand" and
   * "dist:fusing include" are set to false.
   * 3. Module Title contains @string/title_dynamicfeature
   * 4. "dist:instant" is set to true
   * 5. Open the app Module strings.xml (not the *dynamic* Module strings.xml) and check that a
   * new string was added for the dynamic feature title
   * 6. Check the app (base module) Manifest contains the attribute "dist:instant" set to true
   * </pre>
   */

  /**
   * Same as the test above, except the fusing check box is checked. Verify the "dist:fusing" attribute is set to true
   */
  @Test
  fun addInstantDynamicModuleWithFusing_baseHasNoModule() {
    val ideFrame = guiTest.importSimpleApplication()

    createInstantDynamicModuleWithFusing(ideFrame)

    guiTest.getProjectFileText("dynamicfeature/src/main/AndroidManifest.xml").run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    guiTest.getProjectFileText("app/src/main/res/values/strings.xml").run {
      assertThat(this).contains("""<string name="title_dynamicfeature">Module Title</string>""")
    }

    guiTest.getProjectFileText("app/src/main/AndroidManifest.xml").run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
    }
  }

  /**
   * Same as above, except the "dist:module" tag and "dist:instant="true"" attribute is added to the app module manifest
   */
  @Test
  fun addInstantDynamicModuleWithFusing_baseHasModule() {
    val ideFrame = guiTest.importSimpleApplication()

    writeDistModuleToBaseManifest(false)
    createInstantDynamicModuleWithFusing(ideFrame)

    guiTest.getProjectFileText("dynamicfeature/src/main/AndroidManifest.xml").run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    guiTest.getProjectFileText("app/src/main/AndroidManifest.xml").run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).doesNotContain("""dist:instant="false""")
    }
  }

  /**
   * Same as above, but sets the delivery mode to "include at install time with conditions" without specifying device feature conditions.
   */
  @Test
  fun addDynamicModuleWithConditionalDelivery_installOnDemandWithMinSdk() {
    val ideFrame = guiTest.importSimpleApplication()

    createDynamicModule(ideFrame, Java, DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS)

    guiTest.getProjectFileText("MyDynamicFeature/src/main/AndroidManifest.xml").run {
      assertThat(this).contains("""<dist:delivery>""")
      assertThat(this).contains("""<dist:install-time>""")
      assertThat(this).contains("""<dist:conditions>""")
      assertThat(this).contains("""</dist:conditions>""")
      assertThat(this).contains("""</dist:install-time>""")
      assertThat(this).doesNotContain("""<dist:on-demand />""")
      assertThat(this).contains("""</dist:delivery>""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
    }

    guiTest.getProjectFileText("app/src/main/res/values/strings.xml").run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Same as above, but sets the delivery mode to "include at install time with conditions" and
   * specifies a few clauses for conditional delivery.
   */
  @Test
  fun addDynamicModuleWithConditionalDelivery_installOnDemandDeviceFeatures() {
    val ideFrame = guiTest.importSimpleApplication()

    createDynamicModule(ideFrame, Java, DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS) {
      addConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test")
      addConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test2")
      addConditionalDeliveryFeature(DeviceFeatureKind.GL_ES_VERSION, "0x2000000")
      removeConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test2")
    }

    guiTest.getProjectFileText("MyDynamicFeature/src/main/AndroidManifest.xml").run {

      val expected = """|<?xml version="1.0" encoding="utf-8"?>
                        |<manifest xmlns:android="http://schemas.android.com/apk/res/android"
                        |    xmlns:dist="http://schemas.android.com/apk/distribution"
                        |    package="com.example.mydynamicfeature">
                        |
                        |    <dist:module
                        |        dist:instant="false"
                        |        dist:title="@string/title_mydynamicfeature">
                        |        <dist:delivery>
                        |            <dist:install-time>
                        |                <dist:conditions>
                        |                    <!-- To include or exclude this module by user countries, uncomment and update this section. -->
                        |                    <!-- Learn more @ [https://d.android.com/r/studio-ui/dynamic-delivery/conditional-delivery] -->
                        |                    <!--   <dist:user-countries dist:exclude="false"> -->
                        |                    <!--     <dist:country dist:code="US" /> -->
                        |                    <!--   </dist:user-countries> -->
                        |                    <dist:device-feature dist:name="test" />
                        |                    <dist:device-feature
                        |                        dist:name="android.hardware.opengles.version"
                        |                        dist:version="0x2000000" />
                        |                </dist:conditions>
                        |            </dist:install-time>
                        |        </dist:delivery>
                        |        <dist:fusing dist:include="false" />
                        |    </dist:module>
                        |</manifest>""".trimMargin()
      assertEquals(expected, this.trim())
    }

    guiTest.getProjectFileText("app/src/main/res/values/strings.xml").run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a New Login Activity to a Dynamic Feature Module
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new Dynamic Feature Module, accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * 5. Go to File -> New -> Activity -> Login Activity -> Finish
   * Verify:
   * 1. The new Dynamic Feature Module is shown in the project explorer pane.
   * 2. Open the app Module strings.xml (not the *dynamic* Module strings.xml) and check that a
   * new string was added for "title_activity_login"
   * 3. Open the "dynamicfeature" module strings.xml and check there are new strings
   * like: "prompt_email", "prompt_password", "error_invalid_email", etc
   * </pre>
   */
  @Test
  fun addLoginActivityToDynamicModule() {
    val ideFrame = guiTest.importSimpleApplication()

    createDynamicModule(ideFrame, Java)
      .addActivity("MyDynamicFeature", "Activity", "Login Activity")

    guiTest.getProjectFileText("app/src/main/res/values/strings.xml").run {
      assertThat(this).contains("title_activity_login")
    }

    guiTest.getProjectFileText("MyDynamicFeature/src/main/res/values/strings.xml").run {
      assertThat(this).contains("prompt_email")
      assertThat(this).contains("prompt_password")
      assertThat(this).contains("invalid_password")
    }
  }

  /**
   * Verifies that user is able to add a Map Activity to a Dynamic Feature Module and that the library
   * dependencies are added to the Base, using "api" references.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new Dynamic Feature Module, accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * 5. Go to File -> New -> Google -> Google Maps Activity -> Finish
   * Verify:
   * 1. The new Dynamic Feature Module is shown in the project explorer pane.
   * 2. Open the "dynamicfeature" module build.gradle check that play-services-maps was not added.
   * 3. Open the "app" module build.gradle and check that play-services-maps was added with "api" dependency.
   * 4. "com.android.support" base dependencies, should be re-written from "implementation" to "api"
   * </pre>
   */
  @Test
  fun addMapsActivityToDynamicModule() {
    val ideFrame = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleAndroidxApplication")

    guiTest.getProjectFileText("app/build.gradle").run {
      assertThat(this).contains("implementation 'androidx.appcompat:appcompat:")
      assertThat(this).contains("implementation 'androidx.constraintlayout:constraintlayout:")
    }

    createDynamicModule(ideFrame, Java)

    // Adding two "Google Maps Activity", should only add dependencies to "base" module and no duplicates
    repeat(2) {
      ideFrame
        .addActivity("MyDynamicFeature", "Google", "Google Maps Activity")

      guiTest.getProjectFileText("MyDynamicFeature/build.gradle").run {
        assertThat(this).doesNotContain("play-services-maps")
        assertThat(this).contains("implementation 'androidx.appcompat:appcompat:")
        assertThat(this).contains("implementation 'androidx.constraintlayout:constraintlayout:")
      }

      fun String.countSubString(sub: String): Int = split(sub).size - 1

      guiTest.getProjectFileText("app/build.gradle").run {
        assertThat(countSubString("api 'com.google.android.gms:play-services-maps")).isEqualTo(1)
        assertThat(countSubString("implementation 'androidx.appcompat:appcompat:")).isEqualTo(1)
        assertThat(this).doesNotContain("api 'androidx.constraintlayout:constraintlayout:")
      }
    }
  }

  /**
   * Verifies the warning about making the base module instant enabled is hidden if the base module
   * is already instant enabled
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Modify the base AndroidManifest file to be instant enabled
   * 3. Sync with Gradle
   * 4. Navigate to the Add Instant Dynamic Feature Module page
   * Verify:
   * 1. Verify the warning labels are not visible
   * </pre>
   */
  @Test
  fun checkWarningLabelIsHiddenWhenBaseIsInstant() {
    val ideFrame = guiTest.importSimpleApplication()
    writeDistModuleToBaseManifest(true)
    ideFrame.actAndWaitForGradleProjectSyncToFinish { it.invokeMenuPath("File", "Sync Project with Gradle Files") }
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    val fixture = NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()

    validateInstantizeBaseManifestWarningIsHidden(fixture)
    fixture.wizard().clickCancel()
  }

  /**
   * Verifies the warning about making the base module instant enabled is visible if the base module
   * is not instant enabled
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Modify the base AndroidManifest file to be instant enabled
   * 3. Sync with Gradle
   * 4. Navigate to the Add Instant Dynamic Feature Module page
   * Verify:
   * 1. Verify the warning labels are visible
   * </pre>
   */
  @Test
  fun checkWarningLabelIsVisibleWhenBaseIsNotInstant() {
    val ideFrame = guiTest.importSimpleApplication()
    writeDistModuleToBaseManifest(false)
    ideFrame.actAndWaitForGradleProjectSyncToFinish { it.invokeMenuPath("File", "Sync Project with Gradle Files") }
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    val fixture = NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()

    // TODO(qumeric): this icon was temporarily removed. It will be added after redesign. See b/139475118
    // validateInstantizeBaseManifestWarningIsVisible(fixture)
    fixture.wizard().clickCancel()
  }

  private fun validateInstantizeBaseManifestWarningIsHidden(fixture: ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture>) {
    assertThat(fixture.robot().finder().findAll(fixture.target(), JLabelMatcher.withName(
      "InstantInfoIcon").andShowing())).isEmpty()
    assertThat(fixture.robot().finder().findAll(fixture.target(), JLabelMatcher.withName(
      "InstantModuleInfo").andShowing())).isEmpty()
  }

  private fun validateInstantizeBaseManifestWarningIsVisible(fixture: ConfigureDynamicFeatureStepFixture<NewModuleWizardFixture>) {
    assertThat(fixture.robot().finder().findAll(fixture.target(), JLabelMatcher.withName(
      "InstantInfoIcon").andShowing())).isNotEmpty()
    assertThat(fixture.robot().finder().findAll(fixture.target(), JLabelMatcher.withName(
      "InstantModuleInfo").andShowing())).isNotEmpty()
  }

  private fun writeDistModuleToBaseManifest(isInstant: Boolean) {
    guiTest.ideFrame().editor
      .open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
      .moveBetween("\"http://schemas.android.com/apk/res/android\"", "")
      .enterText("\nxmlns:dist=\"http://schemas.android.com/apk/distribution\"")
      .moveBetween("google.simpleapplication\" >", "")
      .enterText("""<dist:module dist:instant="$isInstant" />""")
  }

  private fun createDynamicModule(
    ideFrame: IdeFrameFixture,
    lang: Language,
    downloadInstallKind: DownloadInstallKind = DownloadInstallKind.ON_DEMAND_ONLY,
    setFeatures: ConfigureDynamicFeatureDeliveryStepFixture<NewModuleWizardFixture>.() -> ConfigureDynamicFeatureDeliveryStepFixture<NewModuleWizardFixture> = { this }
  ): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .setSourceLanguage(lang)
      .enterFeatureModuleName("MyDynamicFeature")
      .enterPackageName("com.example.mydynamicfeature")
      .selectBaseApplication(ideFrame.getModule("app").name)
      .selectMinimumSdkApi(26)
      .clickNextToConfigureConditionalDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setDownloadInstallKind(downloadInstallKind)
      .setFeatures()
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    // Check we created the instrumented test files
    val fileName = "MyDynamicFeature/src/androidTest/java/com/example/mydynamicfeature/ExampleInstrumentedTest.${lang.extension}"
    assertThat(ideFrame.findFileByRelativePath(fileName)).isNotNull()

    return ideFrame
  }

  private fun createInstantDynamicModuleWithFusing(ideFrame: IdeFrameFixture): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module\u2026")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()
      .checkFusingCheckbox()
      .wizard()
      .clickFinishAndWaitForSyncToFinish()

    return ideFrame
  }

  private fun IdeFrameFixture.addActivity(moduleName: String, activityGroup: String, activityName: String) {
    projectView
      .selectAndroidPane()
      .clickPath(moduleName)
      .invokeMenuPath("File", "New", activityGroup, activityName)
    NewActivityWizardFixture.find(this)
      .clickFinishAndWaitForSyncToFinish()
  }
}
