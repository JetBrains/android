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
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class WindowsPerformanceHintsCheckerTest {
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

    try {
      assertEquals(myPowerShellStatusProvider.getRealtimeScanningStatus(), myRegistryStatusProvider.getRealtimeScanningStatus());
    }
    catch (IOException e) {
      e.printStackTrace();
      fail();
    }
  }
}
