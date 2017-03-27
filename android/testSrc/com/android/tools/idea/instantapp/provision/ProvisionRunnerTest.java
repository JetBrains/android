/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.instantapp.provision;

import com.android.ddmlib.IDevice;
import com.google.common.collect.Lists;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import org.jetbrains.android.AndroidTestCase;
import org.mockito.Mock;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Tests for {@link ProvisionRunner}.
 */
public class ProvisionRunnerTest extends AndroidTestCase {
  @Mock ProgressIndicator indicator;
  @Mock ProvisionPackage pack;

  private ProvisionRunner myProvisionRunner;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myProvisionRunner = new ProvisionRunner(indicator, Lists.newArrayList(pack));
  }

  public void testExceptionWhenCancelled() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setApiLevel(23).setGoogleAccountLogged().getDevice();
    when(indicator.isCanceled()).thenReturn(true);
    assertExceptionInRunProvision(device);
    verify(indicator, times(1)).isCanceled();
  }

  public void testProvisionFinishedWhenNotCancelled() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setApiLevel(23).setGoogleAccountLogged().getDevice();
    when(indicator.isCanceled()).thenReturn(false);
    when(pack.shouldInstall(device)).thenReturn(true);
    myProvisionRunner.runProvision(device);
    verify(pack, times(1)).shouldInstall(device);
    verify(pack, times(1)).install(device);
  }

  void assertExceptionInRunProvision(IDevice device) throws Throwable {
    assertException(new AbstractExceptionCase() {
      @Override
      public Class getExpectedExceptionClass() {
        return ProvisionException.class;
      }

      @Override
      public void tryClosure() throws Throwable {
        myProvisionRunner.runProvision(device);
      }
    });
  }
}
