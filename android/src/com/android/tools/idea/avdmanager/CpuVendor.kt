/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager

import com.intellij.openapi.util.SystemInfo
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg

/**
 * Utilities for determining the CPU vendor.
 */
object CpuVendor {
  private val cpuVendor: String
    get() {
      assert(SystemInfo.isWindows)
      val cpuInfoKey = "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\"
      val cpuNum = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, cpuInfoKey)
      return "".takeIf { cpuNum.isEmpty() } ?:
             Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, cpuInfoKey + cpuNum[0], "VendorIdentifier")
    }

  @JvmStatic
  val isAMD: Boolean get() = cpuVendor == "AuthenticAMD"
  @JvmStatic
  val isIntel: Boolean get() = cpuVendor == "GenuineIntel"
}