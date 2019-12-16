/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.transport

import com.android.ddmlib.AdbCommandRejectedException
import java.io.IOException

interface TransportFileCopier {
  /**
   * Given a [DeployableFile] returns a list of on-device paths of the copied files. Paths are expected to be UNIX paths.
   *
   * Most file formats result in only one file copied. For executable files, there may be multiple compatible ABI files, which will result
   * in multiple files copied over.
   */
  @Throws(AdbCommandRejectedException::class, IOException::class)
  fun copyFileToDevice(deployableFile: DeployableFile): List<String>
}