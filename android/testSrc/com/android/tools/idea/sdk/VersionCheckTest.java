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
import org.jetbrains.android.AndroidTestCase;

/**
 * Tests for {@link VersionCheck}.
 */
public class VersionCheckTest extends TestCase {
  private static final String PRE_V22_SDK_PATH = "PRE_V22_SDK_PATH";

  private String mySdkPath;
  private String myPreV22SdkPath;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    mySdkPath = System.getProperty(AndroidTestCase.SDK_PATH_PROPERTY);
    if (mySdkPath == null) {
      mySdkPath = System.getenv(AndroidTestCase.SDK_PATH_PROPERTY);
    }
    String format = "Please specify the path of an Android SDK (v22.0.0) in the system property '%1$s'";
    String msg = String.format(format, AndroidTestCase.SDK_PATH_PROPERTY);
    assertNotNull(msg, mySdkPath);

    myPreV22SdkPath = System.getProperty(PRE_V22_SDK_PATH);
    if (myPreV22SdkPath == null) {
      myPreV22SdkPath = System.getenv(PRE_V22_SDK_PATH);
    }
    msg = String.format("Please specify the path of an old Android SDK (pre-22.0.0) with the system property '%1$s'", PRE_V22_SDK_PATH);
    assertNotNull(msg, myPreV22SdkPath);
  }

  public void testCheckVersion() {
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(mySdkPath);
    assertTrue(result.isCompatibleVersion());
  }

  public void testCheckVersionWithOldSdk() {
    VersionCheck.VersionCheckResult result = VersionCheck.checkVersion(myPreV22SdkPath);
    assertFalse(result.isCompatibleVersion());
    assertNotNull(result.getRevision());
  }
}
