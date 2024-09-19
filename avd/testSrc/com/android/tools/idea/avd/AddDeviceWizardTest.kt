/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.avd

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.android.sdklib.AndroidVersion
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.TestComposeWizard
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import java.nio.file.Files
import javax.swing.JPanel
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.jewel.bridge.LocalComponent
import org.junit.Rule
import org.junit.Test

class AddDeviceWizardTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  /**
   * Pick a device, advance, and then finish (using default system image and settings). Verify that
   * AVD files are created.
   */
  @Test
  fun addDeviceDefaultPath() {
    with(SdkFixture()) {
      val api34 = createLocalSystemImage("google_apis", listOf(), AndroidVersion(34))
      repoPackages.setLocalPkgInfos(listOf(api34))

      val source =
        LocalVirtualDeviceSource(persistentListOf(NoSkin.INSTANCE), sdkHandler, avdManager)

      val wizard = TestComposeWizard { with(AddDeviceWizard(source, null)) { DeviceGridPage() } }
      val swingPanel = JPanel()
      composeTestRule.setContent {
        CompositionLocalProvider(
          LocalComponent provides swingPanel,
          LocalFileSystem provides fileSystem,
        ) {
          wizard.Content()
        }
      }

      composeTestRule.onNodeWithText("Pixel 8").performClick()
      composeTestRule.waitForIdle()

      wizard.performAction(wizard.nextAction)
      composeTestRule.waitForIdle()

      wizard.performAction(wizard.finishAction)
      composeTestRule.waitForIdle()
      wizard.awaitClose()

      val files = Files.list(avdRoot).map { it.fileName.toString() }.toList()
      assertThat(files).containsExactly("Pixel_8.avd", "Pixel_8.ini")
    }
  }
}
