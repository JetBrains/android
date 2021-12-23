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

import java.nio.file.Path

/**
 * Identifying information for a running Emulator.
 */
data class EmulatorId(val grpcPort: Int, val grpcCertificate: String?, val grpcToken: String?,
                      val avdId: String, val avdName: String, val avdFolder: Path,
                      val serialPort: Int, val adbPort: Int, val commandLine: List<String>,
                      val registrationFileName: String) {

  override fun toString(): String = "$avdId @ $grpcPort"

  val serialNumber = "emulator-$serialPort"

  val isEmbedded: Boolean
    get() = commandLine.contains("-qt-hide-window")
}