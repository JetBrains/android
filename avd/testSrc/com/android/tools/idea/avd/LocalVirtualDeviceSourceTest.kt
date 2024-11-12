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
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageTags
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.LocalFileSystem
import com.android.tools.idea.adddevicedialog.LocalProject
import com.android.tools.idea.adddevicedialog.TestComposeWizard
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import java.nio.file.Files
import javax.swing.JPanel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jetbrains.jewel.bridge.LocalComponent
import org.junit.Rule
import org.junit.Test

class LocalVirtualDeviceSourceTest {
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  private fun SdkFixture.api34() =
    createLocalSystemImage(
      "google_apis",
      listOf(SystemImageTags.GOOGLE_APIS_TAG),
      AndroidVersion(34),
    )

  private fun SdkFixture.remoteApi34() =
    createRemoteSystemImage(
      "google_apis",
      listOf(SystemImageTags.GOOGLE_APIS_TAG),
      AndroidVersion(34),
    )

  private fun SdkFixture.api34ext8() =
    createLocalSystemImage(
      "google_apis",
      listOf(SystemImageTags.GOOGLE_APIS_TAG),
      AndroidVersion(34, null, 8, false),
    )

  @Test
  fun profiles() {
    with(SdkFixture()) {
      repoPackages.setRemotePkgInfos(listOf(remoteApi34()))

      val profiles: List<VirtualDeviceProfile> = runBlocking {
        LocalVirtualDeviceSource(persistentListOf(), sdkHandler, avdManager).profilesWhenReady()
      }
      val names = profiles.map { it.name }

      assertThat(names).containsAllOf("Pixel", "Pixel 8", "Medium Phone")
    }
  }

  @OptIn(ExperimentalTestApi::class)
  inner class ConfigurationPageFixture(sdkFixture: SdkFixture) {
    val wizard: TestComposeWizard

    init {
      with(sdkFixture) {
        val source =
          LocalVirtualDeviceSource(persistentListOf(NoSkin.INSTANCE), sdkHandler, avdManager)
        val profiles = runBlocking {
          LocalVirtualDeviceSource(persistentListOf(), sdkHandler, avdManager).profilesWhenReady()
        }
        val pixel8 = profiles.first { it.name == "Pixel 8" }

        wizard = TestComposeWizard { with(source) { selectionUpdated(pixel8) } }
        val swingPanel = JPanel()

        composeTestRule.setContent {
          CompositionLocalProvider(
            LocalComponent provides swingPanel,
            LocalFileSystem provides sdkRoot.fileSystem,
            LocalProject provides null,
          ) {
            wizard.Content()
          }
        }

        wizard.performAction(wizard.nextAction)
        composeTestRule.waitForIdle()
        composeTestRule.waitUntilDoesNotExist(hasText("Loading system images", substring = true))
      }
    }
  }

  @Test
  fun configurationPage_extensionImages() {
    val sdkFixture =
      SdkFixture().apply { repoPackages.setLocalPkgInfos(listOf(api34(), api34ext8())) }
    with(ConfigurationPageFixture(sdkFixture)) {
      composeTestRule.onNodeWithText("34").assertIsSelected()

      composeTestRule.onNodeWithText("Show SDK extension system images").performClick()
      composeTestRule.onNodeWithText("34-ext8").performClick()

      composeTestRule.onNodeWithText("Show SDK extension system images").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.finishAction.action).isNull()

      composeTestRule.onNodeWithText("Show SDK extension system images").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.finishAction.action).isNotNull()
    }
  }

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun configurationPage_nameValidation() {
    with(SdkFixture()) {
      repoPackages.setLocalPkgInfos(listOf(api34(), api34ext8()))

      // Create a Pixel 8
      with(ConfigurationPageFixture(this)) {
        wizard.performAction(wizard.finishAction)
        wizard.awaitClose()
      }

      with(ConfigurationPageFixture(this)) {
        // The name defaults to "Pixel 8 (2)" since Pixel 8 already exists
        composeTestRule.onNodeWithText("Pixel 8 (2)").performTextReplacement("Pixel 8")
        composeTestRule.waitForIdle()

        // We can't use Pixel 8 because it already exists
        assertThat(wizard.finishAction.action).isNull()

        composeTestRule.onNodeWithText("Pixel 8").performTextReplacement("My Pixel!")
        composeTestRule.waitForIdle()

        // We can't use "My Pixel!" because ! is not allowed in device names
        assertThat(wizard.finishAction.action).isNull()

        composeTestRule.onNodeWithText("My Pixel!").performTextReplacement("My Pixel")
        composeTestRule.waitForIdle()

        // Create "My Pixel"
        wizard.performAction(wizard.finishAction)
        composeTestRule.waitForIdle()
        wizard.awaitClose()

        val files = Files.list(avdRoot).map { it.fileName.toString() }.toList()
        assertThat(files)
          .containsExactly("Pixel_8.avd", "Pixel_8.ini", "My_Pixel.avd", "My_Pixel.ini")
      }
    }
  }
}

private suspend fun <T : DeviceProfile> DeviceSource<T>.profilesWhenReady(): List<T> =
  profiles.filterIsInstance<LoadingState.Ready<List<T>>>().first().value
