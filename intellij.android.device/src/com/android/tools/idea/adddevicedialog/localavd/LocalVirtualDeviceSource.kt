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
package com.android.tools.idea.adddevicedialog.localavd

import androidx.compose.runtime.Composable
import com.android.tools.idea.adddevicedialog.DeviceProfile
import com.android.tools.idea.adddevicedialog.DeviceSource
import com.android.tools.idea.adddevicedialog.WizardAction
import com.android.tools.idea.adddevicedialog.WizardPageScope
import com.android.tools.idea.avdmanager.skincombobox.NoSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.avdmanager.skincombobox.SkinCollector
import com.android.tools.idea.avdmanager.skincombobox.SkinComboBoxModel
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList

internal class LocalVirtualDeviceSource(
  val systemImages: ImmutableCollection<SystemImage>,
  val skins: ImmutableCollection<Skin>,
) : DeviceSource {

  companion object {
    fun create() =
      LocalVirtualDeviceSource(
        SystemImage.getSystemImages().toImmutableList(),
        SkinComboBoxModel.merge(listOf(NoSkin.INSTANCE), SkinCollector.updateAndCollect())
          .toImmutableList(),
      )
  }

  private val state = LocalAvdConfigurationState(systemImages, skins)

  override fun WizardPageScope.selectionUpdated(profile: DeviceProfile) {
    nextAction = WizardAction { pushPage { configurationPage(profile) } }
    finishAction = WizardAction.Disabled
  }

  @Composable
  private fun WizardPageScope.configurationPage(device: DeviceProfile) {
    ConfigureDevicePanel(
      state.device,
      state.systemImages,
      state.skins,
      { state.device = it },
      ::importSkin,
    )

    nextAction = WizardAction.Disabled
    finishAction = WizardAction {
      VirtualDevices.add(state.device)
      close()
    }
  }

  private fun importSkin() {
    // TODO Validate the skin

    val skin =
      FileChooser.chooseFile(
        FileChooserDescriptorFactory.createSingleFolderDescriptor(),
        null, // TODO: add component from CompositionLocal?
        null,
        null,
      )

    if (skin != null) {
      state.importSkin(skin.toNioPath())
    }
  }

  override val profiles: List<DeviceProfile>
    get() =
      // Stub implementation
      emptyList()
}
