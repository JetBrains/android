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

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import com.intellij.openapi.util.text.StringUtil;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class WindowsDefenderPowerShellStatusProvider implements VirusCheckerStatusProvider {
  private static final int POWERSHELL_COMMAND_TIMEOUT_MS = 10000;
  private static final int MAX_POWERSHELL_STDERR_LENGTH = 500;

  @Override
  @NotNull
  public List<String> getExcludedPaths() throws IOException {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command",
        "Get-MpPreference | select -ExpandProperty \"ExclusionPath\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        return output.getStdoutLines(true);
      } else {
        throw new IOException("Windows Defender exclusion path check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      throw e.toIOException();
    }
  }

  @Override
  @NotNull
  public RealtimeScanningStatus getRealtimeScanningStatus() throws IOException {
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command",
        "Get-MpPreference | select -ExpandProperty \"DisableRealtimeMonitoring\""), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() == 0) {
        if (output.getStdout().startsWith("False")) return RealtimeScanningStatus.SCANNING_ENABLED;
        return RealtimeScanningStatus.SCANNING_DISABLED;
      } else {
        throw new IOException("Windows Defender realtime scanning status check exited with status " + output.getExitCode() + ": " +
                 StringUtil.first(output.getStderr(), MAX_POWERSHELL_STDERR_LENGTH, false));
      }
    } catch (ExecutionException e) {
      throw e.toIOException();
    }
  }
}
