/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.emulator

import com.intellij.openapi.util.SystemInfo
import java.nio.file.Path
import java.nio.file.Paths

/**
 * A container of properties that may have different values in production environment and in tests.
 */
open class Configuration {

  open val emulatorRegistrationDirectory = computeEmulatorRegistrationDirectory()

  /**
   * Returns the Emulator registration directory.
   */
  private fun computeEmulatorRegistrationDirectory(): Path {
    val dirInfo =
      when {
        SystemInfo.isMac -> {
          DirDescriptor("HOME", "Library/Caches/TemporaryItems")
        }
        SystemInfo.isWindows -> {
          DirDescriptor("LOCALAPPDATA", "Temp")
        }
        else -> {
          DirDescriptor("XDG_RUNTIME_DIR", null)
        }
      }

    val base = System.getenv(dirInfo.environmentVariable)
    if (dirInfo.relativePath == null) {
      return Paths.get(base, REGISTRATION_DIRECTORY_RELATIVE_PATH)
    }
    return Paths.get(base, dirInfo.relativePath, REGISTRATION_DIRECTORY_RELATIVE_PATH)
  }

  private class DirDescriptor(val environmentVariable: String, val relativePath: String?)
}

private const val REGISTRATION_DIRECTORY_RELATIVE_PATH = "avd/running"
