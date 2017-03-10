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

import org.jetbrains.annotations.NotNull;

import java.io.File;

import static com.android.tools.idea.instantapp.provision.ProvisionPackage.getApkVersion;
import static com.android.tools.idea.instantapp.provision.ProvisionPackageTestUtil.getInstantAppSdk;

/**
 * Tests for {@link PolicySetsPackage}.
 */
public class PolicySetsPackageTest extends ProvisionPackageTest<PolicySetsPackage> {
  @NotNull
  @Override
  PolicySetsPackage getProvisionPackageInstance() {
    if (myProvisionPackage == null) {
      myProvisionPackage = new PolicySetsPackage(getInstantAppSdk());
    }
    return myProvisionPackage;
  }

  public void testGetApk() throws Throwable {
    assertEquals((getInstantAppSdk().getPath() + "/tools/apks/debug/policy_sets/instantapps_arm64-v8a.apk").replace('/', File.separatorChar),
                 myProvisionPackage.getApk("arm64-v8a", "debug").getPath());
  }

  public void testGetApkVersion() throws Throwable {
    assertEquals("34010.147725744.147725744", getApkVersion(myProvisionPackage.getApk("arm64-v8a", "debug")));
  }
}