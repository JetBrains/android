/*
 * Copyright (C) 2022 The Android Open Source Project
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

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.tools.idea.avdmanager.skincombobox.DefaultSkin
import com.android.tools.idea.avdmanager.skincombobox.Skin
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.awt.Component
import java.nio.file.Path
import kotlinx.collections.immutable.ImmutableCollection
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.android.AndroidPluginDisposable

internal class LocalAvdConfigurationState
internal constructor(
  private val project: Project?,
  systemImages: ImmutableCollection<SystemImage>,
  skins: ImmutableCollection<Skin>,
  device: VirtualDevice,
) {
  internal var systemImages by mutableStateOf(systemImages)
    private set

  internal var skins by mutableStateOf(skins)
  internal var device by mutableStateOf(device)

  internal fun downloadSystemImage(parent: Component, path: String) {
    val dialog = SdkQuickfixUtils.createDialogForPaths(parent, listOf(path), false)

    if (dialog == null) {
      thisLogger().warn("Could not create the SDK Quickfix Installation dialog")
      return
    }

    dialog.show()

    val parentDisposable =
      if (project == null) {
        AndroidPluginDisposable.getApplicationInstance()
      } else {
        AndroidPluginDisposable.getProjectInstance(project)
      }

    AndroidCoroutineScope(parentDisposable, AndroidDispatchers.uiThread).launch {
      systemImages =
        withContext(AndroidDispatchers.workerThread) {
          SystemImage.getSystemImages().toImmutableList()
        }
    }
  }

  internal fun importSkin(path: Path) {
    var skin = skins.find { it.path() == path }

    if (skin == null) {
      skin = DefaultSkin(path)
      skins = (skins + skin).sorted().toImmutableList()
    }

    device = device.copy(skin = skin)
  }
}
