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

import com.android.flags.junit.RestoreFlagRule
import com.android.tools.idea.flags.StudioFlags.NPW_DYNAMIC_APP_MODULE
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.GuiTestRunner
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewModuleWizardFixture
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunIn(TestGroup.PROJECT_WIZARD)
@RunWith(GuiTestRunner::class)
class AddDynamicFeatureTest {
  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  @Rule
  @JvmField
  val myRestoreFlagRule = RestoreFlagRule(NPW_DYNAMIC_APP_MODULE)

  @Before
  fun setUp() {
    NPW_DYNAMIC_APP_MODULE.override(true)
  }

  /**
   * Verifies that user is able to add a Dynamic module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Follow through the wizard to add a new dynamic module, accepting defaults.
   * 4. Complete the wizard and wait for the build to complete.
   * Verify:
   * 1. The new dynamic module is shown in the project explorer pane.
   * 2. Open the dynamic module manifest and check that "dist:onDemand" and
   * "dist:fusing include" are set to true.
   * 3. Open the app Module Strings (not the *dynamic* Module Strings) and check that a
   * new string was added for the dynamic feature title
  </pre> *
   */
  @Test
  @Throws(Exception::class)
  fun addDefaultDynamicModule() {
    val ideFrame = guiTest.importSimpleLocalApplication()

    ideFrame.invokeMenuPath("File", "New", "New Module...")

    NewModuleWizardFixture.find(ideFrame)
      .clickNextToDynamicFeature()
      .clickNextToConfigureDynamicDelivery()
      .wizard()
      .clickFinish()

    ideFrame.waitForGradleProjectSyncToFinish()

    ideFrame.projectView
      .selectAndroidPane()
      .clickPath("dynamic-feature")

    ideFrame.editor
      .open("dynamic-feature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="true"""")
      assertThat(this).contains("""<dist:fusing include="true" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_dynamic_feature">Module Title</string>""")
    }
  }

  /**
   * Verifies that user is able to add a Dynamic module through the
   * new module wizard.
   *
   * <pre>
   * Test steps:
   * 1. Import simple application project
   * 2. Go to File -> New module to open the new module dialog wizard.
   * 3. Select dynamic module and press next.
   * 4. In the Module Configuration step, select "app" as the Base application module and name the Module "MyDynamicFeature"
   * 5. In the Dynamic Delivery step, name the Delivery ""My Dynamic Feature Title", and un-tick the check box for On-Demand/Fusing.
   * Verify:
   * 1. The new dynamic module is shown in the project explorer pane (MyDynamicFeature).
   * 2. Open the dynamic module manifest and check that "dist:onDemand" and
   * "dist:fusing include" are set to false.
   * 3. Open the app Module Strings (not the *dynamic* Module Strings) and check that a
   * new string was added for the dynamic feature title with value "My Dynamic Feature Title"
  </pre> *
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

    ideFrame.waitForGradleProjectSyncToFinish()

    ideFrame.projectView
      .selectAndroidPane()
      .clickPath("MyDynamicFeature")

    ideFrame.editor
      .open("MyDynamicFeature/src/main/AndroidManifest.xml")
      .currentFileContents.run {
      assertThat(this).contains("""dist:onDemand="false"""")
      assertThat(this).contains("""<dist:fusing include="false" />""")
    }

    ideFrame.editor
      .open("app/src/main/res/values/strings.xml")
      .currentFileContents.run {
      assertThat(this).contains("""<string name="title_mydynamicfeature">My Dynamic Feature Title</string>""")
    }
  }
}
