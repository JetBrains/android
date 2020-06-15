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
package com.android.tools.idea.diagnostics.windows;

import com.sun.jna.platform.win32.Advapi32Util;
import com.sun.jna.platform.win32.Win32Exception;
import com.sun.jna.platform.win32.WinReg;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public class WindowsDefenderRegistryStatusProvider implements VirusCheckerStatusProvider {
  @NotNull
  @Override
  public List<String> getExcludedPaths() throws IOException {
    try {
      Set<String> localPaths = new HashSet<>(Advapi32Util.registryGetValues(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Microsoft\\Windows Defender\\Exclusions\\Paths").keySet());
      Set<String> policyPaths = new HashSet<>(Advapi32Util.registryGetValues(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Policies\\Microsoft\\Windows Defender\\exclusions\\paths").keySet());

      localPaths.addAll(policyPaths);
      return new ArrayList<>(localPaths);
    }
    catch (Win32Exception exception) {
      throw new IOException("Error code " + exception.getErrorCode() + ": " + exception.getMessage(), exception);
    }
  }

  @NotNull
  @Override
  public RealtimeScanningStatus getRealtimeScanningStatus() throws IOException {
    try {
      boolean policyExists = Advapi32Util.registryKeyExists(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection");
      // If group policy key exists, it should override the local value
      if (policyExists) {
        int policyDisableRealtimeValue = Advapi32Util.registryGetIntValue(
          WinReg.HKEY_LOCAL_MACHINE,
          "SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection",
          "DisableRealtimeMonitoring");
        return policyDisableRealtimeValue == 0 ? RealtimeScanningStatus.SCANNING_ENABLED : RealtimeScanningStatus.SCANNING_DISABLED;
      }

      // Check local value if group policy key doesn't exist
      int localDisableRealtimeValue = Advapi32Util.registryGetIntValue(
        WinReg.HKEY_LOCAL_MACHINE,
        "SOFTWARE\\Microsoft\\Windows Defender\\Real-Time Protection",
        "DisableRealtimeMonitoring");
      return localDisableRealtimeValue == 0 ? RealtimeScanningStatus.SCANNING_ENABLED : RealtimeScanningStatus.SCANNING_DISABLED;
    }
    catch (Win32Exception exception) {
      throw new IOException("Error code " + exception.getErrorCode() + ": " + exception.getMessage(), exception);
    }
  }
}
