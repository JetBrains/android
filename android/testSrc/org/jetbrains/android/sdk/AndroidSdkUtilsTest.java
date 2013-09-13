/*
 * Copyright (C) 2013 The Android Open Source Project
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
package org.jetbrains.android.sdk;

import com.android.sdklib.IAndroidTarget;
import com.android.utils.NullLogger;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AndroidSdkUtils}.
 */
public class AndroidSdkUtilsTest extends IdeaTestCase {
  private String mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkPath = System.getProperty(AndroidTestCase.SDK_PATH_PROPERTY);
    String format = "Please specify the path of an Android SDK (v22.0.0) in the system property '%1$s'";
    String msg = String.format(format, AndroidTestCase.SDK_PATH_PROPERTY);
    assertNotNull(msg, mySdkPath);
  }

  public void testFindSuitableAndroidSdkWhenNoSdkSet() {
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk("android-17", mySdkPath, false);
    assertNull(sdk);
  }

  public void testFindSuitableAndroidSdkWithPathOfExistingModernSdk() {
    String targetHashString = "android-17";
    Sdk jdk = getTestProjectJdk();
    assertNotNull(jdk);
    createAndroidSdk(mySdkPath, targetHashString, jdk);

    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(targetHashString, mySdkPath, false);
    assertNotNull(sdk);
    assertEquals(mySdkPath, sdk.getHomePath());
  }

  public void testTryToCreateAndSetAndroidSdkWithPathOfModernSdk() {
    boolean sdkSet = AndroidSdkUtils.tryToCreateAndSetAndroidSdk(myModule, mySdkPath, "android-17", false);
    assertTrue(sdkSet);
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    assertNotNull(sdk);
    assertEquals(mySdkPath, sdk.getHomePath());
  }

  public void testCreateNewAndroidPlatformWithPathOfModernSdkOnly() {
    Sdk sdk = AndroidSdkUtils.createNewAndroidPlatform(mySdkPath);
    assertNotNull(sdk);
    assertEquals(mySdkPath, sdk.getHomePath());
  }

  private static void createAndroidSdk(@NotNull String androidHomePath, @NotNull String targetHashString, @NotNull Sdk javaSdk) {
    Sdk sdk = SdkConfigurationUtil.createAndAddSDK(androidHomePath, AndroidSdkType.getInstance());
    assertNotNull(sdk);
    AndroidSdkData sdkData = AndroidSdkData.parse(androidHomePath, NullLogger.getLogger());
    assertNotNull(sdkData);
    IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
    assertNotNull(target);
    AndroidSdkUtils.setUpSdk(sdk, target.getName(), new Sdk[]{javaSdk}, target, javaSdk, true);
  }
}
