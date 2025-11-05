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

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasParent
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import com.android.repository.api.LocalPackage
import com.android.repository.api.RemotePackage
import com.android.repository.testframework.FakeProgressIndicator
import com.android.sdklib.AndroidVersion
import com.android.sdklib.PathFileWrapper
import com.android.sdklib.SystemImageTags
import com.android.sdklib.devices.Device
import com.android.sdklib.internal.avd.AvdManager
import com.android.sdklib.internal.avd.ConfigKey
import com.android.sdklib.repository.IdDisplay
import com.android.tools.adtui.compose.TestComposeWizard
import com.android.tools.adtui.compose.utils.StudioComposeTestRule.Companion.createStudioComposeTestRule
import com.android.tools.adtui.compose.utils.lingerMouseHover
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.google.common.truth.Truth.assertThat
import com.intellij.idea.IJIgnore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Files
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

@RunsInEdt
class EditVirtualDeviceDialogTest {
  @get:Rule val edtRule = EdtRule()
  @get:Rule val applicationRule = ApplicationRule()
  @get:Rule val composeTestRule = createStudioComposeTestRule()

  @OptIn(ExperimentalTestApi::class)
  private inner class EditAvdFixture(
    val sdkFixture: SdkFixture,
    val device: Device = sdkFixture.deviceManager.getDevice("pixel_7", "Google")!!,
    localPackages: List<LocalPackage> = emptyList<LocalPackage>(),
    remotePackages: List<RemotePackage> = emptyList<RemotePackage>(),
  ) {
    val sdkHandler by sdkFixture::sdkHandler
    val avdManager by sdkFixture::avdManager

    init {
      with(sdkFixture) {
        repoPackages.setLocalPkgInfos(localPackages)
        repoPackages.setRemotePkgInfos(remotePackages)
      }
    }

    val systemImageManager = sdkHandler.getSystemImageManager(FakeProgressIndicator())
    val api34Image = systemImageManager.getImageAt(localPackages.first().location)

    val avdInfo =
      avdManager.createAvd(
        avdManager.createAvdBuilder(device).apply {
          systemImage = api34Image
          skin = null
        }
      )

    val editDialog =
      EditVirtualDeviceDialog(
        avdInfo,
        device,
        EditVirtualDeviceDialog.Mode.EDIT,
        MutableStateFlow(sdkFixture.systemImageState()),
        persistentListOf(NoSkin.INSTANCE),
        sdkHandler,
        avdManager,
      )

    val wizard = TestComposeWizard { with(editDialog) { Page() } }

    init {
      with(sdkFixture) {
        composeTestRule.setContentWithSdkLocals { wizard.Content() }
        composeTestRule.waitUntilDoesNotExist(hasText("Loading"))
      }
    }

    fun parseIniFile(): Map<String, String> =
      checkNotNull(
        AvdManager.parseIniFile(
          PathFileWrapper(sdkFixture.avdRoot.resolve("Pixel_7.avd").resolve("config.ini")),
          null,
        )
      )
  }

  /** Edit an existing AVD, changing its name and its system image. */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun editAvdName() {
    with(SdkFixture()) {
      with(
        EditAvdFixture(sdkFixture = this, localPackages = listOf(api34(), api34ps16kbPackage()))
      ) {
        composeTestRule.onNodeWithEditableText("Pixel 7").performTextReplacement("Large Pages")
        composeTestRule.onNodeWithText("16 KB Page Size", substring = true).performClick()

        wizard.performAction(wizard.finishAction)
        wizard.awaitClose()

        assertThat(Files.list(avdRoot).map { it.fileName.toString() }.toList())
          .containsExactly("Pixel_7.avd", "Large_Pages.ini")

        val properties = parseIniFile()
        assertThat(properties[ConfigKey.IMAGES_1]).contains("google_apis_ps16k")
      }
    }
  }

  /** Edit an existing AVD, changing its RAM. */
  @OptIn(ExperimentalTestApi::class)
  @Test
  @IJIgnore(issue = "IDEA-376024")
  fun editAvdRam() {
    with(SdkFixture()) {
      with(EditAvdFixture(sdkFixture = this, localPackages = listOf(api34Play()))) {
        composeTestRule.onNodeWithText("Additional settings").performClick()
        composeTestRule
          .onNode(hasParent(hasTestTag("RamRow")) and hasSetTextAction())
          .performTextReplacement("5")

        wizard.performAction(wizard.finishAction)
        wizard.awaitClose()

        val properties = parseIniFile()
        assertThat(properties[ConfigKey.RAM_SIZE]).contains("5120")
      }
    }
  }

  /** Duplicates an existing AVD. */
  @OptIn(ExperimentalTestApi::class)
  @Test
  fun duplicateAvd() {
    with(SdkFixture()) {
      val api34Package = api34()
      repoPackages.setLocalPkgInfos(listOf(api34Package))

      val systemImageManager = sdkHandler.getSystemImageManager(FakeProgressIndicator())
      val api34Image = systemImageManager.getImageAt(api34Package.location)

      val pixel7 = deviceManager.getDevice("pixel_7", "Google")!!
      val pixel7AvdInfo =
        avdManager.createAvd(
          avdManager.createAvdBuilder(pixel7).apply {
            systemImage = api34Image
            skin = null
          }
        )

      val editDialog =
        EditVirtualDeviceDialog(
          pixel7AvdInfo,
          pixel7,
          EditVirtualDeviceDialog.Mode.DUPLICATE,
          MutableStateFlow(systemImageState()),
          persistentListOf(NoSkin.INSTANCE),
          sdkHandler,
          avdManager,
        )
      val wizard = TestComposeWizard { with(editDialog) { Page() } }
      composeTestRule.setContentWithSdkLocals { wizard.Content() }

      composeTestRule.waitUntilDoesNotExist(hasText("Loading"))

      // We can't change the name to the AVD that it was cloned from
      composeTestRule.onNodeWithEditableText("Pixel 7 (2)").performMouseInput {
        moveTo(Offset(5f, 5f))
      }
      composeTestRule.onNodeWithEditableText("Pixel 7 (2)").performTextReplacement("Pixel 7")
      composeTestRule.waitForIdle()
      composeTestRule
        .onNodeWithEditableText("Pixel 7")
        .assertIsDisplayed()
        .lingerMouseHover(composeTestRule)
      composeTestRule.onNodeWithText("already exists", substring = true).assertIsDisplayed()
      assertThat(wizard.finishAction.enabled).isFalse()

      // Change it back
      composeTestRule.onNodeWithEditableText("Pixel 7").performTextReplacement("Pixel 7 (2)")
      composeTestRule.waitForIdle()
      wizard.performAction(wizard.finishAction)
      wizard.awaitClose()

      assertThat(Files.list(avdRoot).map { it.fileName.toString() }.toList())
        .containsExactly("Pixel_7.avd", "Pixel_7.ini", "Pixel_7_2.avd", "Pixel_7_2.ini")
    }
  }
}

private fun SdkFixture.api34() =
  createLocalSystemImage("google_apis", listOf(SystemImageTags.GOOGLE_APIS_TAG), AndroidVersion(34))

private fun SdkFixture.api34Play() =
  createLocalSystemImage(
    "google_apis_playstore",
    listOf(SystemImageTags.PLAY_STORE_TAG),
    AndroidVersion(34),
  )

private fun SdkFixture.api34ps16kbPackage() =
  createLocalSystemImage(
    "google_apis_ps16k",
    listOf(SystemImageTags.GOOGLE_APIS_TAG, IdDisplay.create("page_size_16kb", "16 KB Page Size")),
    AndroidVersion(34),
  )
