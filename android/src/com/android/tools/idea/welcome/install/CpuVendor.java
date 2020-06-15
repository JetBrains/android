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
package com.android.tools.idea.welcome.install;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.WinReg;
import com.intellij.openapi.util.SystemInfo;

/**
 * Utilities for determining the CPU vendor.
 */
public final class CpuVendor {
  private CpuVendor() { }

  static String getCpuVendor() {
    assert(SystemInfo.isWindows);
    String CpuVendor = "";

    final String CpuInfoKey = "HARDWARE\\DESCRIPTION\\System\\CentralProcessor\\";
    String[] CpuNum = Advapi32Util.registryGetKeys(WinReg.HKEY_LOCAL_MACHINE, CpuInfoKey);
    if (CpuNum.length > 0) {
        String Cpu0InfoKey = CpuInfoKey + CpuNum[0];
        CpuVendor = Advapi32Util.registryGetStringValue(WinReg.HKEY_LOCAL_MACHINE, Cpu0InfoKey, "VendorIdentifier");
    }

    return CpuVendor;
  }

  public static boolean isAMD() {
    return getCpuVendor().compareTo("AuthenticAMD") == 0;
  }

  public static boolean isIntel() {
    return getCpuVendor().compareTo("GenuineIntel") == 0;
  }
}
