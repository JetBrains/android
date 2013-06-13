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
package com.android.tools.idea.sdk;

import junit.framework.TestCase;

/**
 * Tests for {@link VersionCheck}.
 */
public class VersionCheckTest extends TestCase {
  private static final String MODERN_ANDROID_SDK_PATH = "android.modern.sdk.path";
  private static final String OLD_ANDROID_SDK_PATH = "android.old.sdk.path";

  private String myModernAndroidSdkPath;
  private String myOldAndroidSdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModernAndroidSdkPath = System.getProperty(MODERN_ANDROID_SDK_PATH);
    String msg =
      String.format("Please specify the path of a modern Android SDK (v22.0.1) with the system property '%1$s'", MODERN_ANDROID_SDK_PATH);
    assertNotNull(msg, myModernAndroidSdkPath);

    myOldAndroidSdkPath = System.getProperty(OLD_ANDROID_SDK_PATH);
    msg = String.format("Please specify the path of an old Android SDK (pre-22.0.1) with the system property '%1$s'", OLD_ANDROID_SDK_PATH);
    assertNotNull(msg, myOldAndroidSdkPath);

  }

  public void testCheckVersion() {
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(myModernAndroidSdkPath);
    assertTrue(result.isCompatibleVersion());
  }

  public void testCheckVersionWithOldSdk() {
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(myOldAndroidSdkPath);
    assertFalse(result.isCompatibleVersion());
    assertNotNull(result.getRevision());
  }
}
