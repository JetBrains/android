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

import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRemoteRunner::class)
class AddDynamicFeatureTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

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
    val ideFrame = guiTest.importSimpleLocalApplication()

    createDefaultDynamicModule(ideFrame)

    ideFrame.editor
      .open("dynamic_feature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="true"""")
      assertThat(this).contains("""<dist:fusing dist:include="true" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_dynamic_feature">Module Title</string>""")
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
    val ideFrame = guiTest.importSimpleLocalApplication()

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
   * 3. Open the "dynamic_feature" module strings.xml and check there are new strings
   * like: "prompt_email", "prompt_password", "error_invalid_email", etc
   * </pre>
   */
  @Test
  @Throws(Exception::class)
  fun addLoginActivityToDynamicModule() {
    val ideFrame = guiTest.importSimpleLocalApplication()

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
      .open("dynamic_feature/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("prompt_email")
      assertThat(this).contains("prompt_password")
      assertThat(this).contains("error_invalid_email")
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
   * 2. Open the "dynamic_feature" module build.gradle check that play-services-maps was not added.
   * 3. Open the "app" module build.gradle and check that play-services-maps was added with "api" dependency.
   * </pre>
   */
  @Test
  @Throws(Exception::class)
  fun addMapsActivityToDynamicModule() {
    val ideFrame = guiTest.importSimpleLocalApplication()

    createDefaultDynamicModule(ideFrame)
      .invokeMenuPath("File", "New", "Google", "Google Maps Activity")
    NewActivityWizardFixture.find(ideFrame)
      .clickFinish()
      .waitForGradleProjectSyncToFinish()

    ideFrame.editor
      .open("dynamic_feature/build.gradle")
      .currentFileContents.run {
      assertThat(this).doesNotContain("play-services-maps")
    }

    ideFrame.editor
      .open("app/build.gradle")
      .currentFileContents.run {
      assertThat(this).contains("api 'com.google.android.gms:play-services-maps")
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
      .clickPath("dynamic_feature")

    return ideFrame
  }
}
