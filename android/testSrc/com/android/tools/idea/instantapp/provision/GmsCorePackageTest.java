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
import static com.android.tools.idea.instantapp.provision.ProvisionPackageTests.getInstantAppSdk;

/**
 * Tests for {@link GmsCorePackage}.
 */
public class GmsCorePackageTest extends ProvisionPackageTest<GmsCorePackage> {
  @NotNull
  @Override
  GmsCorePackage getProvisionPackageInstance() {
    if (myProvisionPackage == null) {
      myProvisionPackage = new GmsCorePackage(getInstantAppSdk());
    }
    return myProvisionPackage;
  }

  public void testGetApk() throws Throwable {
    assertEquals((getInstantAppSdk().getPath() + "/tools/apks/debug/GmsCore_prodmnc_xxhdpi_x86.apk").replace('/', File.separatorChar),
                 myProvisionPackage.getApk("x86", "debug").getPath());
  }

  public void testGetApkVersion() throws Throwable {
    assertEquals("10.5.34 (440-147160465)", getApkVersion(myProvisionPackage.getApk("arm64-v8a", "debug")));
  }
}