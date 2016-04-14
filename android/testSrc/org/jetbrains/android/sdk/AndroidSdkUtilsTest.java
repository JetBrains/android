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

import com.android.annotations.NonNull;
import com.android.sdklib.AndroidVersion;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.internal.androidTarget.MockPlatformTarget;
import com.android.tools.idea.AndroidTestCaseHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Tests for {@link AndroidSdkUtils}.
 */
public class AndroidSdkUtilsTest extends IdeaTestCase {
  private String mySdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkPath = AndroidTestCaseHelper.getAndroidSdkPath().getPath();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        removeExistingAndroidSdks();
      }
    });
  }

  private static void removeExistingAndroidSdks() {
    ProjectJdkTable table = ProjectJdkTable.getInstance();

    List<Sdk> androidSdks = table.getSdksOfType(AndroidSdkType.getInstance());
    for (Sdk sdk : androidSdks) {
      table.removeJdk(sdk);
    }
  }

  @Override
  protected void checkForSettingsDamage(@NotNull List<Throwable> exceptions) {
    // for this test we don't care for this check
  }

  public void testFindSuitableAndroidSdkWhenNoSdkSet() {
    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk("android-22");
    assertNull(sdk);
  }

  public void testFindSuitableAndroidSdkWithPathOfExistingModernSdk() {
    String targetHashString = "android-22";
    Sdk jdk = getTestProjectJdk();
    assertNotNull(jdk);
    createAndroidSdk(mySdkPath, targetHashString, jdk);

    Sdk sdk = AndroidSdkUtils.findSuitableAndroidSdk(targetHashString);
    assertNotNull(sdk);
    assertTrue(FileUtil.pathsEqual(mySdkPath, sdk.getHomePath()));
  }

  public void DISABLEDtestTryToCreateAndSetAndroidSdkWithPathOfModernSdk() {
    boolean sdkSet = AndroidSdkUtils.tryToCreateAndSetAndroidSdk(myModule, mySdkPath, "android-22");
    System.out.println("Trying to set sdk for module from: " + mySdkPath + " -> " + sdkSet);
    assertTrue(sdkSet);
    Sdk sdk = ModuleRootManager.getInstance(myModule).getSdk();
    assertNotNull(sdk);
    assertTrue(FileUtil.pathsEqual(mySdkPath, sdk.getHomePath()));
  }

  public void DISABLEDtestCreateNewAndroidPlatformWithPathOfModernSdkOnly() {
    Sdk sdk = AndroidSdkUtils.createNewAndroidPlatform(mySdkPath, false);
    System.out.println("Creating new android platform from: " + mySdkPath + " -> " + sdk);
    assertNotNull(sdk);
    assertTrue(FileUtil.pathsEqual(mySdkPath, sdk.getHomePath()));
  }

  public void testGetTargetLabel() throws Exception {
    IAndroidTarget platformTarget = new MockPlatformTarget(18, 2);
    assertEquals("API 18: Android 4.3 (Jelly Bean)", AndroidSdkUtils.getTargetLabel(platformTarget));

    IAndroidTarget unknownTarget = new MockPlatformTarget(-1, 1);
    assertEquals("API -1", AndroidSdkUtils.getTargetLabel(unknownTarget));

    IAndroidTarget anotherUnknownTarget = new MockPlatformTarget(100, 1);
    assertEquals("API 100", AndroidSdkUtils.getTargetLabel(anotherUnknownTarget));

    IAndroidTarget platformPreviewTarget = new MockPlatformTarget(100, 1) {
      @NonNull
      @Override
      public AndroidVersion getVersion() {
        return new AndroidVersion(100, "Z");
      }
    };
    assertEquals("API 100+: platform r100", AndroidSdkUtils.getTargetLabel(platformPreviewTarget));
  }

  private static void createAndroidSdk(@NotNull String androidHomePath, @NotNull String targetHashString, @NotNull Sdk javaSdk) {
    Sdk sdk = SdkConfigurationUtil.createAndAddSDK(androidHomePath, AndroidSdkType.getInstance());
    assertNotNull(sdk);
    AndroidSdkData sdkData = AndroidSdkData.getSdkData(androidHomePath);
    assertNotNull(sdkData);
    IAndroidTarget target = sdkData.findTargetByHashString(targetHashString);
    assertNotNull(target);
    AndroidSdkUtils.setUpSdk(sdk, target.getName(), new Sdk[]{javaSdk}, target, javaSdk, true);
  }
}
