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
package com.android.tools.idea.tests.gui.projectstructure

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.tests.gui.framework.GuiTestRule
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.openPsd
import com.android.tools.idea.tests.gui.framework.fixture.newpsd.selectBuildVariantsConfigurable
import com.android.tools.idea.tests.gui.framework.fixture.npw.NewActivityWizardFixture
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.fest.swing.timing.Wait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(GuiTestRemoteRunner::class)
class CreateNewFlavorsTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule().withTimeout(10, TimeUnit.MINUTES)

  /**
   * Verifies addition of new flavors from project structure dialog.
   * <p>This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>TT ID: 8eab6486-073b-4b60-b979-470fb3163920
   * <pre>
   *   Test Steps:
   *   1. Open the project structure dialog
   *   2. Select the Build Variants view
   *   3. Click the flavors tab
   *   4. Add "demo" flavor dimension
   *   5. Create two new Flavors named "flavor1" and "flavor2"
   *   6. Set some properties of the flavors
   *   7. Sync the gradle, it should be successful
   *   Verification:
   *   1. Open the build.gradle file for that module and verify
   *   entries in productFlavors for "flavor1" and "flavor2"
   *   2. Verify the properties in the file match the values
   *   set in the project structure flavor dialog
   * </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  @Throws(Exception::class)
  fun createNewFlavors() {
    val ide = guiTest.importProjectAndWaitForProjectSyncToFinish("SimpleApplication", Wait.seconds(120))

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectProductFlavorsTab().run {
          clickAddFlavorDimension().run {
            type(DIMEN_NAME)
            clickOk()
          }
          clickAddProductFlavor().run {
            type(FLAVOR1)
            clickOk()
          }
          minSdkVersion().selectItemWithIndex(0) //Index 0 is corresponding SDK - 24 (API 24 ("Nougat"; Android 7.0))
          targetSdkVersion().selectItemWithIndex(0) //Index 0 is corresponding SDK - 24 (API 24 ("Nougat"; Android 7.0))
          clickAddProductFlavor().run {
            type(FLAVOR2)
            clickOk()
          }
          versionCode().enterText("2")
          versionName().enterText("2.3")
        }
      }
      clickOk()
    }

    val gradleFileContents = ide
      .editor
      .open("/app/build.gradle")
      .currentFileContents

    val dimenDemo = "dimension '$DIMEN_NAME'"
    val flavor1 = "$FLAVOR1 {\n            $dimenDemo\n            minSdk 24\n            targetSdk 24\n        }"
    val flavor2 = "$FLAVOR2 {\n            $dimenDemo\n            versionCode 2\n            versionName '2.3'\n        }"

    assertThat(gradleFileContents).contains(flavor1)
    assertThat(gradleFileContents).contains(flavor2)

    // For b/143102526:
    // Additional check: Check if able to add new Product Flavor specific Activity.
    ide.invokeMenuPath("File", "New", "Activity", "Empty Views Activity")
    NewActivityWizardFixture.find(ide)
      .getConfigureActivityStep("Empty Views Activity")
      .selectLauncherActivity()
      .setTargetSourceSet(FLAVOR1)
      .wizard()
      .clickFinishAndWaitForSyncToFinish(Wait.seconds(120))
  }
}

private val FLAVOR1 = "flavor1"
private val FLAVOR2 = "flavor2"
private val DIMEN_NAME = "demo"
