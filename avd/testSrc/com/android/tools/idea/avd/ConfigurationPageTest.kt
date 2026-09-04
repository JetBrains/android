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

import androidx.compose.runtime.remember
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
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.AndroidVersion
import com.android.sdklib.SystemImageSupplier
import com.android.sdklib.SystemImageTags
import com.android.sdklib.internal.avd.AvdNames
import com.android.sdklib.internal.avd.UserSettingsKey
import com.android.sdklib.repository.targets.SystemImage
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.idea.adddevicedialog.LoadingState
import com.android.tools.idea.avdmanager.AccelerationErrorCode
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.utils.NullLogger
import com.google.common.truth.Truth.assertThat
import com.intellij.idea.IJIgnore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.awt.Component
import java.nio.file.Files
import javax.swing.JPanel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class ConfigurationPageTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  private fun SdkFixture.api34() = createLocalSystemImage("google_apis", listOf(SystemImageTags.GOOGLE_APIS_TAG), AndroidVersion(34))

  private fun SdkFixture.api34Play() =
    createLocalSystemImage("google_apis_playstore", listOf(SystemImageTags.PLAY_STORE_TAG), AndroidVersion(34))

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

  private fun SdkFixture.remoteApi34() = createRemoteSystemImage("google_apis", listOf(SystemImageTags.GOOGLE_APIS_TAG), AndroidVersion(34))

  private fun SdkFixture.remoteApi34Play() =
    createRemoteSystemImage("google_apis_playstore", listOf(SystemImageTags.PLAY_STORE_TAG), AndroidVersion(34))

  private fun SdkFixture.api34ext8() =
    createLocalSystemImage("google_apis", listOf(SystemImageTags.GOOGLE_APIS_TAG), AndroidVersion(34, null, 8, false))

  @Test
  fun profiles() {
    with(SdkFixture()) {
      repoPackages.setRemotePkgInfos(listOf(remoteApi34()))

      val profiles: List<VirtualDeviceProfile> = runBlocking { createAddDeviceWizard().profilesWhenReady() }
      val names = profiles.map { it.name }

      assertThat(names).containsAllOf("Pixel", "Pixel 8", "Medium Phone")
    }
  }

  @OptIn(ExperimentalTestApi::class)
  internal inner class ConfigurationPageFixture(
    val sdkFixture: SdkFixture,
    initialSystemImageState: SystemImageState = sdkFixture.systemImageState(),
    val context: ConfigurationPageContext = DefaultConfigurationPageContext,
  ) {
    val wizard: TestComposeWizard
    internal val systemImageStateFlow: MutableStateFlow<SystemImageState> = MutableStateFlow(initialSystemImageState)

    init {
      with(sdkFixture) {
        val addDeviceWizard = createAddDeviceWizard(systemImageStateFlow = systemImageStateFlow)
        val profiles = runBlocking { addDeviceWizard.profilesWhenReady() }
        val pixel8 = profiles.first { it.name == "Pixel 8" }

        wizard = TestComposeWizard {
          val deviceNameValidator = remember { DeviceNameValidator.createForAvdManager(avdManager) }
          val device =
            remember(pixel8) {
              VirtualDevice(pixel8.device).apply {
                initializeFromProfile()
                name = deviceNameValidator.uniquify(AvdNames.cleanDisplayName(pixel8.name))
              }
            }
          ConfigurationPage(
            device = device,
            systemImageStateFlow = systemImageStateFlow,
            skins = persistentListOf(NoSkin.INSTANCE),
            deviceNameValidator = deviceNameValidator,
            sdkHandler = sdkHandler,
            finish = ::finish,
            context = context,
          )
        }

        composeTestRule.setContentWithSdkLocals { wizard.Content() }
        composeTestRule.waitForIdle()
      }
    }

    private suspend fun finish(device: VirtualDevice): Boolean {
      withContext(Dispatchers.IO) { VirtualDevices(sdkFixture.avdManager).add(device) }
      return true
    }
  }

  @Test
  fun configurationPage_extensionImages() {
    val sdkFixture = SdkFixture().apply { repoPackages.setLocalPkgInfos(listOf(api34(), api34ext8())) }
    with(ConfigurationPageFixture(sdkFixture)) {
      composeTestRule.onNodeWithClickableText("34").assertIsSelected()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.onNodeWithClickableText("34-ext8").performClick()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.nextAction.action).isNull()

      composeTestRule.onNodeWithText("Show system images with SDK extensions").performClick()
      composeTestRule.waitForIdle()
      assertThat(wizard.nextAction.action).isNotNull()
    }
  }

  @Test
  fun configurationPage_nameValidation() {
    with(SdkFixture()) {
      repoPackages.setLocalPkgInfos(listOf(api34(), api34ext8()))

      // Create a Pixel 8
      with(ConfigurationPageFixture(this)) {
        wizard.performAction(wizard.nextAction)
        wizard.awaitClose()
      }

      with(ConfigurationPageFixture(this)) {
        // The name defaults to "Pixel 8 (2)" since Pixel 8 already exists
        composeTestRule.onNodeWithEditableText("Pixel 8 (2)").performTextReplacement("Pixel 8")
        composeTestRule.waitForIdle()

        // We can't use Pixel 8 because it already exists
        assertThat(wizard.nextAction.action).isNull()

        composeTestRule.onNodeWithEditableText("Pixel 8").performTextReplacement("My Pixel!")
        composeTestRule.waitForIdle()

        // We can't use "My Pixel!" because ! is not allowed in device names
        assertThat(wizard.nextAction.action).isNull()

        composeTestRule.onNodeWithEditableText("My Pixel!").performTextReplacement("My Pixel")
        composeTestRule.waitForIdle()

        // Create "My Pixel"
        wizard.performAction(wizard.nextAction)
        composeTestRule.waitForIdle()
        wizard.awaitClose()

        val files = Files.list(avdRoot).map { it.fileName.toString() }.toList()
        assertThat(files).containsExactly("Pixel_8.avd", "Pixel_8.ini", "My_Pixel.avd", "My_Pixel.ini")
      }
    }
  }

  @Test
  @IJIgnore(issue = "IDEA-370408")
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
        assertThat(wizard.nextAction.action).isNotNull()

        // Select a different system image without RISC-V
        composeTestRule.onNodeWithClickableText("Device").performClick()
        composeTestRule.onNodeWithText(api34Image.displayName).performClick()

        // We get an error banner and cannot proceed
        composeTestRule.waitForIdle()
        assertThat(wizard.nextAction.action).isNull()
        composeTestRule
          .onNodeWithText("Preferred ABI \"${SdkConstants.ABI_RISCV64}\" is not available with selected system image")
          .assertIsDisplayed()

        // Change the preferred ABI to something we have
        composeTestRule.onNodeWithText("Additional settings").performClick()
        composeTestRule.onNodeWithText(SdkConstants.ABI_RISCV64).performScrollTo().performClick()
        composeTestRule.onNodeWithClickableText(recommendedAbiForHost()).performClick()

        // We should be able to finish the edit
        composeTestRule.onNodeWithText("is not available with selected system image", substring = true).assertDoesNotExist()
        composeTestRule.waitForIdle()
        wizard.performAction(wizard.nextAction)
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
        composeTestRule.onNode(hasText("Google Play Store") and hasAnyAncestor(isPopup())).performClick()

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

        systemImageStateFlow.value = SystemImageState(hasLocal = true, hasRemote = true, images = persistentListOf())

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
        composeTestRule.onNode(hasText("Google Play Store") and hasAnyAncestor(isPopup())).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(remoteApi34Image.displayName).assertDoesNotExist()
        composeTestRule.onNodeWithText(remoteApi34PlayImage.displayName).assertIsDisplayed()
      }
    }
  }

  @Test
  @Ignore("IDEA-369982")
  fun systemImageLoading_remoteError() {
    with(SdkFixture()) {
      val api34Image = api34()
      repoPackages.setLocalPkgInfos(listOf(api34Image))

      with(ConfigurationPageFixture(this, SystemImageState.INITIAL)) {
        composeTestRule.onNodeWithText("Loading system images...").assertIsDisplayed()

        systemImageStateFlow.value = systemImageState(hasLocal = true, hasRemote = false, error = "No internet connection")

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
        assertThat(wizard.nextAction.enabled).isTrue()
      }
    }
  }

  @Test
  fun clickDownloadButton_callsPackageDownloader() {
    with(SdkFixture()) {
      val remoteImage = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remoteImage))

      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
        )

      with(ConfigurationPageFixture(this, context = context)) {
        composeTestRule.onNodeWithContentDescription("Download").performClick()
        composeTestRule.waitForIdle()

        assertThat(context.recordedDownloadPaths).containsExactly(remoteImage.path)
      }
    }
  }

  @Test
  fun finishWizard_withRemoteSystemImage_downloadsAndCompletes() {
    with(SdkFixture()) {
      val remoteImage = remoteApi34()
      val localImage = api34()
      repoPackages.setRemotePkgInfos(listOf(remoteImage))

      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
          onDownload = {
            repoPackages.setLocalPkgInfos(listOf(localImage))
          },
        )

      with(ConfigurationPageFixture(this, context = context)) {
        composeTestRule.onNodeWithText(remoteImage.displayName).assertIsSelected()
        wizard.performAction(wizard.nextAction)
        composeTestRule.waitForIdle()
        wizard.awaitClose()

        assertThat(context.recordedPrompts).isNotEmpty()
        assertThat(context.recordedDownloadPaths).containsExactly(remoteImage.path)
        val files = Files.list(avdRoot).map { it.fileName.toString() }.toList()
        assertThat(files).containsAllOf("Pixel_8.avd", "Pixel_8.ini")
      }
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_noPackagesRequired_returnsTrueImmediately() {
    with(SdkFixture()) {
      val localImage = api34()
      repoPackages.setLocalPkgInfos(listOf(localImage))
      val systemImages = sdkHandler.getSystemImageManager(FakeProgressIndicator()).images
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = systemImages.first()
        }

      val context = FakeConfigurationPageContext()
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), emptyList())

      assertThat(result).isTrue()
      assertThat(context.recordedPrompts).isEmpty()
      assertThat(context.recordedDownloadPaths).isEmpty()
      assertThat(device.image).isEqualTo(systemImages.first())
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_remoteImage_userCancelsConfirmation_returnsFalse() {
    with(SdkFixture()) {
      val remotePkg = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remotePkg))
      val remoteImage =
        SystemImageSupplier(repoManager, sdkHandler.getSystemImageManager(FakeProgressIndicator()), NullLogger()).get().first()
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = remoteImage
        }

      val context = FakeConfigurationPageContext(promptYesNoResult = false)
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), emptyList())

      assertThat(result).isFalse()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download $remoteImage?")
      assertThat(context.recordedDownloadPaths).isEmpty()
      assertThat(device.image).isEqualTo(remoteImage)
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_remoteImage_downloadFails_returnsFalse() {
    with(SdkFixture()) {
      val remotePkg = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remotePkg))
      val remoteImage =
        SystemImageSupplier(repoManager, sdkHandler.getSystemImageManager(FakeProgressIndicator()), NullLogger()).get().first()
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = remoteImage
        }

      val context = FakeConfigurationPageContext(promptYesNoResult = true, downloadPackagesResult = false)
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), emptyList())

      assertThat(result).isFalse()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download $remoteImage?")
      assertThat(context.recordedDownloadPaths).containsExactly(remotePkg.path)
      assertThat(device.image).isEqualTo(remoteImage)
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_remoteImage_downloadSucceeds_updatesDeviceImageToLocal() {
    with(SdkFixture()) {
      val remotePkg = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remotePkg))
      val remoteImage =
        SystemImageSupplier(repoManager, sdkHandler.getSystemImageManager(FakeProgressIndicator()), NullLogger()).get().first()
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = remoteImage
        }

      val localPkg = api34()
      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
          onDownload = { repoPackages.setLocalPkgInfos(listOf(localPkg)) },
        )
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), emptyList())

      assertThat(result).isTrue()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download $remoteImage?")
      assertThat(context.recordedDownloadPaths).containsExactly(remotePkg.path)
      assertThat(device.image).isNotEqualTo(remoteImage)
      assertThat(device.image).isInstanceOf(SystemImage::class.java)
      assertThat(device.image?.`package`?.path).isEqualTo(remotePkg.path)
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_remoteImage_downloadReportsSuccessButPackageNotPresent_returnsFalse() {
    with(SdkFixture()) {
      val remotePkg = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remotePkg))
      val remoteImage =
        SystemImageSupplier(repoManager, sdkHandler.getSystemImageManager(FakeProgressIndicator()), NullLogger()).get().first()
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = remoteImage
        }

      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
          onDownload = { /* do not install local package */ },
        )
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), emptyList())

      assertThat(result).isFalse()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download $remoteImage?")
      assertThat(device.image).isEqualTo(remoteImage)
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_requiredExtraPackages_downloadsSuccessfully() {
    with(SdkFixture()) {
      val localImage = api34()
      repoPackages.setLocalPkgInfos(listOf(localImage))
      val systemImages = sdkHandler.getSystemImageManager(FakeProgressIndicator()).images
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = systemImages.first()
        }

      val extraRemotePkg =
        FakeRemotePackage("emulator_preview").apply {
          setDisplayName("Emulator Preview (latest)")
        }
      val extraLocalPkg = FakeLocalPackage("emulator_preview")

      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
          onDownload = {
            repoPackages.setLocalPkgInfos(listOf(localImage, extraLocalPkg))
          },
        )
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), listOf(extraRemotePkg))

      assertThat(result).isTrue()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download Emulator Preview?")
      assertThat(context.recordedDownloadPaths).containsExactly("emulator_preview")
    }
  }

  @Test
  fun ensureNeededPackagesArePresent_bothRemoteImageAndRequiredPackages_downloadsAll() {
    with(SdkFixture()) {
      val remotePkg = remoteApi34()
      repoPackages.setRemotePkgInfos(listOf(remotePkg))
      val remoteImage =
        SystemImageSupplier(repoManager, sdkHandler.getSystemImageManager(FakeProgressIndicator()), NullLogger()).get().first()
      val pixel8 = readTestDevices().first { it.name == "Pixel 8" }
      val device =
        VirtualDevice(pixel8).apply {
          initializeFromProfile()
          image = remoteImage
        }

      val extraRemotePkg =
        FakeRemotePackage("emulator_preview").apply {
          setDisplayName("Emulator Preview (latest)")
        }
      val extraLocalPkg = FakeLocalPackage("emulator_preview")
      val localImage = api34()

      val context =
        FakeConfigurationPageContext(
          promptYesNoResult = true,
          downloadPackagesResult = true,
          onDownload = {
            repoPackages.setLocalPkgInfos(listOf(localImage, extraLocalPkg))
          },
        )
      val result = context.ensureNeededPackagesArePresent(sdkHandler, device, JPanel(), listOf(extraRemotePkg))

      assertThat(result).isTrue()
      assertThat(context.recordedPrompts).containsExactly("Confirm Download" to "Download Emulator Preview and $remoteImage?")
      assertThat(context.recordedDownloadPaths).containsExactly("emulator_preview", remotePkg.path)
      assertThat(device.image).isInstanceOf(SystemImage::class.java)
    }
  }
}

private class FakeConfigurationPageContext(
  var promptYesNoResult: Boolean = true,
  var downloadPackagesResult: Boolean = true,
  var onDownload: ((paths: List<String>) -> Unit)? = null,
) : ConfigurationPageContext {
  val recordedPrompts = mutableListOf<Pair<String, String>>()
  val recordedDownloadPaths = mutableListOf<String>()
  val recordedErrors = mutableListOf<Pair<String, String?>>()

  override fun promptYesNo(parent: Component, title: String, message: String): Boolean {
    recordedPrompts.add(title to message)
    return promptYesNoResult
  }

  override fun downloadPackages(parent: Component, paths: List<String>): Boolean {
    recordedDownloadPaths.addAll(paths)
    onDownload?.invoke(paths)
    return downloadPackagesResult
  }

  override fun showError(parent: Component, message: String?, title: String) {
    recordedErrors.add(title to message)
  }
}

private suspend fun AddDeviceWizard.profilesWhenReady(): List<VirtualDeviceProfile> =
  profiles.filterIsInstance<LoadingState.Ready<List<VirtualDeviceProfile>>>().first().value

internal fun SdkFixture.createAddDeviceWizard(
  accelerationCheck: () -> AccelerationErrorCode = { AccelerationErrorCode.ALREADY_INSTALLED },
  systemImageStateFlow: StateFlow<SystemImageState> = MutableStateFlow(systemImageState()),
  virtualDeviceFilter: (VirtualDeviceProfile) -> Boolean = { true },
) =
  AddDeviceWizard(
    project = null,
    skins = persistentListOf(NoSkin.INSTANCE),
    sdkHandler = sdkHandler,
    avdManager = avdManager,
    accelerationCheck = accelerationCheck,
    systemImageFlow = systemImageStateFlow,
    virtualDeviceFilter = virtualDeviceFilter,
  )
