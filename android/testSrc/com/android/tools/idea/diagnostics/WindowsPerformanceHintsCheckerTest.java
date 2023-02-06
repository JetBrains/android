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
package com.android.tools.idea.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.android.testutils.AssumeUtil;
import com.android.tools.idea.diagnostics.windows.WindowsDefenderPowerShellStatusProvider;
import com.android.tools.idea.diagnostics.windows.WindowsDefenderRegistryStatusProvider;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.util.ExecUtil;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WindowsPerformanceHintsCheckerTest {
  private static final int POWERSHELL_COMMAND_TIMEOUT_MS = 10000;

  private WindowsDefenderPowerShellStatusProvider myPowerShellStatusProvider;
  private WindowsDefenderRegistryStatusProvider myRegistryStatusProvider;

  @Before
  public void newProviders() {
    myPowerShellStatusProvider = new WindowsDefenderPowerShellStatusProvider();
    myRegistryStatusProvider = new WindowsDefenderRegistryStatusProvider();
  }

  @Test
  public void excludedPathsMatches() {
    AssumeUtil.assumeWindows();
    System.out.println("Checking assumptions for excludedPathsMatches");
    assumePowershellGetMpPreferenceExists();

    try {
      List<String> powershellPaths = myPowerShellStatusProvider.getExcludedPaths();
      List<String> registryPaths = myRegistryStatusProvider.getExcludedPaths();
      Collections.sort(powershellPaths);
      Collections.sort(registryPaths);
      assertEquals(powershellPaths, registryPaths);
    }
    catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  public void realtimeScanningEnabledMatches() {
    AssumeUtil.assumeWindows();
    System.out.println("Checking assumptions for realtimeScanningEnabledMatches");
    assumePowershellGetMpPreferenceExists();

    try {
      assertEquals(myPowerShellStatusProvider.getRealtimeScanningStatus(), myRegistryStatusProvider.getRealtimeScanningStatus());
    }
    catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }

  private void assumePowershellGetMpPreferenceExists() {
    // Not guaranteed to work on test machines, so skip the test if that's the case
    try {
      ProcessOutput output = ExecUtil.execAndGetOutput(new GeneralCommandLine(
        "powershell", "-inputformat", "none", "-outputformat", "text", "-NonInteractive", "-Command",
        "Get-MpPreference"), POWERSHELL_COMMAND_TIMEOUT_MS);
      if (output.getExitCode() != 0) {
        System.out.println("Skipping test. Exit code = " + output.getExitCode());
        throw new AssumptionViolatedException("Skipping test. Exit code = " + output.getExitCode());
      }
    }
    catch (ExecutionException e) {
      System.out.println("Skipping test.");
      e.printStackTrace(System.out);
      throw new AssumptionViolatedException("Skipping test.", e);
    }
    System.out.println("Get-MpPreference exists");
  }
}
