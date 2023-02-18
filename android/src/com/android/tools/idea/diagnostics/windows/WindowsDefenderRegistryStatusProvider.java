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
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinReg;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public class WindowsDefenderRegistryStatusProvider implements VirusCheckerStatusProvider {
  /** The registry key for paths excluded from Windows Defender locally */
  public static final String LOCAL_EXCLUDED_PATHS_KEY = "SOFTWARE\\Microsoft\\Windows Defender\\Exclusions\\Paths";
  /** The registry key for paths excluded from Windows Defender by the group policy */
  public static final String POLICY_EXCLUDED_PATHS_KEY = "SOFTWARE\\Policies\\Microsoft\\Windows Defender\\exclusions\\paths";
  /** The registry key for Windows Defender scanning status set by the group policy */
  public static final String POLICY_SCANNING_STATUS_KEY = "SOFTWARE\\Policies\\Microsoft\\Windows Defender\\Real-Time Protection";
  /** The registry key for the local setting of Windows Defender's scanning status */
  public static final String LOCAL_SCANNING_STATUS_KEY = "SOFTWARE\\Microsoft\\Windows Defender\\Real-Time Protection";
  /** The name of the registry value for disabling Windows Defender monitoring */
  public static final String DISABLE_MONITORING_VALUE_NAME = "DisableRealtimeMonitoring";

  /**
   * Check both the local and policy keys for excluded paths, the total excluded path is the union of these.
   */
  @NotNull
  @Override
  public List<String> getExcludedPaths() throws IOException {
    List<String> excludedPaths = new ArrayList<>();
    excludedPaths.addAll(readExcludedPaths(LOCAL_EXCLUDED_PATHS_KEY));
    excludedPaths.addAll(readExcludedPaths(POLICY_EXCLUDED_PATHS_KEY));
    return excludedPaths;
  }

  @NotNull
  private static Collection<String> readExcludedPaths(@NotNull String key) throws IOException {
    try {
      return new HashSet<>(Advapi32Util.registryGetValues(WinReg.HKEY_LOCAL_MACHINE, key).keySet());
    }
    catch (Win32Exception exception) {
      // If the exception is FileNotFound, the key doesn't exist, so just assume empty
      if (exception.getErrorCode() == WinError.ERROR_FILE_NOT_FOUND) {
        return Collections.emptySet();
      }
      throw new IOException("Error code " + exception.getErrorCode() + ": " + exception.getMessage(), exception);
    }
  }

  @NotNull
  @Override
  public RealtimeScanningStatus getRealtimeScanningStatus() throws IOException {
    // Check group policy first, because it overrides the local value
    Optional<Boolean> isMonitoringDisabled = readMonitoringDisabled(POLICY_SCANNING_STATUS_KEY);
    if (!isMonitoringDisabled.isPresent()) {
      // If group policy is empty, then check the local key
      isMonitoringDisabled = readMonitoringDisabled(LOCAL_SCANNING_STATUS_KEY);
    }

    // If it is still empty, then assume it is not disabled
    return isMonitoringDisabled.orElse(false) ?
           RealtimeScanningStatus.SCANNING_DISABLED :
           RealtimeScanningStatus.SCANNING_ENABLED;
  }

  @NotNull
  private static Optional<Boolean> readMonitoringDisabled(@NotNull String key) throws IOException {
    try {
      // If key exists, check whether the value for DisableRealtimeMonitoring exists
      boolean policyValueExists = Advapi32Util.registryValueExists(
        WinReg.HKEY_LOCAL_MACHINE, key, DISABLE_MONITORING_VALUE_NAME);
      // If the value exists, then 0 means enabled and 1 means disabled
      if (policyValueExists) {
        int policyDisableRealtimeValue = Advapi32Util.registryGetIntValue(
          WinReg.HKEY_LOCAL_MACHINE, key, DISABLE_MONITORING_VALUE_NAME);
        return Optional.of(policyDisableRealtimeValue != 0);
      }
      return Optional.empty();
    }
    catch (Win32Exception exception) {
      // If the exception is FileNotFound, the key doesn't exist, so it is empty
      if (exception.getErrorCode() != WinError.ERROR_FILE_NOT_FOUND) {
        throw new IOException("Error code " + exception.getErrorCode() + ": " + exception.getMessage(), exception);
      }
      return Optional.empty();
    }
  }
}
