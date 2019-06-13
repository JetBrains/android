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

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.npw.dynamicapp.DeviceFeatureKind
import com.android.tools.idea.npw.dynamicapp.DownloadInstallKind
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.EditorFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.ConfigureDynamicFeatureStepFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.core.matcher.JLabelMatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class AddDynamicFeatureTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @After
  fun tearDown() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.clearOverride()
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new Dynamic Feature Module, accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new Dynamic Feature Module is shown in the project explorer pane.
   * 2. Open the Dynamic Feature Module manifest and check that "dist:onDemand" and
   * "dist:fusing include" are set to true.
   * 3. Open the app Module strings.xml (not the *dynamic* Module strings.xml) and check that a
   * new string was added for the dynamic feature title
   * </pre>
   */
  @Test
  @Throws(Exception::class)
  fun addDefaultDynamicModule() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(false)
    val ideFrame = guiTest.importSimpleApplication()

    createDefaultDynamicModule(ideFrame)

    ideFrame.editor
      .open("dynamicfeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="true"""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_dynamicfeature">Module Title</string>""")
    }
  }

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
  @Test
  @Throws(Exception::class)
  fun addInstantDynamicModule_baseHasNoModule() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(false)
    val ideFrame = guiTest.importSimpleApplication()
    createInstantDynamicModule(ideFrame)

    ideFrame.editor
      .open("dynamicfeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="false"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
      assertThat(this).contains("""dist:instant="true"""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_dynamicfeature">Module Title</string>""")
    }

    ideFrame.editor
      .open("app/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
    }
  }

  /**
   * Same as the test above, except the fusing check box is checked. Verify the "dist:fusing" attribute is set to true
   */
  @Test
  @Throws(Exception::class)
  fun addInstantDynamicModuleWithFusing_baseHasNoModule() {
    val ideFrame = guiTest.importSimpleApplication()

    createInstantDynamicModuleWithFusing(ideFrame)

    ideFrame.editor
      .open("dynamicfeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    ideFrame.editor
      .open("app/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
    }
  }

  /**
   * Same as above, except the "dist:module" tag and "dist:instant="true"" attribute is added to the app module manifest
   */
  @RunIn(TestGroup.UNRELIABLE)  // b/116415248
  @Test
  @Throws(Exception::class)
  fun addInstantDynamicModuleWithFusing_baseHasModule() {
    val ideFrame = guiTest.importSimpleApplication()

    writeDistModuleToBaseManifest(false)
    createInstantDynamicModuleWithFusing(ideFrame)

    ideFrame.editor
      .open("dynamicfeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    ideFrame.editor
      .open("app/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:instant="true"""")
      assertThat(this).contains("""xmlns:dist="http://schemas.android.com/apk/distribution""")
      assertThat(this).doesNotContain("""dist:instant="false""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Select Dynamic Feature Module and press next.
   * 4. In the Module Configuration step, select "app" as the Base application module and name the Module "MyDynamicFeature"
   * 5. In the Dynamic Delivery step, name the Delivery ""My Dynamic Feature Title", and un-tick the check box for On-Demand/Fusing.
   * Verify:
   * 1. The new Dynamic Feature Module is shown in the project explorer pane (MyDynamicFeature).
   * 2. Open the Dynamic Feature Module manifest and check that "dist:onDemand" and
   * "dist:fusing include" are set to false.
   * 3. Open the app Module strings.xml (not the *dynamic* Module strings.xml) and check that a
   * new string was added for the dynamic feature title with value "My Dynamic Feature Title"
   * </pre>
   */
  @Test
  @Throws(Exception::class)
  fun addDynamicModuleWithModifiedDelivery() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(false)
    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("MyDynamicFeature")
      .selectBaseApplication("app")
      .selectMinimumSdkApi("26")
      .clickNextToConfigureDynamicDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setOnDemand(false)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="false"""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard, with conditional delivery specifying "install time".
   */
  @Test
  @Throws(Exception::class)
  fun addDynamicModuleWithConditionalDelivery_includeAtInstallTime() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(true)
    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("MyDynamicFeature")
      .selectBaseApplication("app")
      .selectMinimumSdkApi("26")
      .clickNextToConfigureConditionalDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setDownloadInstallKind(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<dist:delivery>""")
      assertThat(this).contains("""<dist:install-time />""")
      assertThat(this).doesNotContain("""<dist:on-demand />""")
      assertThat(this).contains("""</dist:delivery>""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard, with conditional delivery specifying "on demand only".
   */
  @Test
  @Throws(Exception::class)
  fun addDynamicModuleWithConditionalDelivery_installOnDemandOnly() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(true)
    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("MyDynamicFeature")
      .selectBaseApplication("app")
      .selectMinimumSdkApi("26")
      .clickNextToConfigureConditionalDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setDownloadInstallKind(DownloadInstallKind.ON_DEMAND_ONLY)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<dist:delivery>""")
      assertThat(this).doesNotContain("""<dist:install-time />""")
      assertThat(this).contains("""<dist:on-demand />""")
      assertThat(this).contains("""</dist:delivery>""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard, without specifying device feature conditions.
   */
  @Test
  @Throws(Exception::class)
  fun addDynamicModuleWithConditionalDelivery_installOnDemandWithMinSdk() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(true)
    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("MyDynamicFeature")
      .selectBaseApplication("app")
      .selectMinimumSdkApi("26")
      .clickNextToConfigureConditionalDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setDownloadInstallKind(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS)
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<dist:delivery>""")
      assertThat(this).contains("""<dist:install-time>""")
      assertThat(this).contains("""<dist:conditions>""")
      assertThat(this).contains("""</dist:conditions>""")
      assertThat(this).contains("""</dist:install-time>""")
      assertThat(this).doesNotContain("""<dist:on-demand />""")
      assertThat(this).contains("""</dist:delivery>""")
      assertThat(this).contains("""<dist:fusing dist:include="false" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic Feature Module through the
   * new module wizard, with conditional delivery specifying a couple of
   * device feature conditions
   */
  @Test
  @Throws(Exception::class)
  fun addDynamicModuleWithConditionalDelivery_installOnDemandDeviceFeatures() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(true)

    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .enterFeatureModuleName("MyDynamicFeature")
      .selectBaseApplication("app")
      .selectMinimumSdkApi("26")
      .clickNextToConfigureConditionalDelivery()
      .enterName("My Dynamic Feature Title")
      .setFusing(false)
      .setDownloadInstallKind(DownloadInstallKind.INCLUDE_AT_INSTALL_TIME_WITH_CONDITIONS)
      .addConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test")
      .addConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test2")
      .addConditionalDeliveryFeature(DeviceFeatureKind.GL_ES_VERSION, "0x2000000")
      .removeConditionalDeliveryFeature(DeviceFeatureKind.NAME, "test2")
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {

      val expected = """|<manifest xmlns:android="http://schemas.android.com/apk/res/android"
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
                        |</manifest>
                        |
                        |""".trimMargin()
      assertEquals(expected, this)
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
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
  @Throws(Exception::class)
  fun addLoginActivityToDynamicModule() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(false)
    val ideFrame = guiTest.importSimpleApplication()

    createDefaultDynamicModule(ideFrame)
      .invokeMenuPath("File", "New", "Activity", "Login Activity")
    NewActivityWizardFixture.find(ideFrame)
      .clickFinish()
      .waitForGradleProjectSyncToFinish()

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("title_activity_login")
    }

    ideFrame.editor
      .open("dynamicfeature/src/main/res/values/strings.xml")
      .currentFileContents.run {
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
  @Throws(Exception::class)
  fun addMapsActivityToDynamicModule() {
    StudioFlags.NPW_DYNAMIC_APPS_CONDITIONAL_DELIVERY.override(false)
    val ideFrame = guiTest.importSimpleApplication()

    ideFrame.editor
      .open("app/build.gradle")
      .currentFileContents.run {
      assertThat(this).contains("implementation 'com.android.support:appcompat-v7:")
      assertThat(this).contains("implementation 'com.android.support.constraint:constraint-layout:")
    }

    createDefaultDynamicModule(ideFrame)
      .invokeMenuPath("File", "New", "Google", "Google Maps Activity")
    NewActivityWizardFixture.find(ideFrame)
      .clickFinish()
      .waitForGradleProjectSyncToFinish()

    ideFrame.editor
      .open("dynamicfeature/build.gradle")
      .currentFileContents.run {
      assertThat(this).doesNotContain("play-services-maps")
    }

    ideFrame.editor
      .open("app/build.gradle")
      .currentFileContents.run {
      assertThat(this).contains("api 'com.google.android.gms:play-services-maps")
      assertThat(this).contains("api 'com.android.support:appcompat-v7:")  // "implementation" re-written as "api"
      assertThat(this).contains("api 'com.android.support.constraint:constraint-layout:")
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
  @Throws(Exception::class)
  fun checkWarningLabelIsHiddenWhenBaseIsInstant() {
    val ideFrame = guiTest.importSimpleApplication()
    writeDistModuleToBaseManifest(true)
    ideFrame.invokeMenuPath("File", "Sync Project with Gradle Files")
    ideFrame.waitForGradleProjectSyncToFinish()
    ideFrame.invokeMenuPath("File", "New", "New Module...")
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
  @Throws(Exception::class)
  fun checkWarningLabelIsVisibleWhenBaseIsNotInstant() {
    val ideFrame = guiTest.importSimpleApplication()
    writeDistModuleToBaseManifest(false)
    ideFrame.invokeMenuPath("File", "Sync Project with Gradle Files")
    ideFrame.waitForGradleProjectSyncToFinish()
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    val fixture = NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()

    validateInstantizeBaseManifestWarningIsVisible(fixture)
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
    val editor = guiTest.ideFrame().getEditor()
    editor.open("app/src/main/AndroidManifest.xml", EditorFixture.Tab.EDITOR)
    editor.moveBetween("\"http://schemas.android.com/apk/res/android\"", "")
    editor.enterText("\nxmlns:dist=\"http://schemas.android.com/apk/distribution\"")
    editor.moveBetween("google.simpleapplication\" >", "")
    if (isInstant) {
      editor.enterText("""<dist:module dist:instant="true" />""")
    }
    else {
      editor.enterText("""<dist:module dist:instant="false" />""")
    }
  }

  private fun createDefaultDynamicModule(ideFrame: IdeFrameFixture): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .clickNextToConfigureDynamicDelivery()
      .wizard()
      .clickFinish()
      .waitForGradleProjectSyncToFinish()
      .projectView
      .selectAndroidPane()
      .clickPath("dynamicfeature")

    return ideFrame
  }

  private fun createInstantDynamicModule(ideFrame: IdeFrameFixture): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()
      .wizard()
      .clickFinish()

    return ideFrame
  }

  private fun createInstantDynamicModuleWithFusing(ideFrame: IdeFrameFixture): IdeFrameFixture {
    ideFrame.invokeMenuPath("File", "New", "New Module...")
    NewModuleWizardFixture.find(ideFrame)
      .clickNextToInstantDynamicFeature()
      .checkFusingCheckbox()
      .wizard()
      .clickFinish()

    return ideFrame
  }
}
