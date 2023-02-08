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
import com.android.tools.idea.tests.gui.framework.waitForIdle
import com.google.common.truth.Truth.assertThat
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(GuiTestRemoteRunner::class)
class FlavorsEditingTest {

  @Rule
  @JvmField
  val guiTest = GuiTestRule()

  /**
   * Verify flavor editing works as expected.
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: bea78054-1424-4c19-a4e5-aff2596d04e2
   * <p>
   *   <pre>
   *   Steps:
   *   1. Change properties of a non-default flavor in the project structure flavor dialog
   *   2. Click "OK" button.
   *   3. Add dimension to the flavor, and check Gradle sync is successful
   *   Verify:
   *   1. Changes made to the flavors are saved to the build.gradle file of that module.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  @Throws(Exception::class)
  fun editFlavors() {
    val ide = guiTest.importSimpleApplication()

    val flavor = "flavor"
    val dimenName = "demo"

    ide.openPsd().run {
      selectBuildVariantsConfigurable().run {
        selectProductFlavorsTab().run {
          clickAddFlavorDimension().run {
            type(dimenName)
            waitForIdle()
            clickOk()
          }
          clickAddProductFlavor().run {
            type(flavor)
            waitForIdle()
            clickOk()
          }
          minSdkVersion().selectItem("25 (API 25: Android 7.1.1 (Nougat))")
          targetSdkVersion().selectItem("24 (API 24: Android 7.0 (Nougat))")
          versionCode().enterText("5")
          versionName().enterText("2.3")
        }
      }
      clickOk()
    }

    val gradleFileContents = ide
      .editor
      .open("/app/build.gradle")
      .currentFileContents

    val dimen = "dimension '$dimenName'"
    assertThat(gradleFileContents).contains(flavor + " {\n" +
                                            "            " + dimen + "\n" +
                                            "            minSdk 25\n" +
                                            "            targetSdk 24\n" +
                                            "            versionCode 5\n" +
                                            "            versionName '2.3'\n" +
                                            "        }")
  }
}
