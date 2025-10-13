/*
 * Copyright (C) 2025 The Android Open Source Project
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

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.application.PathManager
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.jewel.ui.component.Text

sealed class Environment {
  abstract fun toPath(): Path?

  object None : Environment() {
    override fun toString(): String = "None"

    override fun toPath(): Path? = null
  }

  class Default(val fileName: String) : Environment() {
    override fun toString(): String = fileName

    override fun toPath(): Path? = defaultEnvironmentsPath().resolve(fileName)
  }

  class Custom(val diskPath: Path) : Environment() {
    override fun toString(): String = diskPath.fileName.toString()

    override fun toPath(): Path = diskPath
  }
}

internal fun defaultEnvironmentsPath(): Path =
  when {
    StudioPathManager.isRunningFromSources() ->
      StudioPathManager.resolvePathFromSourcesRoot(
        "tools/adt/idea/artwork/resources/device-art-resources/ai_glasses_device"
      )
    else ->
      Paths.get(PathManager.getHomePath())
        .resolve("plugins/android/resources/device-art-resources/ai_glasses_device")
  }

internal fun defaultEnvironments(): List<Environment.Default> =
  defaultEnvironmentsPath().listDirectoryEntries().map {
    Environment.Default(it.fileName.toString())
  }

@Composable
internal fun GlassesEnvironmentSelector(device: VirtualDevice, state: ConfigureDevicePanelState) {
  Row {
    Text("Background", Modifier.padding(end = Padding.SMALL).alignByBaseline())

    val defaultEnvironments = remember { defaultEnvironments() }
    val currentEnvironment =
      remember(device.environment) {
        val environment = device.environment
        if (environment == null) Environment.None
        else
          defaultEnvironments.find { it.fileName == environment.fileName.toString() }
            ?: Environment.Custom(environment)
      }
    // Note that we remember on `device` so that the device's initial background value doesn't go
    // away if another is selected.
    val environments: List<Environment> =
      remember(device) {
        buildList {
          add(Environment.None)
          defaultEnvironments().forEach { add(it) }
          if (currentEnvironment is Environment.Custom) {
            add(currentEnvironment)
          }
        }
      }

    Dropdown(
      currentEnvironment,
      environments.toImmutableList(),
      { device.environment = it.toPath() },
      Modifier.alignByBaseline().testTag("GlassesEnvironmentDropdown"),
    )
  }
}
