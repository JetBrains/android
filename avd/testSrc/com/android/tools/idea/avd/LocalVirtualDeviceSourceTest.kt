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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.android.SdkConstants
import com.android.repository.testframework.FakePackage.FakeLocalPackage
import com.android.repository.testframework.FakePackage.FakeRemotePackage
import com.android.sdklib.AndroidVersion
import com.android.sdklib.ISystemImage
import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.adddevicedialog.TestComposeWizard
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Files
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class LocalVirtualDeviceSourceTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  private fun SdkFixture.api34() =
    createLocalSystemImage(
      "google_apis",
      listOf(SystemImageTags.GOOGLE_APIS_TAG),
      AndroidVersion(34),
    )

  private fun SdkFixture.api34Play() =
    createLocalSystemImage(
      "google_apis_playstore",
      listOf(SystemImageTags.PLAY_STORE_TAG),
      AndroidVersion(34),
    )

  private fun SdkFixture.localApi34RiscV() = api34RiscV(false) as FakeLocalPackage

  private fun SdkFixture.remoteApi34RiscV() = api34RiscV(true) as FakeRemotePackage

  private fun SdkFixture.api34RiscV(isRemote: Boolean) =
    createSystemImage(
      isRemote = isRemote,
      path = "google_apis_riscv",
      tags = listOf(SystemImageTags.GOOGLE_APIS_TAG),
      androidVersion = AndroidVersion(34),
      displayName = "Google APIs with RISC-V Translation",
      abis = listOf(recommendedAbiForHost()),
      translatedAbis = listOf(SdkConstants.ABI_RISCV64),
    )

  private fun SdkFixture.remoteApi34() =
    createRemoteSystemImage(
      "google_apis",
      listOf(SystemImageTags.GOOGLE_APIS_TAG),
      AndroidVersion(34),
    )

  private fun SdkFixture.remoteApi34Play() =
    createRemoteSystemImage(
      "google_apis_playstore",
      listOf(SystemImageTags.PLAY_STORE_TAG),
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
        createLocalVirtualDeviceSource().profilesWhenReady()
      }
      val names = profiles.map { it.name }

      assertThat(names).containsAllOf("Pixel", "Pixel 8", "Medium Phone")
    }
  }

  @OptIn(ExperimentalTestApi::class)
  internal inner class ConfigurationPageFixture(
    val sdkFixture: SdkFixture,
    initialSystemImageState: SystemImageState = sdkFixture.systemImageState(),
  ) {
    val wizard: TestComposeWizard
    internal val systemImageStateFlow: MutableStateFlow<SystemImageState> =
      MutableStateFlow(initialSystemImageState)

    init {
      with(sdkFixture) {
        val source = createLocalVirtualDeviceSource(systemImageStateFlow)
        val profiles = runBlocking { source.profilesWhenReady() }
        val pixel8 = profiles.first { it.name == "Pixel 8" }

        wizard = TestComposeWizard { with(source) { selectionUpdated(pixel8, finish = ::finish) } }

        composeTestRule.setContentWithSdkLocals { wizard.Content() }

        wizard.performAction(wizard.nextAction)
        composeTestRule.waitForIdle()
      }
    }

    private suspend fun finish(device: VirtualDevice, image: ISystemImage): Boolean {
      withContext(AndroidDispatchers.diskIoThread) {
        VirtualDevices(sdkFixture.avdManager).add(device, image)
      }
      return true
    }
  }

  @Test
  fun configurationPage_extensionImages() {
    val sdkFixture =
      SdkFixture().apply { repoPackages.setLocalPkgInfos(listOf(api34(), api34ext8())) }
    with(ConfigurationPageFixture(sdkFixture)) {
      composeTestRule.onNodeWithClickableText("34").assertIsSelected()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.onNodeWithClickableText("34-ext8").performClick()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.finishAction.action).isNull()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.finishAction.action).isNotNull()
    }
  }

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
        composeTestRule.onNodeWithEditableText("Pixel 8 (2)").performTextReplacement("Pixel 8")
        composeTestRule.waitForIdle()

        // We can't use Pixel 8 because it already exists
        assertThat(wizard.finishAction.action).isNull()

        composeTestRule.onNodeWithEditableText("Pixel 8").performTextReplacement("My Pixel!")
        composeTestRule.waitForIdle()

        // We can't use "My Pixel!" because ! is not allowed in device names
        assertThat(wizard.finishAction.action).isNull()

        composeTestRule.onNodeWithEditableText("My Pixel!").performTextReplacement("My Pixel")
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

  @Test
  fun configurationPage_preferredAbi() {
    with(SdkFixture()) {
      val api34Image = api34()
      repoPackages.setLocalPkgInfos(listOf(api34Image, localApi34RiscV()))

      with(ConfigurationPageFixture(this)) {
        // Select system image with RISC-V translation
        composeTestRule.onNodeWithText("Google APIs with RISC-V Translation").performClick()
        composeTestRule.onNodeWithText("Additional settings").performClick()

        // Select RISC-V preferred ABI
        composeTestRule.onNodeWithText("Optimal").performScrollTo().performClick()
        composeTestRule.onNodeWithClickableText(SdkConstants.ABI_RISCV64).performClick()

        // We should have no validation error
        composeTestRule.waitForIdle()
        assertThat(wizard.finishAction.action).isNotNull()

        // Select a different system image without RISC-V
        composeTestRule.onNodeWithClickableText("Device").performClick()
        composeTestRule.onNodeWithText(api34Image.displayName).performClick()

        // We get an error banner and cannot proceed
        composeTestRule.waitForIdle()
        assertThat(wizard.finishAction.action).isNull()
        composeTestRule
          .onNodeWithText(
            "Preferred ABI \"${SdkConstants.ABI_RISCV64}\" is not available with selected system image"
          )
          .assertIsDisplayed()

        // Change the preferred ABI to something we have
        composeTestRule.onNodeWithText("Additional settings").performClick()
        composeTestRule.onNodeWithText(SdkConstants.ABI_RISCV64).performScrollTo().performClick()
        composeTestRule.onNodeWithClickableText(recommendedAbiForHost()).performClick()

        // We should be able to finish the edit
        composeTestRule
          .onNodeWithText("is not available with selected system image", substring = true)
          .assertDoesNotExist()
        composeTestRule.waitForIdle()
        wizard.performAction(wizard.finishAction)
        wizard.awaitClose()

        // The preferred ABI is written to disk
        assertThat(Files.readString(avdRoot.resolve("Pixel_8.avd").resolve("user-settings.ini")))
          .contains("${UserSettingsKey.PREFERRED_ABI}=${recommendedAbiForHost()}")
      }
    }
  }

  @Test
  fun configurationPage_deviceDetails() {
    with(SdkFixture()) {
      val api34Image = api34()
      val api34PlayImage = api34Play()
      repoPackages.setLocalPkgInfos(listOf(api34Image, api34PlayImage))

      with(ConfigurationPageFixture(this)) {
        // The Play image should be selected by default, and present in the device details
        composeTestRule.onNodeWithText(api34PlayImage.displayName).assertIsSelected()
        composeTestRule.onAllNodes(hasText("System Image") and isHeading()).assertCountEquals(2)
        composeTestRule.onNodeWithText("Google Play").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("34").assertCountEquals(2)

        // Switch to Google APIs
        composeTestRule.onNodeWithText("Google Play Store").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNode(hasText("Google APIs") and hasAnyAncestor(isPopup())).performClick()

        // Device details no longer includes system image
        composeTestRule.onAllNodes(hasText("System Image") and isHeading()).assertCountEquals(1)
        composeTestRule.onNodeWithText("Google Play").assertDoesNotExist()
        composeTestRule.onAllNodesWithText("34").assertCountEquals(1)

        // Switch back to Google Play
        composeTestRule.onNodeWithText("Google APIs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
          .onNode(hasText("Google Play Store") and hasAnyAncestor(isPopup()))
          .performClick()

        // Back where we started
        composeTestRule.onNodeWithText(api34PlayImage.displayName).assertIsSelected()
        composeTestRule.onNodeWithText("Google Play").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("34").assertCountEquals(2)
      }
    }
  }

  @Test
  fun systemImageLoading_noneFound() {
    with(SdkFixture()) {
      with(ConfigurationPageFixture(this, SystemImageState(false, false, persistentListOf()))) {
        composeTestRule.onNodeWithText("Loading system images...").assertIsDisplayed()

        systemImageStateFlow.value =
          SystemImageState(hasLocal = true, hasRemote = true, images = persistentListOf())

        composeTestRule.onNodeWithText("No system images available.").assertIsDisplayed()
      }
    }
  }

  @Test
  fun systemImageLoading_remoteLoading() {
    with(SdkFixture()) {
      val api34Image = api34()
      val remoteApi34Image = remoteApi34RiscV()
      val remoteApi34PlayImage = remoteApi34Play()

      repoPackages.setLocalPkgInfos(listOf(api34Image))

      composeTestRule.mainClock.autoAdvance = false

      with(ConfigurationPageFixture(this, SystemImageState.INITIAL)) {
        composeTestRule.onNodeWithText("Loading system images...").assertIsDisplayed()
        composeTestRule.onNodeWithText(api34Image.displayName).assertDoesNotExist()

        systemImageStateFlow.value = systemImageState(hasLocal = true, hasRemote = false)

        // Need to wait a second before proceeding, then we see the local package
        composeTestRule.onNodeWithText(api34Image.displayName).assertDoesNotExist()
        composeTestRule.mainClock.advanceTimeBy(1001)
        composeTestRule.onNodeWithText(api34Image.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading system images...").assertIsDisplayed()

        // Now the remote package arrives; it should be displayed
        repoPackages.setRemotePkgInfos(listOf(remoteApi34Image, remoteApi34PlayImage))
        systemImageStateFlow.value = systemImageState(hasLocal = true, hasRemote = true)

        composeTestRule.onNodeWithText(remoteApi34Image.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading system images...").assertDoesNotExist()

        // We should be able to select Google Play now under Services
        composeTestRule.onNodeWithClickableText("Google APIs").performClick()
        composeTestRule.waitForIdle()
        composeTestRule
          .onNode(hasText("Google Play Store") and hasAnyAncestor(isPopup()))
          .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(remoteApi34Image.displayName).assertDoesNotExist()
        composeTestRule.onNodeWithText(remoteApi34PlayImage.displayName).assertIsDisplayed()
      }
    }
  }

  @Test
  fun systemImageLoading_remoteError() {
    with(SdkFixture()) {
      val api34Image = api34()
      repoPackages.setLocalPkgInfos(listOf(api34Image))

      composeTestRule.mainClock.autoAdvance = false

      with(ConfigurationPageFixture(this, SystemImageState.INITIAL)) {
        composeTestRule.onNodeWithText("Loading system images...").assertIsDisplayed()

        systemImageStateFlow.value =
          systemImageState(hasLocal = true, hasRemote = false, error = "No internet connection")

        // We don't need to timeout to see this when there's an error
        composeTestRule.onNodeWithText(api34Image.displayName).assertIsDisplayed()
        composeTestRule.onNodeWithText("Loading system images...").assertDoesNotExist()
        composeTestRule.onNodeWithText("No internet connection").assertIsDisplayed()
      }
    }
  }

  @Test
  fun downloadSystemImage() {
    with(SdkFixture()) {
      val localImage = api34()
      val remoteImage = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remoteImage))

      with(ConfigurationPageFixture(this)) {
        composeTestRule.onNodeWithContentDescription("Download").assertIsDisplayed()
        composeTestRule.onNodeWithText(remoteImage.displayName).assertIsSelected()

        repoPackages.setLocalPkgInfos(listOf(localImage))
        systemImageStateFlow.value = systemImageState()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Download").assertDoesNotExist()
        composeTestRule.onNodeWithText(localImage.displayName).assertIsSelected()
        assertThat(wizard.finishAction.enabled).isTrue()
      }
    }
  }
}

private suspend fun LocalVirtualDeviceSource.profilesWhenReady(): List<VirtualDeviceProfile> =
  profiles.filterIsInstance<LoadingState.Ready<List<VirtualDeviceProfile>>>().first().value

internal fun SdkFixture.createLocalVirtualDeviceSource(
  systemImageStateFlow: StateFlow<SystemImageState> = MutableStateFlow(systemImageState())
) =
  LocalVirtualDeviceSource(
    persistentListOf(NoSkin.INSTANCE),
    sdkHandler,
    avdManager,
    systemImageStateFlow,
  )
