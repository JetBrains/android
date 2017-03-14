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
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.intellij.testFramework.exceptionCases.AbstractExceptionCase;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.instantapp.provision.ProvisionPackageTests.getMockDevice;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Abstract class to be extended by all classes testing a {@link ProvisionPackage}. Basic common tests are made here.
 */
abstract class ProvisionPackageTest<T extends ProvisionPackage> extends AndroidGradleTestCase {
  T myProvisionPackage = null;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myProvisionPackage = getProvisionPackageInstance();
  }

  @NotNull
  abstract T getProvisionPackageInstance();

  public void testShouldInstallStandard() throws Throwable {
    IDevice device = getMockDevice("arm64-v8a", 23, "dev-keys", myProvisionPackage.getPkgName(), "");
    assertTrue(myProvisionPackage.shouldInstall(device));
  }

  public void testShouldNotInstallWrongArchitecture() throws Throwable {
    IDevice device = getMockDevice("falseArch", 23, "dev-keys", myProvisionPackage.getPkgName(), "");
    assertExceptionInShouldInstall(device);
  }

  public void testShouldNotInstallLowApiLevel() throws Throwable {
    IDevice device = getMockDevice("arm64-v8a", 16, "dev-keys", myProvisionPackage.getPkgName(), "");
    assertExceptionInShouldInstall(device);
  }

  public void testInstall() throws Throwable {
    IDevice device = getMockDevice("arm64-v8a", 23, "dev-keys", myProvisionPackage.getPkgName(), "1.0-release-1234");
    // Testing that no exception is thrown
    myProvisionPackage.install(device);
    verify(device).installPackage(any(), eq(true), eq("-d"));
  }

  public void testGetInstalledApkVersion() throws Throwable {
    IDevice device = new ProvisionPackageTests.DeviceGenerator().setVersionOfPackage(myProvisionPackage.getPkgName(), "versionMock").getDevice();
    assertEquals("versionMock", myProvisionPackage.getInstalledApkVersion(device));
  }

  void assertExceptionInShouldInstall(IDevice device) throws Throwable {
    assertException(new AbstractExceptionCase() {
      @Override
      public Class getExpectedExceptionClass() {
        return ProvisionException.class;
      }

      @Override
      public void tryClosure() throws Throwable {
        myProvisionPackage.shouldInstall(device);
      }
    });
  }

}
